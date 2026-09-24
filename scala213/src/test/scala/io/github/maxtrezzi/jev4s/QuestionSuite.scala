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

  test("a Noul has no criteria unless given") {
    assertEquals(Noul("Urgent?"), Noul("Urgent?", None, None))
  }
}
