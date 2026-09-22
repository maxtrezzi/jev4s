package io.github.maxtrezzi.jev4s

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

  test("a Noul has no criteria unless given") {
    assertEquals(Noul("Urgent?"), Noul("Urgent?", None, None))
  }
}
