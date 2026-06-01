package com.telecombridge.loadtest

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.util.Random

/**
 * Gatling load test simulation for the Telecom-Bridge charge endpoint.
 *
 * Targets: POST /api/v1/charge
 * Profile: 30-second ramp-up to 100 TPS, then sustained 100 TPS for ~500,000 total transactions.
 *
 * Assertions:
 *   - Success rate >= 99.9%
 *   - p95 response time < 100ms
 *
 * Requirements validated: 9.1, 9.2, 9.3, 9.4, 9.5
 *
 * Heap Memory Monitoring Approach:
 * ---------------------------------
 * To validate the 20% heap growth threshold (Requirement 9.3), monitor the Gateway JVM
 * heap usage externally during the load test run:
 *
 * 1. Enable JMX on the Gateway JVM:
 *    -Dcom.sun.management.jmxremote
 *    -Dcom.sun.management.jmxremote.port=9010
 *    -Dcom.sun.management.jmxremote.authenticate=false
 *    -Dcom.sun.management.jmxremote.ssl=false
 *
 * 2. Record baseline heap usage at the 5-minute mark (post-warm-up):
 *    Use jcmd <pid> GC.heap_info or connect via VisualVM/JConsole.
 *
 * 3. Sample heap usage periodically (every 60 seconds) throughout the test.
 *
 * 4. After the test, verify that max heap usage did not exceed 120% of the
 *    5-minute baseline. If heap grows beyond 20% of baseline, investigate
 *    potential memory leaks (e.g., Correlation Map entries not being evicted).
 *
 * Alternatively, use Spring Boot Actuator metrics endpoint:
 *    GET /actuator/metrics/jvm.memory.used?tag=area:heap
 *    to collect heap usage samples via a script running alongside the load test.
 */
class ChargeSimulation extends Simulation {

  private val random = new Random()

  private val baseUrl = System.getProperty("gatling.baseUrl", "http://localhost:8080")

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  /**
   * Generates a random MSISDN in E.164 format: "+" followed by 10-12 digits.
   */
  private def randomMsisdn(): String = {
    val length = 10 + random.nextInt(3) // 10, 11, or 12 digits
    val digits = (1 to length).map(_ => random.nextInt(10)).mkString
    s"+$digits"
  }

  /**
   * Generates a random service identifier: numeric string of 1-6 digits.
   */
  private def randomServiceIdentifier(): String = {
    val length = 1 + random.nextInt(6) // 1 to 6 digits
    (1 to length).map(_ => random.nextInt(10)).mkString
  }

  /**
   * Generates a random request type: 1 (INITIAL), 2 (UPDATE), 3 (TERMINATION), or 4 (EVENT).
   */
  private def randomRequestType(): Int = {
    1 + random.nextInt(4)
  }

  /**
   * Generates a random JSON request body for the charge endpoint.
   */
  private def generateRandomBody(): String = {
    val msisdn = randomMsisdn()
    val serviceId = randomServiceIdentifier()
    val reqType = randomRequestType()
    s"""{"msisdn":"$msisdn","serviceIdentifier":"$serviceId","requestType":$reqType}"""
  }

  val chargeScenario = scenario("Charge Request Load Test")
    .exec(
      http("POST /api/v1/charge")
        .post("/api/v1/charge")
        .body(StringBody(session => generateRandomBody()))
        .check(status.is(200))
    )

  // Sustained duration to reach 500,000 total transactions at 100 TPS.
  // Ramp-up phase: 30 seconds, average ~50 TPS => ~1,500 transactions during ramp.
  // Remaining: 500,000 - 1,500 = 498,500 transactions at 100 TPS => ~4,985 seconds (~83 minutes).
  private val sustainedDurationSeconds = 4985

  setUp(
    chargeScenario.inject(
      rampUsersPerSec(1).to(100).during(30.seconds),           // 30-second ramp-up to 100 TPS
      constantUsersPerSec(100).during(sustainedDurationSeconds.seconds) // Sustain 100 TPS for ~500K total
    )
  ).protocols(httpProtocol)
    .assertions(
      global.successfulRequests.percent.gte(99.9),   // Success rate >= 99.9%
      global.responseTime.percentile(95).lt(100)     // p95 response time < 100ms
    )
}
