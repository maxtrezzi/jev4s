package io.github.maxtrezzi.jev4s

class AnswerSuite extends munit.FunSuite {

  test("a Noul is yes from 0.5 up") {
    assert(NoulAnswer(Probability.unsafe(0.5)).isYes)
    assert(!NoulAnswer(Probability.unsafe(Math.nextDown(0.5))).isYes)
  }

  test("ifConfident keeps the choice at the threshold and drops it just below") {
    val answer = ChoiceAnswer("billing", Probability.unsafe(0.8), Map("billing" -> Probability.unsafe(0.8)))
    assertEquals(answer.ifConfident(0.8), Some("billing"))
    assertEquals(answer.ifConfident(Math.nextUp(0.8)), None)
  }

  test("every answer type is an Answer") {
    val answers: List[Answer] = List(
      NoulAnswer(Probability.unsafe(0.9)),
      ScoreAnswer[ujson.Value](
        1.3,
        "Calm",
        Probability.unsafe(0.7),
        Map[ujson.Value, Probability](ujson.Str("Calm") -> Probability.unsafe(0.7))
      ),
      ChoiceAnswer(1, Probability.unsafe(0.6), Map(1 -> Probability.unsafe(0.6)))
    )
    val kinds = answers.map {
      case _: NoulAnswer      => "noul"
      case _: ScoreAnswer[_]  => "score"
      case _: ChoiceAnswer[_] => "choice"
    }
    assertEquals(kinds, List("noul", "score", "choice"))
  }
}
