# Telecom-Bridge Gateway

A high-performance REST-to-Diameter Gateway microservice that accepts JSON requests via REST, initiates asynchronous Diameter Credit Control Requests (CCR) to a billing server, and returns the Credit Control Answer (CCA) back to the REST caller.

## Architecture

```
┌──────────────┐       ┌──────────────────────┐       ┌────────────────────┐
│  REST Client │──────▶│   Gateway (8080)     │──────▶│  Diameter Server   │
│  (curl/JMeter│◀──────│  Spring Boot 3.3.5   │◀──────│  Simulator (3868)  │
│   /Gatling)  │ JSON  │  + Netty TCP Client   │ Diam. │  Netty TCP Server  │
└──────────────┘       └──────────────────────┘       └────────────────────┘
```

### Modules

| Module | Description |
|--------|-------------|
| `diameter-codec` | Diameter protocol codec — binary encoding/decoding of headers and AVPs per RFC 6733 / RFC 4006 |
| `gateway` | Spring Boot REST microservice with async Diameter client |
| `simulator` | Standalone Diameter server simulator (responds to CER, CCR, DWR) |
| `load-test` | Gatling load test simulations (100 TPS / 500K transactions) |

## Prerequisites

- **Java 17** (tested with Eclipse Temurin 17.0.2)
- **Gradle 8.7** (wrapper included, no separate install needed)
- **Docker & Docker Compose** (optional, for containerized deployment)

## Quick Start

### 1. Build the project

```bash
./gradlew clean build
```

This compiles all modules and runs unit + integration tests.

### 2. Start the Diameter Simulator

```bash
./gradlew :simulator:run --args="--port=3868 --delay=50"
```

Options (passed via `--args`):
- `--port=<port>` — TCP port to listen on (default: 3868)
- `--delay=<ms>` — Simulated processing delay for CCR in milliseconds (default: 50, max: 100)
- `--workers=<n>` — Netty worker threads (default: 4)

### 3. Start the Gateway

```bash
./gradlew :gateway:bootRun
```

The gateway will:
1. Connect to the simulator on `localhost:3868`
2. Perform CER/CEA capability exchange
3. Start the DWR/DWA watchdog (every 30 seconds)
4. Begin accepting REST requests on port 8080

### 4. Send a test request

```bash
curl -X POST http://localhost:8080/api/v1/charge \
  -H "Content-Type: application/json" \
  -d '{
    "msisdn": "+919788164935",
    "serviceIdentifier": "12345",
    "requestType": 4
  }'
```

Response:
```json
{
  "sessionId": "CTOPUP;37005812090375;1",
  "resultCode": 2001,
  "grantedServiceUnit": null
}
```

## Docker Deployment

```bash
docker compose up --build
```

This starts both the simulator and gateway in a Docker network. The gateway is available at `http://localhost:8080`.

## API Reference

### POST /api/v1/charge

Initiates a Diameter Credit Control Request.

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `msisdn` | String | Yes | Subscriber phone number in E.164 format (`+` followed by 8-15 digits) |
| `serviceIdentifier` | String | Yes | Numeric service identifier (1-32 digits) |
| `requestType` | Integer | Yes | CC-Request-Type: 1=INITIAL, 2=UPDATE, 3=TERMINATION, 4=EVENT |

**Success Response (200 OK):**

```json
{
  "sessionId": "CTOPUP;37005812090375;1",
  "resultCode": 2001,
  "grantedServiceUnit": {
    "ccTime": 3600,
    "ccTotalOctets": 104857600,
    "ccServiceSpecificUnits": null
  }
}
```

**Error Responses:**

| Status | Condition | Example |
|--------|-----------|---------|
| 400 | Validation failure | Missing/invalid MSISDN, invalid requestType |
| 503 | Diameter server unreachable | Connection not established or lost |
| 504 | Diameter request timeout | No CCA received within 5 seconds |
| 502 | Protocol error | Malformed CCA from server |

## Configuration

Configuration is in `gateway/src/main/resources/application.yml`:

