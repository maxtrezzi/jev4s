package io.github.maxtrezzi.jev4s

import io.github.maxtrezzi.jev4s.QuestionSuite.Other
import io.github.maxtrezzi.jev4s.internal.Validator

class QuestionSuite extends munit.FunSuite {

  sealed abstract class Tier extends Product with Serializable
  object Tier {
    case object Free extends Tier
    case object Pro  extends Tier
    implicit val choices: JevChoice[Tier] =
      JevChoice(ChoiceOption(Free, "free"), ChoiceOption(Pro, "pro", Some("Paying")))
  }

  test("Choice.of takes its options from the implicit JevChoice") {
    val question = Choice.of[Tier]("Which plan?")
    assertEquals(question.instructions, ujson.Str("Which plan?"))
    assertEquals(
      question.options,
      List(ChoiceOption(Tier.Free, "free", None), ChoiceOption(Tier.Pro, "pro", Some("Paying")))
    )
  }

  test("JevChoice.keys uses each key as its own value") {
    assertEquals(JevChoice.keys("a", "b").options, List(ChoiceOption("a", "a"), ChoiceOption("b", "b")))
  }

  test("a Score keeps its levels in order") {
    val score = Score("How angry?", List("Calm", "Angry"))
    assertEquals(score.instructions, ujson.Str("How angry?"))
    assertEquals(score.levels, List[ScaleLevel[ujson.Value]](ScaleLevel("Calm", "Calm"), ScaleLevel("Angry", "Angry")))
  }

  test("Score.of takes its levels from the implicit JevScale") {
    implicit val scale: JevScale[Tier] = JevScale(ScaleLevel(Tier.Free, "No payment"), ScaleLevel(Tier.Pro, "Paying"))
    val score                          = Score.of[Tier]("Which plan?")
    assertEquals(score.instructions, ujson.Str("Which plan?"))
    assertEquals(score.levels, List(ScaleLevel[Tier](Tier.Free, "No payment"), ScaleLevel[Tier](Tier.Pro, "Paying")))
  }

  test("a Score over an unknown type says what is missing") {
    assert(
      compileErrors("""Score.of[java.time.DayOfWeek]("Which day?")""").contains(
        "no levels for Score.of[java.time.DayOfWeek]: define an implicit JevScale[java.time.DayOfWeek]"
      )
    )
  }

  sealed abstract class Team extends Product with Serializable
  object Team {
    case object Billing          extends Team
    case object TechnicalSupport extends Team
    case object HTTPError        extends Team
  }

  sealed abstract class Plan(val description: String) extends Product with Serializable with Described
  object Plan {
    case object Free extends Plan("No payment")
    case object Pro  extends Plan("Paying")
  }

  test("JevChoice.named keeps the given order, with each case object's name in snake_case") {
    assertEquals(
      JevChoice.named[Team](Team.Billing, Team.TechnicalSupport, Team.HTTPError).options,
      List[ChoiceOption[Team]](
        ChoiceOption(Team.Billing, "billing"),
        ChoiceOption(Team.TechnicalSupport, "technical_support"),
        ChoiceOption(Team.HTTPError, "http_error")
      )
    )
  }

  test("JevChoice.named gives each Described case object its description") {
    assertEquals(
      JevChoice.named[Plan](Plan.Free, Plan.Pro).options,
      List[ChoiceOption[Plan]](
        ChoiceOption(Plan.Free, "free", Some("No payment")),
        ChoiceOption(Plan.Pro, "pro", Some("Paying"))
      )
    )
  }

  test("JevScale.named keeps the given order, with each case object's name or its description") {
    assertEquals(
      JevScale.named[Team](Team.HTTPError, Team.Billing).levels,
      List[ScaleLevel[Team]](ScaleLevel(Team.HTTPError, "HTTPError"), ScaleLevel(Team.Billing, "Billing"))
    )
    assertEquals(
      JevScale.named[Plan](Plan.Free, Plan.Pro).levels,
      List[ScaleLevel[Plan]](ScaleLevel(Plan.Free, "No payment"), ScaleLevel(Plan.Pro, "Paying"))
    )
  }

  test("named takes the case objects of a sealed trait that does not extend Product") {
    implicit val choices: JevChoice[QuestionSuite.Size] = JevChoice.named(QuestionSuite.Small, QuestionSuite.Large)
    implicit val levels: JevScale[QuestionSuite.Size]   = JevScale.named(QuestionSuite.Small, QuestionSuite.Large)
    assertEquals(Choice.of[QuestionSuite.Size]("Which?").options.map(_.key), List("small", "large"))
    assertEquals(Score.of[QuestionSuite.Size]("How?").levels.map(_.text), List[ujson.Value]("Small", "Large"))
  }

  test("two instances of one case class share a name, and the request reports it") {
    val choice = Choice.of[Other]("Which?")(JevChoice.named(Other(1), Other(2)))
    val score  = Score.of[Other]("How?")(JevScale.named(Other(1), Other(2)))
    assertEquals(choice.options.map(_.key), List("other", "other"))
    assertEquals(score.levels.map(_.text), List[ujson.Value]("Other", "Other"))
    assertEquals(
      Validator.validate(List("choice" -> choice, "score" -> score)),
      List(Problem.DuplicateOptionKey("choice", "other"), Problem.DuplicateLevel("score", "Other"))
    )
  }

  test("snake_case splits words, acronyms and digits") {
    assertEquals(JevChoice.snakeCase("billing"), "billing")
    assertEquals(JevChoice.snakeCase("Billing"), "billing")
    assertEquals(JevChoice.snakeCase("TechnicalSupport"), "technical_support")
    assertEquals(JevChoice.snakeCase("HTTPError"), "http_error")
    assertEquals(JevChoice.snakeCase("getHTTPResponseCode"), "get_http_response_code")
    assertEquals(JevChoice.snakeCase("Tier2Plan"), "tier2_plan")
    assertEquals(JevChoice.snakeCase("ABC"), "abc")
  }

  test("snake_case does not depend on the default locale") {
    val saved = java.util.Locale.getDefault
    try {
      java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"))
      assertEquals(JevChoice.snakeCase("Invoice"), "invoice")
    } finally java.util.Locale.setDefault(saved)
  }

  test("Choice.keys asks over keys known at runtime, each key its own value") {
    val items = List("a", "b")
    assertEquals(
      Choice.keys("Which item?", items: _*),
      Choice("Which item?", List(ChoiceOption("a", "a"), ChoiceOption("b", "b")))
    )
  }

  test("a Noul has no criteria unless given") {
    assertEquals(Noul("Urgent?"), Noul("Urgent?", None, None))
  }
}

object QuestionSuite {
  final case class Other(n: Int)

  sealed trait Size
  case object Small extends Size
  case object Large extends Size
}
