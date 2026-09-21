package io.github.maxtrezzi.jev4s

class QuestionSuite extends munit.FunSuite:

  enum Tier derives CanEqual:
    case Free, Pro

  test("a Choice takes its options from the given JevChoice"):
    given JevChoice[Tier] = JevChoice(ChoiceOption(Tier.Free, "free"), ChoiceOption(Tier.Pro, "pro", Some("Paying")))
    val question = Choice[Tier]("Which plan?")
    assertEquals(question.instructions, "Which plan?")
    assertEquals(
      question.choices.options,
      List(ChoiceOption(Tier.Free, "free", None), ChoiceOption(Tier.Pro, "pro", Some("Paying"))),
    )

  test("JevChoice.keys uses each key as its own value"):
    assertEquals(JevChoice.keys("a", "b").options, List(ChoiceOption("a", "a"), ChoiceOption("b", "b")))

  test("a Score keeps its levels in order"):
    val Question.Score(instructions, levels*) = Score("How angry?", "Calm", "Angry"): @unchecked
    assertEquals((instructions, levels.toList), ("How angry?", List("Calm", "Angry")))

  test("a Noul has no criteria unless given"):
    assertEquals(Noul("Urgent?"), Question.Noul("Urgent?", None, None))