```yaml
diameter:
  host: localhost              # Diameter server hostname
  port: 3868                   # Diameter server port
  origin-host: CTOPUP          # Origin-Host AVP value
  origin-realm: ctop.com       # Origin-Realm AVP value
  destination-realm: BSNL.NET  # Destination-Realm AVP value
  request-timeout-ms: 5000     # CCR timeout in milliseconds
  watchdog-interval-ms: 30000  # DWR interval in milliseconds
  watchdog-timeout-ms: 10000   # DWA timeout in milliseconds
  thread-pool-size: 4          # Netty worker thread count
```

Override via environment variables (Docker):
```
DIAMETER_HOST=simulator
DIAMETER_PORT=3868
```

## Diameter Protocol Implementation

### Connection Lifecycle

```
Client                          Server
  │                               │
  │──── TCP Connect ────────────▶│  (port 3868)
  │                               │
  │──── CER (Cmd 257) ─────────▶│  Origin-Host, Origin-Realm,
  │                               │  Host-IP-Address, Vendor-Id,
  │◀─── CEA (Cmd 257) ──────────│  Product-Name, Auth-App-Id=4
  │     Result-Code: 2001        │
  │                               │
  │     ═══ READY STATE ═══      │
  │                               │
  │──── CCR (Cmd 272) ─────────▶│  Session-Id, Auth-App-Id,
  │                               │  Origin-Host, Origin-Realm,
  │◀─── CCA (Cmd 272) ──────────│  Dest-Realm, CC-Request-Type,
  │     Result-Code: 2001        │  CC-Request-Number, Subscription-Id
  │                               │
  │──── DWR (Cmd 280) ─────────▶│  (every 30 seconds)
  │◀─── DWA (Cmd 280) ──────────│
  │                               │
```

### Async Correlation (Core Design)

```java
ConcurrentHashMap<Long, PendingRequest> pendingRequests;

// SENDING CCR:
CompletableFuture<CcaData> future = new CompletableFuture<>();
pendingRequests.put(hopByHopId, new PendingRequest(future, deadline, sessionId));
channel.writeAndFlush(encodedCcr);

// RECEIVING CCA (reader thread):
long hopByHopId = message.getHeader().hopByHopId();
PendingRequest pending = pendingRequests.remove(hopByHopId);
pending.future().complete(parsedCcaData);
```

### AVP Encoding

All AVPs follow RFC 6733 wire format with proper 4-byte boundary padding:

```
┌────────────────────────────────────────────────┐
│ AVP Code (4 bytes)                             │
│ Flags (1 byte) + Length (3 bytes)              │
│ Vendor-ID (4 bytes, if V flag set)             │
│ Data (variable)                                │
│ Padding (0-3 bytes to 4-byte boundary)         │
└────────────────────────────────────────────────┘
```

## Load Testing

### Smoke Test (quick validation)

```bash
# Start simulator + gateway first, then:
./gradlew :load-test:gatlingRun-com.telecombridge.loadtest.ChargeSmokeSimulation
```

Runs 10 TPS for 30 seconds (~300 transactions).

### Full Load Test (100 TPS / 500K transactions)

```bash
./gradlew :load-test:gatlingRun-com.telecombridge.loadtest.ChargeSimulation
```

Profile:
- 30-second ramp-up from 1 to 100 TPS
- Sustained 100 TPS for ~83 minutes
- Total: ~500,000 transactions

Assertions:
- Success rate ≥ 99.9%
- p95 response time < 100ms

### Capturing the PCAP Trace

To generate `transaction_flow.pcap`:

```bash
# Terminal 1: Start packet capture on loopback
sudo tcpdump -i lo0 -w transaction_flow.pcap 'port 3868 or port 8080'

# Terminal 2: Start simulator
./gradlew :simulator:run --args="--port=3868 --delay=50"

# Terminal 3: Start gateway
./gradlew :gateway:bootRun

# Terminal 4: Send test requests
for i in $(seq 1 10); do
  curl -s -X POST http://localhost:8080/api/v1/charge \
    -H "Content-Type: application/json" \
    -d "{\"msisdn\": \"+9197881649${i}5\", \"serviceIdentifier\": \"${i}00\", \"requestType\": 1}"
done

# Stop tcpdump (Ctrl+C in Terminal 1)
```

The resulting PCAP will show:
1. HTTP POST requests on port 8080
2. Diameter CER/CEA exchange on port 3868
3. Diameter CCR/CCA transactions on port 3868

## Running Tests

