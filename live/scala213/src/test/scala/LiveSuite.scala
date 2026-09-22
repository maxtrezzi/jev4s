import io.github.maxtrezzi.jev4s._

/** Calls the real API. Every test is skipped unless TYPESAFE_API_KEY is set; each makes one call. */
class LiveSuite extends munit.FunSuite {

  private val model  = "jev-1.13.0"
  private val ticket = "Help! My payouts have been failing for 3 days and I have a launch tomorrow."

  private def config: JevConfig = {
    assume(sys.env.get("TYPESAFE_API_KEY").exists(_.trim.nonEmpty), "TYPESAFE_API_KEY is not set")
    JevConfig.fromEnv(model).fold(e => fail(e.message), _.copy(retry = RetryPolicy.none))
  }

  test("a real reply decodes into typed answers, and a Replied event names the model") {
    var events  = List.empty[JevEvent]
    val client  = JevClient.create(config, e => events = events :+ e)
    val dept    = Choice.of[Example.Dept]("Which team should handle this?").as("dept")
    val mood    = Score("How frustrated is the customer?", List("Calm", "Frustrated", "Very angry")).as("mood")
    val answers = client.ask(ticket, dept, mood).fold(e => fail(e.toString), identity)
    assert(answers.get(dept).isDefined)
    assertEquals(
      answers.get(mood).map(_.probabilities.keySet),
      Some(Set[ujson.Value]("Calm", "Frustrated", "Very angry"))
    )
    assertEquals(events.collect { case JevEvent.Replied(reply) => reply.model }, List(model))
  }

  test("a wrong API key is Unauthorized") {
    val wrong = config.copy(apiKey = new ApiKey("jev4s-invalid-key"))
    assertEquals(
      JevClient.create(wrong).ask(ticket, Noul("Urgent?").as("urgent")).left.toOption,
      Some(JevError.Unauthorized)
    )
  }

  test("an unknown model is Rejected (HTTP 400), with the API's message") {
    JevClient.create(config.copy(model = "jev-0.0.0")).ask(ticket, Noul("Urgent?").as("urgent")) match {
      case Left(JevError.Rejected(message)) => assert(message.contains("jev-0.0.0"), message)
      case other                            => fail(s"expected Rejected, got $other")
    }
  }
}
