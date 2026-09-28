package io.github.maxtrezzi.jev4s

class QuestionSuite extends munit.FunSuite:

  enum Tier derives CanEqual:
    case Free, Pro

  test("a Choice takes its options from the given JevChoice"):
    given JevChoice[Tier] = JevChoice(ChoiceOption(Tier.Free, "free"), ChoiceOption(Tier.Pro, "pro", Some("Paying")))
    val question          = Choice[Tier]("Which plan?")
    assertEquals(question.instructions, ujson.Str("Which plan?"))
    assertEquals(
      question.options,
      List(ChoiceOption(Tier.Free, "free", None), ChoiceOption(Tier.Pro, "pro", Some("Paying"))),
    )

  test("JevChoice.keys uses each key as its own value"):
    assertEquals(JevChoice.keys("a", "b").options, List(ChoiceOption("a", "a"), ChoiceOption("b", "b")))

  test("Choice.keys asks over keys known at runtime, each key its own value"):
    val items = List("a", "b")
    assertEquals(
      Choice.keys("Which item?", items*),
      Question.Choice[String]("Which item?", List(ChoiceOption("a", "a"), ChoiceOption("b", "b"))),
    )

  test("a Score keeps its levels in order"):
    assertEquals(
      Score("How angry?", "Calm", "Angry"),
      Question.Score[ujson.Value]("How angry?", List(ScaleLevel("Calm", "Calm"), ScaleLevel("Angry", "Angry"))),
    )

  test("a typed Score takes its levels from the given JevScale"):
    given JevScale[Tier] = JevScale(ScaleLevel(Tier.Free, "No payment"), ScaleLevel(Tier.Pro, "Paying"))
    assertEquals(
      Score[Tier]("Which plan?"),
      Question.Score("Which plan?", List(ScaleLevel(Tier.Free, "No payment"), ScaleLevel(Tier.Pro, "Paying"))),
    )

  test("two Choices are equal only when their options are equal"):
    val free = Choice[Tier]("Which plan?")(using JevChoice(ChoiceOption(Tier.Free, "free")))
    val pro  = Choice[Tier]("Which plan?")(using JevChoice(ChoiceOption(Tier.Pro, "pro")))
    assert(free != pro)
    assert(free.hashCode != pro.hashCode)
    assert(free == Question.Choice("Which plan?", List(ChoiceOption(Tier.Free, "free"))))

  test("questions of different kinds can be compared, and differ"):
    assert(Noul("Urgent?") != Score("Urgent?", "No", "Yes"))

  test("a Noul has no criteria unless given"):
    assertEquals(Noul("Urgent?"), Question.Noul("Urgent?", None, None))