```bash
# All tests (unit + integration)
./gradlew test

# Only codec tests
./gradlew :diameter-codec:test

# Only gateway tests (includes integration tests with embedded simulator)
./gradlew :gateway:test
```

### Test Coverage

| Area | Tests |
|------|-------|
| AVP encoding/decoding | `DiameterCodecEncodeTest`, `DiameterCodecDecodeTest` |
| Request correlation | `RequestCorrelatorTest` (timeout, late arrival, eviction) |
| ID generation | `IdGeneratorTest`, `SessionIdGeneratorTest` |
| Input validation | `ChargeRequestValidatorTest`, `ChargeControllerTest` |
| Error handling | `GlobalExceptionHandlerTest` |
| Connection errors | `GatewayConnectionTest` (503, 504 scenarios) |
| Full integration | `GatewayIntegrationTest` (REST → CCR → CCA → JSON) |
| Configuration | `DiameterPropertiesTest` (range validation) |

## Error Handling & Resilience

| Scenario | Behavior |
|----------|----------|
| Simulator not running on startup | Exponential backoff reconnection (1s → 2s → 4s → ... → 30s max) |
| Connection lost mid-operation | All pending futures completed exceptionally, REST returns 503, reconnection triggered |
| No CCA within timeout (5s) | Future completed with `DiameterTimeoutException`, REST returns 504 |
| DWA not received within 10s | Connection closed, reconnection triggered |
| Malformed CCA | REST returns 502 Bad Gateway |
| Invalid request payload | REST returns 400 with field-specific error details |

## Project Structure

```
telecom-bridge/
├── build.gradle                 # Root build (Java 17, JUnit 5, jqwik)
├── settings.gradle              # Module includes
├── docker-compose.yml           # Container orchestration
├── gradle/libs.versions.toml    # Dependency version catalog
├── diameter-codec/
│   └── src/main/java/.../codec/
│       ├── Avp.java             # AVP data model with typed accessors
│       ├── AvpCodes.java        # RFC 6733/4006 AVP code constants
│       ├── AvpDecoder.java      # Binary → Avp parsing
│       ├── AvpEncoder.java      # Avp → binary encoding with padding
│       ├── CommandCodes.java    # Diameter command code constants
│       ├── DiameterCodec.java   # Full message encode/decode
│       ├── DiameterHeader.java  # 20-byte header record
│       ├── DiameterMessage.java # Header + AVP list
│       └── MessageFrameDecoder.java  # Netty TCP framing
├── gateway/
│   ├── Dockerfile
│   └── src/main/java/.../gateway/
│       ├── GatewayApplication.java
│       ├── config/DiameterProperties.java
│       ├── controller/ChargeController.java
│       ├── service/ChargeService.java
│       ├── diameter/
│       │   ├── DiameterClient.java       # Netty client + CER/CEA
│       │   ├── RequestCorrelator.java    # ConcurrentHashMap correlation
│       │   ├── WatchdogScheduler.java    # DWR/DWA keepalive
│       │   ├── IdGenerator.java          # Hop-by-Hop / End-to-End IDs
│       │   └── SessionIdGenerator.java   # Session-Id generation
│       ├── dto/                          # Request/Response records
│       ├── exception/                    # Global error handling
│       ├── metrics/MetricsCollector.java # Latency & throughput metrics
│       └── validation/                   # E.164, requestType validation
├── simulator/
│   ├── Dockerfile
│   └── src/main/java/.../simulator/
│       ├── DiameterSimulator.java        # Netty server bootstrap
│       └── SimulatorHandler.java         # CER/CCR/DWR dispatch
└── load-test/
    └── src/gatling/scala/.../loadtest/
        ├── ChargeSimulation.scala        # 100 TPS / 500K full test
        └── ChargeSmokeSimulation.scala   # 10 TPS quick validation
```

## Technology Stack

| Component | Technology |
|-----------|-----------|
| REST Framework | Spring Boot 3.3.5 |
| TCP/Diameter | Netty 4.1.114 |
| Async Model | CompletableFuture + ConcurrentHashMap |
| Build | Gradle 8.7 with version catalog |
| Testing | JUnit 5 + jqwik (property-based) |
| Load Testing | Gatling 3.10.5 |
| Logging | SLF4J + Logback |
| Containerization | Docker multi-stage builds |
| Java Version | 17 (Eclipse Temurin) |
