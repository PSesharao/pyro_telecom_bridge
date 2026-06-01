package com.telecombridge.loadtest

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.util.Random

/**
 * Smoke test simulation for quick validation of the charge endpoint.
 *
 * Profile: Ramp to 10 TPS over 5 seconds, sustain for 30 seconds (~300 transactions total).
 * Use this to verify the system is functional before running the full load test.
 *
 * Run with: ./gradlew :load-test:gatlingRun-com.telecombridge.loadtest.ChargeSmokeSimulation
 */
class ChargeSmokeSimulation extends Simulation {

  private val random = new Random()

  private val baseUrl = System.getProperty("gatling.baseUrl", "http://localhost:8080")

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  private def randomMsisdn(): String = {
    val length = 10 + random.nextInt(3)
    val digits = (1 to length).map(_ => random.nextInt(10)).mkString
    s"+$digits"
  }

  private def randomServiceIdentifier(): String = {
    val length = 1 + random.nextInt(6)
    (1 to length).map(_ => random.nextInt(10)).mkString
  }

  private def randomRequestType(): Int = {
    1 + random.nextInt(4)
  }

  private def generateRandomBody(): String = {
    val msisdn = randomMsisdn()
    val serviceId = randomServiceIdentifier()
    val reqType = randomRequestType()
    s"""{"msisdn":"$msisdn","serviceIdentifier":"$serviceId","requestType":$reqType}"""
  }

  val chargeScenario = scenario("Charge Request Smoke Test")
    .exec(
      http("POST /api/v1/charge")
        .post("/api/v1/charge")
        .body(StringBody(session => generateRandomBody()))
        .check(status.is(200))
    )

  setUp(
    chargeScenario.inject(
      rampUsersPerSec(1).to(10).during(5.seconds),    // 5-second ramp to 10 TPS
      constantUsersPerSec(10).during(30.seconds)       // Sustain 10 TPS for 30 seconds
    )
  ).protocols(httpProtocol)
    .assertions(
      global.successfulRequests.percent.gte(99.0),     // Relaxed for smoke test
      global.responseTime.percentile(95).lt(200)       // Relaxed for smoke test
    )
}
