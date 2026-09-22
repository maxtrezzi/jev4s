package io.github.maxtrezzi.jev4s

class AnswerSuite extends munit.FunSuite:

  test("a Noul is yes from 0.5 up"):
    assert(NoulAnswer(Probability.unsafe(0.5)).isYes)
    assert(NoulAnswer(Probability.unsafe(0.9)).isYes)
    assert(!NoulAnswer(Probability.unsafe(Math.nextDown(0.5))).isYes)

  test("ifConfident keeps the choice at and above the threshold, and drops it just below"):
    val answer = ChoiceAnswer("billing", Probability.unsafe(0.8), Map("billing" -> Probability.unsafe(0.8)))
    assertEquals(answer.ifConfident(Probability(0.8)), Some("billing"))
    assertEquals(answer.ifConfident(Probability(0.5)), Some("billing"))
    assertEquals(answer.ifConfident(Probability.unsafe(Math.nextUp(0.8))), None)

  test("the answer union covers the three answer types"):
    val answers: List[Answer] = List(
      NoulAnswer(Probability.unsafe(0.9)),
      ScoreAnswer(1.3, Probability.unsafe(0.7), Map(ujson.Str("Calm") -> Probability.unsafe(0.7))),
      ChoiceAnswer(1, Probability.unsafe(0.6), Map(1 -> Probability.unsafe(0.6))),
    )
    val kinds = answers.map:
      case _: NoulAnswer      => "noul"
      case _: ScoreAnswer     => "score"
      case _: ChoiceAnswer[?] => "choice"
    assertEquals(kinds, List("noul", "score", "choice"))
