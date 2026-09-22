package io.github.maxtrezzi.jev4s

class JevClientSuite extends munit.FunSuite:

  enum Dept derives JevChoice, CanEqual:
    case Billing, Technical

  private val ticket   = "Help! My payouts have been failing for 3 days and I have a launch tomorrow."
  private val state    = ujson.Obj("channel" -> "email", "customer_tier" -> "pro", "message" -> ticket)
  private val recorded = Golden.read("mixed/response.json")("answers")

  // The questions of golden/mixed, as a caller writes them.
  private given JevChoice[Dept] = JevChoice(
    ChoiceOption(Dept.Billing, "billing", Some("Payments, invoicing, refunds")),
    ChoiceOption(Dept.Technical, "technical"),
  )
  private def mixed = (
    urgent = Noul("Does `message` convey urgency?"),
    department = Choice[Dept]("Which team should handle `message`?"),
    frustration = Score("How frustrated is the customer?", "Calm", "Frustrated", "Very angry"),
  )

  private def client(transport: Transport, onEvent: JevEvent => Unit = _ => ()) =
    JevClient.withTransport("jev-1.13.0", transport, onEvent)

  test("ask sends the recorded request, and types each answer by its question"):
    val transport = FakeTransport.golden("mixed")
    val r         = client(transport).ask(state, mixed).fold(e => fail(e.toString), identity)
    assertEquals(transport.sent.map(ujson.read(_)), List(Golden.read("mixed/request.json")))
    val dept: Dept = r.department.choice
    assertEquals(dept, Dept.Billing)
    assertEquals(r.urgent.probability.value, recorded("urgent")("noul").num)
    assertEquals(r.frustration.score, recorded("frustration")("score").num)

  test("askMap answers questions built at runtime, under their names"):
    val questions = Map(
      "urgent"      -> mixed.urgent,
      "department"  -> mixed.department,
      "frustration" -> mixed.frustration,
    )
    val answers = client(FakeTransport.golden("mixed")).askMap(state, questions).fold(e => fail(e.toString), identity)
    assertEquals(answers.keySet, Set("urgent", "department", "frustration"))
    assertEquals(answers("urgent"), NoulAnswer(Probability.from(recorded("urgent")("noul").num).get))

  test("one Replied event, with the model that answered and the input tokens"):
    var events = List.empty[JevEvent]
    client(FakeTransport.golden("mixed"), e => events = events :+ e).ask(state, mixed)
    val json = Golden.read("mixed/response.json")
    assertEquals(events, List(JevEvent.Replied(Reply(json("model").str, json("usage")("input_tokens").num.toLong))))

  test("an error from the transport is returned, and no event is sent"):
    var called = false
    val result = client(FakeTransport(Left(JevError.Unauthorized)), _ => called = true).ask(state, mixed)
    assertEquals(result, Left(JevError.Unauthorized))
    assert(!called)

  test("a reply that cannot be read is a Decoding error, and no event is sent"):
    var called = false
    val result = client(FakeTransport(Right("{}")), _ => called = true).ask(state, mixed)
    assertEquals(result, Left(JevError.Decoding("missing field 'answers'")))
    assert(!called)

  test("an invalid request is not sent"):
    val transport = FakeTransport.golden("mixed")
    val result    = client(transport).askMap(ticket, Map("mood" -> Score("How?", "Only one level")))
    assertEquals(result, Left(JevError.InvalidRequest(List(Problem.ScoreLevels("mood", 1)))))
    assertEquals(transport.sent, Nil)

  test("an exception thrown by onEvent reaches the caller"):
    val boom = intercept[IllegalStateException]:
      client(FakeTransport.golden("mixed"), _ => throw IllegalStateException("boom")).ask(state, mixed)
    assertEquals(boom.getMessage, "boom")

  test("a client built with no onEvent answers, and sends its events nowhere"):
    val result =
      JevClient.withTransport("jev-1.13.0", FakeTransport.golden("noul")).ask(ticket, (is_urgent = Noul("Urgent?")))
    assert(result.isRight, result)

  test("a String state is sent as text"):
    val transport = FakeTransport.golden("noul")
    client(transport).ask(ticket, (is_urgent = Noul("Does this convey urgency?")))
    assertEquals(transport.sent.map(ujson.read(_)), List(Golden.read("noul/request.json")))

  test("a state of your own type is sent through its ToState"):
    final case class Ticket(text: String)
    given ToState[Ticket] = t => ujson.Str(t.text)
    val transport         = FakeTransport.golden("noul")
    client(transport).ask(Ticket(ticket), (is_urgent = Noul("Does this convey urgency?")))
    assertEquals(transport.sent.map(ujson.read(_)), List(Golden.read("noul/request.json")))
