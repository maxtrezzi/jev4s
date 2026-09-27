package io.github.maxtrezzi.jev4s
package testkit

import io.github.maxtrezzi.jev4s.testkit.JevTestkitSuite._

class JevTestkitSuite extends munit.FunSuite {

  private val team    = Choice.of[Team]("Which team?").as("team")
  private val urgent  = Noul("Is it urgent?").as("urgent")
  private val feeling = Score.of[Feeling]("How does the customer feel?").as("feeling")

  private def p(d: Double) = Probability.unsafe(d)

  private def file(name: String): String = {
    val stream = getClass.getResourceAsStream(s"/$name")
    try new String(stream.readAllBytes(), "UTF-8")
    finally stream.close()
  }

  private def ok[A](result: Either[JevError, A]): A = result.fold(e => fail(e.toString), identity)

  test("a level or an option gets all the probability, and a confidence of 1") {
    val client    = JevTestkit.answering(team.is(Team.Sales), urgent.is(true), feeling.is(Feeling.Annoyed))
    val (t, u, f) = ok(client.ask("any state", team, urgent, feeling))
    assertEquals(
      t,
      ChoiceAnswer[Team](
        Team.Sales,
        p(1.0),
        Map(Team.Billing -> p(0.0), Team.Technical -> p(0.0), Team.Sales -> p(1.0))
      )
    )
    assertEquals(u, NoulAnswer(p(1.0)))
    assertEquals(
      f,
      ScoreAnswer[Feeling](
        1.0,
        0.5,
        Feeling.Annoyed,
        p(1.0),
        Map(Feeling.Calm -> p(0.0), Feeling.Annoyed -> p(1.0), Feeling.Angry -> p(0.0))
      )
    )
  }

  test("a Noul answers false, or any probability of yes") {
    assertEquals(ok(JevTestkit.answering(urgent.is(false)).ask("any state", urgent)), NoulAnswer(p(0.0)))
    val probability = Probability.from(0.3).get
    assertEquals(ok(JevTestkit.answering(urgent.is(probability)).ask("any state", urgent)), NoulAnswer(p(0.3)))
  }

  test("the first level of a Score is 0, and a Score of text levels answers with one of them") {
    val risk   = Score("How risky?", List("Low", "Medium", "High")).as("risk")
    val answer = ok(JevTestkit.answering(risk.is(ujson.Str("Low"))).ask("any state", risk))
    assertEquals((answer.score, answer.normalized, answer.mostLikely), (0.0, 0.0, ujson.Str("Low"): ujson.Value))
  }

  test("a whole answer sets the confidence and the probabilities; the rest follows from the question") {
    val t = ChoiceAnswer[Team](
      Team.Technical,
      p(0.6),
      Map(Team.Technical -> p(0.6), Team.Billing -> p(0.4), Team.Sales -> p(0.0))
    )
    val f = ScoreAnswer[Feeling](
      1.3,
      0.0,
      Feeling.Calm,
      p(0.7),
      Map(Feeling.Calm -> p(0.0), Feeling.Annoyed -> p(0.7), Feeling.Angry -> p(0.3))
    )
    val client      = JevTestkit.answering(team.is(t), urgent.is(p(0.9)), feeling.is(f))
    val (tt, _, ff) = ok(client.ask("any state", team, urgent, feeling))
    assertEquals(tt, t)
    assertEquals(ff, f.copy(normalized = 0.65, mostLikely = Feeling.Annoyed))
  }

  test("the answers of a real reply come back the same through the testkit") {
    val state      = ujson.read(file("mixed/request.json"))("state")
    val urgent     = Noul("Does `message` convey urgency?").as("urgent")
    val department =
      Choice.of[String]("Which team should handle `message`?")(JevChoice.keys("billing", "technical")).as("department")
    val frustration =
      Score("How frustrated is the customer?", List("Calm", "Frustrated", "Very angry")).as("frustration")
    val real = ok(
      JevClient
        .withTransport("jev-1.13.0", _ => Right(file("mixed/response.json")))
        .ask(state, urgent, department, frustration)
    )
    val fake = JevTestkit.answering(urgent.is(real._1.probability), department.is(real._2), frustration.is(real._3))
    assertEquals(ok(fake.ask("any state", urgent, department, frustration)), real)
  }

  test("JSON levels and options work as in a real reply") {
    val recorded = ujson.read(file("structured/request.json"))("questions")
    val risk     = Score(recorded("risk")("instructions"), recorded("risk")("criteria").arr.toList).as("risk")
    val real     = ok(
      JevClient.withTransport("jev-1.13.0", _ => Right(file("structured/response.json"))).ask("any state", risk)
    )
    assertEquals(ok(JevTestkit.answering(risk.is(real)).ask("any state", risk)), real)
    val level = recorded("risk")("criteria")(2)
    assertEquals(ok(JevTestkit.answering(risk.is(level)).ask("any state", risk)).mostLikely, level)
  }

