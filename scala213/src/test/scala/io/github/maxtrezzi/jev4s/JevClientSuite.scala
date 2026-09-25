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

  private def answer[A](result: Either[JevError, A]): A = result.fold(e => fail(e.toString), identity)

  test("ask sends the recorded request, and returns the answer of each key, typed, in the order of the keys") {
    val transport = FakeTransport.golden("mixed")
    val (u, d, f) = answer(client(transport).ask(state, urgent, department, frustration))
    assertEquals(transport.sent.map(ujson.read(_)), List(Golden.read("mixed/request.json")))
    val dept: Dept               = d.choice
    val score: Double            = f.score
    val probability: Probability = u.probability
    assertEquals(dept, Dept.Billing)
    assertEquals(probability.value, recorded("urgent")("noul").num)
    assertEquals(score, recorded("frustration")("score").num)
  }

  // Ten Nouls, q1 to q10, and a reply that gives q<i> the probability i / 100.
  private val nouls      = (1 to 10).toList.map(i => Noul(s"Question $i?").as(s"q$i"))
  private val tenAnswers = ujson.Obj(
    "model"   -> "jev-1.13.0",
    "answers" -> ujson.Obj.from(nouls.zipWithIndex.map { case (k, i) =>
      k.name -> ujson.Obj("type" -> "noul", "noul" -> (i + 1) / 100.0)
    })
  )
  private def ten = client(new FakeTransport(Right(tenAnswers.render())))

  test("ask with 1 to 10 keys returns the answer of each key at its position") {
    val Vector(k1, k2, k3, k4, k5, k6, k7, k8, k9, k10) = nouls.toVector: @unchecked
    def p(a: NoulAnswer): Double                        = a.probability.value
    def ps(answers: Product): List[Double] = answers.productIterator.collect { case a: NoulAnswer => p(a) }.toList
    def expected(n: Int): List[Double]     = (1 to n).toList.map(_ / 100.0)

    assertEquals(answer(ten.ask(state, k1).map(p)), 0.01)
    assertEquals(ps(answer(ten.ask(state, k1, k2))), expected(2))
    assertEquals(ps(answer(ten.ask(state, k1, k2, k3))), expected(3))
    assertEquals(ps(answer(ten.ask(state, k1, k2, k3, k4))), expected(4))
    assertEquals(ps(answer(ten.ask(state, k1, k2, k3, k4, k5))), expected(5))
    assertEquals(ps(answer(ten.ask(state, k1, k2, k3, k4, k5, k6))), expected(6))
    assertEquals(ps(answer(ten.ask(state, k1, k2, k3, k4, k5, k6, k7))), expected(7))
    assertEquals(ps(answer(ten.ask(state, k1, k2, k3, k4, k5, k6, k7, k8))), expected(8))
    assertEquals(ps(answer(ten.ask(state, k1, k2, k3, k4, k5, k6, k7, k8, k9))), expected(9))
    assertEquals(ps(answer(ten.ask(state, k1, k2, k3, k4, k5, k6, k7, k8, k9, k10))), expected(10))
  }

  test("the keys go into the request in their order") {
    val transport = new FakeTransport(Right(tenAnswers.render()))
    client(transport).ask(state, nouls(2), nouls(0), nouls(1))
    assertEquals(transport.sent.map(ujson.read(_)("questions").obj.keys.toList), List(List("q3", "q1", "q2")))
  }

  test("askMap asks questions known only at runtime, and returns the answers under their names") {
    val transport = FakeTransport.golden("mixed")
    val questions = Map[String, Question[_]](
      "urgent"      -> urgent.question,
      "department"  -> department.question,
      "frustration" -> frustration.question
    )
    val answers = answer(client(transport).askMap(state, questions))
    assertEquals(transport.sent.map(ujson.read(_)("questions")), List(Golden.read("mixed/request.json")("questions")))
    assertEquals(answers.keySet, questions.keySet)
    answers("urgent") match {
      case a: NoulAnswer => assertEquals(a.probability.value, recorded("urgent")("noul").num)
      case other         => fail(s"not a NoulAnswer: $other")
    }
    answers("department") match {
      case a: ChoiceAnswer[_] => assertEquals[Any, Any](a.choice, Dept.Billing)
      case other              => fail(s"not a ChoiceAnswer: $other")
    }
  }

  test("askMap checks the request, and does not send an invalid one") {
    val transport = FakeTransport.golden("mixed")
    val result    = client(transport).askMap(ticket, Map.empty[String, Question[_]])
    assertEquals(result.left.toOption, Some(JevError.InvalidRequest(List(Problem.NoQuestions))))
    assertEquals(transport.sent, Nil)
  }

  test("askMap returns the error of the transport") {
    val result = client(new FakeTransport(Left(JevError.Unauthorized))).askMap(ticket, Map("u" -> Noul("U?")))
    assertEquals(result.left.toOption, Some(JevError.Unauthorized))
  }

  test("more than 10 keys do not compile: they need askMap") {
    val errors = compileErrors(
      "ten.ask(state, nouls(0), nouls(1), nouls(2), nouls(3), nouls(4), nouls(5), " +
        "nouls(6), nouls(7), nouls(8), nouls(9), nouls(0))"
    )
    assert(errors.contains("overloaded method ask"), errors)
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
    assertEquals(result.map(_.probability.value), Right(recorded("urgent")("noul").num))
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

  test("a client built from a config that java.net.http would refuse does not throw: ask returns the error") {
    val config = JevConfig(new ApiKey("k"), "jev-1.13.0", timeout = scala.concurrent.duration.Duration.Zero)
    assertEquals(
      JevClient.create(config).ask(ticket, urgent),
      Left(JevError.InvalidConfig("the timeout must be more than zero: 0 days"))
    )
  }
}
