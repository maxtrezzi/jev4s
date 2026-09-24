package io.github.maxtrezzi.jev4s

class JevClientSuite extends munit.FunSuite {

  sealed abstract class Dept extends Product with Serializable
  object Dept {
    case object Billing   extends Dept
    case object Technical extends Dept
    implicit val choices: JevChoice[Dept] = JevChoice.fromOptions(
      List(ChoiceOption(Billing, "billing", Some("Payments, invoicing, refunds")), ChoiceOption(Technical, "technical"))
    )
  }

  private val ticket   = "Help! My payouts have been failing for 3 days and I have a launch tomorrow."
  private val state    = ujson.Obj("channel" -> "email", "customer_tier" -> "pro", "message" -> ticket)
  private val recorded = Golden.read("mixed/response.json")("answers")

  // The questions of golden/mixed, as a caller writes them.
  private val urgent      = Noul("Does `message` convey urgency?").as("urgent")
  private val department  = Choice.of[Dept]("Which team should handle `message`?").as("department")
  private val frustration =
    Score("How frustrated is the customer?", List("Calm", "Frustrated", "Very angry")).as("frustration")

  private def client(transport: Transport, onEvent: JevEvent => Unit = _ => ()) =
    JevClient.withTransport("jev-1.13.0", transport, onEvent)

  private def answers(result: Either[JevError, Answers]): Answers = result.fold(e => fail(e.toString), identity)

  test("ask sends the recorded request, and each key reads its answer, typed") {
    val transport = FakeTransport.golden("mixed")
    val a         = answers(client(transport).ask(state, urgent, department, frustration))
    assertEquals(transport.sent.map(ujson.read(_)), List(Golden.read("mixed/request.json")))
    val dept: Option[Dept] = a.get(department).map(_.choice)
    assertEquals(dept, Some(Dept.Billing))
    assertEquals(a.get(urgent).map(_.probability.value), Some(recorded("urgent")("noul").num))
    assertEquals(a.get(frustration).map(_.score), Some(recorded("frustration")("score").num))
  }

  test("a key with the same name but a different question gives None") {
    val a     = answers(client(FakeTransport.golden("mixed")).ask(state, urgent, department, frustration))
    val other = Noul("Is the customer polite?").as("urgent")
    assertEquals(a.get(other), None)
  }

  test("a key whose name was not asked gives None") {
    val a = answers(client(FakeTransport.golden("mixed")).ask(state, urgent, department, frustration))
    assertEquals(a.get(Noul("Does `message` convey urgency?").as("elsewhere")), None)
  }

  test("an equal key built again reads the same answer") {
    val a = answers(client(FakeTransport.golden("mixed")).ask(state, urgent, department, frustration))
    assertEquals(a.get(Noul("Does `message` convey urgency?").as("urgent")), a.get(urgent))
  }

  // A Choice under the name of golden/mixed's, over values of any type.
  private def choiceOver[C](billing: C, technical: C) =
    Choice("Which team?", List(ChoiceOption(billing, "billing"), ChoiceOption(technical, "technical"))).as("department")

  test("a Choice over values of another type gives None, although Scala's == says it is equal") {
    def ask[C](key: Key[ChoiceAnswer[C]]) =
      answers(client(FakeTransport.golden("mixed")).ask(state, urgent, key, frustration))

    val numbers = ask(choiceOver(1, 2))
    assert(choiceOver(1, 2) == choiceOver(1L, 2L))
    assertEquals(numbers.get(choiceOver(1, 2)).map(_.choice), Some(1))
    assertEquals(numbers.get(choiceOver(1L, 2L)), None)

    val options = ask(choiceOver(Option(1), Option(2)))
    assert(choiceOver(Option(1), Option(2)) == choiceOver(Option(1L), Option(2L)))
    assertEquals(options.get(choiceOver(Option(1), Option(2))).map(_.choice), Some(Some(1)))
    assertEquals(options.get(choiceOver(Option(1L), Option(2L))), None)

    // A part of the same class next to one of another class: Vector("a", 1) and Vector("a", 1L).
    def mixed(n: Any): Vector[Any] = Vector("a", n)
    val vectors                    = ask(choiceOver(mixed(1), mixed(2)))
    assert(choiceOver(mixed(1), mixed(2)) == choiceOver(mixed(1L), mixed(2L)))
    assertEquals(vectors.get(choiceOver(mixed(1), mixed(2))).map(_.choice), Some(mixed(1)))
    assertEquals(vectors.get(choiceOver(mixed(1L), mixed(2L))), None)
  }