  test("the reply names the model, reports no input tokens, and goes to onEvent") {
    var events = List.empty[JevEvent]
    ok(JevTestkit.answering("jev-1.13.0", e => events :+= e)(urgent.is(true)).ask("any state", urgent))
    assertEquals(events, List[JevEvent](JevEvent.Replied(Reply("jev-1.13.0", None))))
    assertEquals(JevTestkit.defaultModel, "jev4s-testkit")
  }

  test("questions under other names are a Decoding error, as a real reply without them would be") {
    val angry = Noul("Is the customer angry?").as("angry")
    assertEquals(
      JevTestkit.answering(urgent.is(true)).ask("any state", angry),
      Left(JevError.Decoding("missing field 'angry'"))
    )
  }

  test("an answer that is not one of its question's levels or options throws at once") {
    val risk = Score("How risky?", List("Low", "High")).as("risk")
    assertEquals(
      intercept[IllegalArgumentException](JevTestkit.answering(risk.is(ujson.Str("Medium")))).getMessage,
      "'risk': \"Medium\" is not one of its levels"
    )
    val onlyBilling = Choice.of[Team]("Which team?")(JevChoice(ChoiceOption[Team](Team.Billing, "billing"))).as("team")
    assertEquals(
      intercept[IllegalArgumentException](JevTestkit.answering(onlyBilling.is(Team.Sales))).getMessage,
      "'team': Sales is not one of its options"
    )
    val otherKey = ChoiceAnswer[Team](Team.Billing, p(0.5), Map(Team.Billing -> p(0.5), Team.Sales -> p(0.5)))
    assertEquals(
      intercept[IllegalArgumentException](JevTestkit.answering(onlyBilling.is(otherKey))).getMessage,
      "'team': Sales is not one of its options"
    )
    val otherLevel = ScoreAnswer[ujson.Value](1.0, 1.0, "High", p(1.0), Map(ujson.Str("Medium") -> p(1.0)))
    assertEquals(
      intercept[IllegalArgumentException](JevTestkit.answering(risk.is(otherLevel))).getMessage,
      "'risk': \"Medium\" is not one of its levels"
    )
  }

  test("a whole answer that leaves a level or an option out of its probabilities throws at once") {
    val t = ChoiceAnswer[Team](Team.Billing, p(1.0), Map(Team.Billing -> p(1.0), Team.Sales -> p(0.0)))
    assertEquals(
      intercept[IllegalArgumentException](
        JevTestkit.answering(team.is(t), urgent.is(true), feeling.is(Feeling.Calm))
      ).getMessage,
      "'team': the answer gives no probability to its option Technical"
    )
    val f = ScoreAnswer[Feeling](0.0, 0.0, Feeling.Calm, p(1.0), Map(Feeling.Calm -> p(1.0), Feeling.Angry -> p(0.0)))
    assertEquals(
      intercept[IllegalArgumentException](
        JevTestkit.answering(team.is(Team.Billing), urgent.is(true), feeling.is(f))
      ).getMessage,
      "'feeling': the answer gives no probability to its level Annoyed"
    )
  }

  test("two answers under the same name throw at once, even for different questions") {
    assertEquals(
      intercept[IllegalArgumentException](JevTestkit.answering(urgent.is(true), urgent.is(false))).getMessage,
      "'urgent' has more than one answer"
    )
    val angry = Noul("Is the customer angry?").as("urgent")
    assertEquals(
      intercept[IllegalArgumentException](
        JevTestkit.answering(team.is(Team.Sales), urgent.is(true), angry.is(false))
      ).getMessage,
      "'urgent' has more than one answer"
    )
  }

  test("a failing client returns its error on every call") {
    assertEquals(JevTestkit.failing(JevError.Overloaded).ask("any state", urgent), Left(JevError.Overloaded))
    assertEquals(
      JevTestkit.failing(JevError.Unauthorized).askMap("any state", Map("u" -> Noul("U?"))),
      Left(JevError.Unauthorized)
    )
  }

  test("an answer of the wrong type does not compile") {
    assert(compileErrors("team.is(Feeling.Calm)").nonEmpty)
    assert(compileErrors("urgent.is(0.9)").nonEmpty)
    assert(compileErrors("feeling.is(Team.Sales)").nonEmpty)
    assertEquals(compileErrors("team.is(Team.Sales)"), "")
  }
}

object JevTestkitSuite {
  sealed abstract class Team extends Product with Serializable
  object Team {
    case object Billing   extends Team
    case object Technical extends Team
    case object Sales     extends Team
    implicit val choices: JevChoice[Team] = JevChoice.named(Billing, Technical, Sales)
  }

  sealed abstract class Feeling extends Product with Serializable
  object Feeling {
    case object Calm    extends Feeling
    case object Annoyed extends Feeling
    case object Angry   extends Feeling
    implicit val levels: JevScale[Feeling] = JevScale.named(Calm, Annoyed, Angry)
  }
}
