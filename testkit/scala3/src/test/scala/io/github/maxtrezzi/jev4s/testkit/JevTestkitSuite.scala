package io.github.maxtrezzi.jev4s
package testkit

class JevTestkitSuite extends munit.FunSuite:

  enum Team derives JevChoice, CanEqual:
    case Billing, Technical, Sales

  enum Feeling derives JevScale, CanEqual:
    case Calm, Annoyed, Angry

  // Named by the code of the compile error tests; not private, so the unused check does not flag it.
  val triage = (
    team = Choice[Team]("Which team?"),
    urgent = Noul("Is it urgent?"),
    feeling = Score[Feeling]("How does the customer feel?"),
  )

  private def p(d: Double) = Probability.unsafe(d)

  private def ask[N <: Tuple, V <: Tuple](client: JevClient, questions: scala.NamedTuple.NamedTuple[N, V])(using
      QuestionNames[N],
      AllQuestions[V],
  ) = client.ask("any state", questions).fold(e => fail(e.toString), identity)

  private def file(name: String): String =
    val stream = getClass.getResourceAsStream(s"/$name")
    try String(stream.readAllBytes(), "UTF-8")
    finally stream.close()

  test("a level or an option gets all the probability, and a confidence of 1"):
    val client = JevTestkit.answering(triage)((team = Team.Sales, urgent = true, feeling = Feeling.Annoyed))
    val r      = ask(client, triage)
    assertEquals(
      r.team,
      ChoiceAnswer(Team.Sales, p(1.0), Map(Team.Billing -> p(0.0), Team.Technical -> p(0.0), Team.Sales -> p(1.0))),
    )
    assertEquals(r.urgent, NoulAnswer(p(1.0)))
    assertEquals(
      r.feeling,
      ScoreAnswer(
        1.0,
        0.5,
        Feeling.Annoyed,
        p(1.0),
        Map(Feeling.Calm -> p(0.0), Feeling.Annoyed -> p(1.0), Feeling.Angry -> p(0.0)),
      ),
    )

  test("a Noul answers false, or any probability of yes"):
    val urgent = (urgent = Noul("Is it urgent?"))
    assertEquals(ask(JevTestkit.answering(urgent)((urgent = false)), urgent).urgent, NoulAnswer(p(0.0)))
    assertEquals(ask(JevTestkit.answering(urgent)((urgent = Probability(0.3))), urgent).urgent, NoulAnswer(p(0.3)))

  test("the first level of a Score is 0, and a Score of text levels answers with one of them"):
    val risk   = (risk = Score("How risky?", "Low", "Medium", "High"))
    val answer = ask(JevTestkit.answering(risk)((risk = ujson.Str("Low"))), risk).risk
    assertEquals((answer.score, answer.normalized, answer.mostLikely), (0.0, 0.0, ujson.Str("Low")))

  test("a whole answer sets the confidence and the probabilities; the rest follows from the question"):
    val team    = ChoiceAnswer(Team.Technical, p(0.6), Map(Team.Technical -> p(0.6), Team.Billing -> p(0.4)))
    val feeling = ScoreAnswer(1.3, 0.0, Feeling.Calm, p(0.7), Map(Feeling.Annoyed -> p(0.7), Feeling.Angry -> p(0.3)))
    val r       = ask(JevTestkit.answering(triage)((team = team, urgent = Probability(0.9), feeling = feeling)), triage)
    assertEquals(r.team, team)
    assertEquals(r.feeling, feeling.copy(normalized = 0.65, mostLikely = Feeling.Annoyed))

  test("the answers of a real reply come back the same through the testkit"):
    val state = ujson.read(file("mixed/request.json"))("state")
    val mixed = (
      urgent = Noul("Does `message` convey urgency?"),
      department = Choice[String]("Which team should handle `message`?")(using JevChoice.keys("billing", "technical")),
      frustration = Score("How frustrated is the customer?", "Calm", "Frustrated", "Very angry"),
    )
    val real = JevClient
      .withTransport("jev-1.13.0", _ => Right(file("mixed/response.json")))
      .ask(state, mixed)
      .fold(e => fail(e.toString), identity)
    val fake = JevTestkit.answering(mixed)(
      (urgent = real.urgent.probability, department = real.department, frustration = real.frustration)
    )
    assertEquals(ask(fake, mixed), real)

  test("JSON levels and options work as in a real reply"):
    val recorded   = ujson.read(file("structured/request.json"))("questions")
    val structured = (
      risk = Score(recorded("risk")("instructions"), recorded("risk")("criteria").arr.toSeq*),
      requests_credentials = Noul(recorded("requests_credentials")("instructions")),
    )
    val real = JevClient
      .withTransport("jev-1.13.0", _ => Right(file("structured/response.json")))
      .ask("any state", structured)
      .fold(e => fail(e.toString), identity)
    val fake = JevTestkit.answering(structured)(
      (risk = real.risk, requests_credentials = real.requests_credentials.probability)
    )
    assertEquals(ask(fake, structured), real)
    val level = recorded("risk")("criteria")(2)
    assertEquals(
      ask(JevTestkit.answering(structured)((risk = level, requests_credentials = true)), structured).risk.mostLikely,
      level,
    )

  test("the reply names the model, reports no input tokens, and goes to onEvent"):
    var events   = List.empty[JevEvent]
    val urgent   = (urgent = Noul("Is it urgent?"))
    val client   = JevTestkit.answering(urgent, model = "jev-1.13.0", onEvent = e => events :+= e)((urgent = true))
    val answered = JevTestkit.answering(urgent, onEvent = e => events :+= e)((urgent = true))
    ask(client, urgent)
    ask(answered, urgent)
    assertEquals(
      events,
      List(JevEvent.Replied(Reply("jev-1.13.0", None)), JevEvent.Replied(Reply("jev4s-testkit", None))),
    )
    assertEquals(JevTestkit.defaultModel, "jev4s-testkit")

  test("questions under other names are a Decoding error, as a real reply without them would be"):
    val client = JevTestkit.answering((urgent = Noul("Is it urgent?")))((urgent = true))
    assertEquals(
      client.ask("any state", (angry = Noul("Is the customer angry?"))),
      Left(JevError.Decoding("missing field 'angry'")),
    )

  test("an answer that is not one of its question's levels or options throws at once"):
    val risk = (risk = Score("How risky?", "Low", "High"))
    assertEquals(
      intercept[IllegalArgumentException](JevTestkit.answering(risk)((risk = ujson.Str("Medium")))).getMessage,
      "'risk': \"Medium\" is not one of its levels",
    )
    val onlyBilling = (team = Choice[Team]("Which team?")(using JevChoice(ChoiceOption(Team.Billing, "billing"))))
    assertEquals(
      intercept[IllegalArgumentException](JevTestkit.answering(onlyBilling)((team = Team.Sales))).getMessage,
      "'team': Sales is not one of its options",
    )
    val otherKey = ChoiceAnswer(Team.Billing, p(0.5), Map(Team.Billing -> p(0.5), Team.Sales -> p(0.5)))
    assertEquals(
      intercept[IllegalArgumentException](JevTestkit.answering(onlyBilling)((team = otherKey))).getMessage,
      "'team': Sales is not one of its options",
    )
    val otherLevel = ScoreAnswer[ujson.Value](1.0, 1.0, "High", p(1.0), Map(ujson.Str("Medium") -> p(1.0)))
    assertEquals(
      intercept[IllegalArgumentException](JevTestkit.answering(risk)((risk = otherLevel))).getMessage,
      "'risk': \"Medium\" is not one of its levels",
    )

  test("a failing client returns its error on every call"):
    val client = JevTestkit.failing(JevError.Overloaded)
    assertEquals(client.ask("any state", (urgent = Noul("Is it urgent?"))), Left(JevError.Overloaded))
    assertEquals(
      JevTestkit.failing(JevError.Unauthorized).askMap("any state", Map("u" -> Noul("U?"))),
      Left(JevError.Unauthorized),
    )

  test("an answer of the wrong type, or under another name, does not compile"):
    assert(
      compileErrors(
        "JevTestkit.answering(triage)((team = Feeling.Calm, urgent = true, feeling = Feeling.Calm))"
      ).nonEmpty
    )
    assert(
      compileErrors("JevTestkit.answering(triage)((team = Team.Sales, urgent = 0.9, feeling = Feeling.Calm))").nonEmpty
    )
    assert(
      compileErrors("JevTestkit.answering(triage)((tema = Team.Sales, urgent = true, feeling = Feeling.Calm))").nonEmpty
    )
    assert(compileErrors("JevTestkit.answering(triage)((team = Team.Sales, urgent = true))").nonEmpty)
    assertEquals(
      compileErrors("JevTestkit.answering(triage)((team = Team.Sales, urgent = true, feeling = Feeling.Calm))"),
      "",
    )