  test("a Choice over null values reads its answer") {
    val key = choiceOver[String](null, "t")
    val a   = answers(client(FakeTransport.golden("mixed")).ask(state, urgent, key, frustration))
    assertEquals(a.get(choiceOver[String](null, "t")).map(_.choice), Some(null))
  }

  test("one Replied event, with the model that answered and the input tokens") {
    var events = List.empty[JevEvent]
    client(FakeTransport.golden("mixed"), e => events = events :+ e).ask(state, urgent, department, frustration)
    val json = Golden.read("mixed/response.json")
    assertEquals(
      events,
      List[JevEvent](JevEvent.Replied(Reply(json("model").str, Some(json("usage")("input_tokens").num.toLong))))
    )
  }

  test("a reply without usage still answers, and its Replied event has no token count") {
    val json = Golden.read("mixed/response.json")
    json.obj.remove("usage")
    var events = List.empty[JevEvent]
    val result = client(new FakeTransport(Right(json.render())), e => events = events :+ e).ask(state, urgent)
    assertEquals(answers(result).get(urgent).map(_.probability.value), Some(recorded("urgent")("noul").num))
    assertEquals(events, List[JevEvent](JevEvent.Replied(Reply(json("model").str, None))))
  }

  test("an error from the transport is returned, and no event is sent") {
    var called = false
    val result = client(new FakeTransport(Left(JevError.Unauthorized)), _ => called = true).ask(state, urgent)
    assertEquals(result.left.toOption, Some(JevError.Unauthorized))
    assert(!called)
  }

  test("a reply that cannot be read is a Decoding error, and no event is sent") {
    var called = false
    val result = client(new FakeTransport(Right("{}")), _ => called = true).ask(state, urgent)
    assertEquals(result.left.toOption, Some(JevError.Decoding("missing field 'answers'")))
    assert(!called)
  }

  test("an invalid request is not sent") {
    val transport = FakeTransport.golden("mixed")
    val result    = client(transport).ask(ticket, Score("How?", List("Only one level")).as("mood"))
    assertEquals(result.left.toOption, Some(JevError.InvalidRequest(List(Problem.ScoreLevels("mood", 1)))))
    assertEquals(transport.sent, Nil)
  }

  test("an exception thrown by onEvent reaches the caller") {
    val boom = intercept[IllegalStateException] {
      client(FakeTransport.golden("mixed"), _ => throw new IllegalStateException("boom"))
        .ask(state, urgent, department, frustration)
    }
    assertEquals(boom.getMessage, "boom")
  }

  test("a client built with no onEvent answers, and sends its events nowhere") {
    val result =
      JevClient.withTransport("jev-1.13.0", FakeTransport.golden("noul")).ask(ticket, Noul("Urgent?").as("is_urgent"))
    assert(result.isRight, result)
  }

  test("a String state is sent as text") {
    val transport = FakeTransport.golden("noul")
    client(transport).ask(ticket, Noul("Does this convey urgency?").as("is_urgent"))
    assertEquals(transport.sent.map(ujson.read(_)), List(Golden.read("noul/request.json")))
  }

  test("a state of your own type is sent through its ToState") {
    final case class Ticket(text: String)
    implicit val ticketState: ToState[Ticket] = t => ujson.Str(t.text)
    val transport                             = FakeTransport.golden("noul")
    client(transport).ask(Ticket(ticket), Noul("Does this convey urgency?").as("is_urgent"))
    assertEquals(transport.sent.map(ujson.read(_)), List(Golden.read("noul/request.json")))
  }

  test("a state type with no ToState does not compile") {
    val errors = compileErrors(
      """JevClient.withTransport("m", new FakeTransport(Left(JevError.Unauthorized))).ask(42, Noul("U?").as("u"))"""
    )
    assert(
      errors.contains("no ToState[Int]: define an implicit ToState[Int], or pass a String or a ujson.Value"),
      errors
    )
  }
}
