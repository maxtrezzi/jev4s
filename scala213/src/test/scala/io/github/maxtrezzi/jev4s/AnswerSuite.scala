package io.github.maxtrezzi.jev4s

class AnswerSuite extends munit.FunSuite {

  test("every answer can be serialized, as Spark does, and comes back equal") {
    val p       = Probability.unsafe(0.5)
    val answers = List[Answer](
      NoulAnswer(p),
      ScoreAnswer[String](1.0, 0.5, "a", p, Map("a" -> p)),
      ChoiceAnswer[String]("a", p, Map("a" -> p)),
      BoxAnswer(Region(0.1, 0.4, 0.2, 0.8), Some(p))
    )
    for (answer <- answers) {
      val bytes = new java.io.ByteArrayOutputStream()
      new java.io.ObjectOutputStream(bytes).writeObject(answer)
      val back = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray)).readObject()
      assertEquals(back, answer)
    }
  }

  test("a Noul is yes from 0.5 up") {
    assert(NoulAnswer(Probability.unsafe(0.5)).isYes)
    assert(!NoulAnswer(Probability.unsafe(Math.nextDown(0.5))).isYes)
  }

  test("a Noul is confident when yes or no has at least the threshold, and gives the more likely one") {
    def noul(p: Double) = NoulAnswer(Probability.unsafe(p))
    assertEquals(noul(0.9).ifConfident(0.8), Some(true))
    assertEquals(noul(0.8).ifConfident(0.8), Some(true))
    assertEquals(noul(0.2).ifConfident(0.8), Some(false))
    assertEquals(noul(0.1).ifConfident(0.8), Some(false))
    assertEquals(noul(Math.nextDown(0.8)).ifConfident(0.8), None)
    assertEquals(noul(Math.nextUp(0.2)).ifConfident(0.8), None)
    assertEquals(noul(0.5).ifConfident(0.5), Some(true))
    assertEquals(noul(0.4).ifConfident(0.3), Some(false))
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
        0.65,
        "Calm",
        Probability.unsafe(0.7),
        Map[ujson.Value, Probability](ujson.Str("Calm") -> Probability.unsafe(0.7))
      ),
      ChoiceAnswer(1, Probability.unsafe(0.6), Map(1 -> Probability.unsafe(0.6))),
      BoxAnswer(Region(0.1, 0.4, 0.2, 0.8), None)
    )
    val kinds = answers.map {
      case _: NoulAnswer      => "noul"
      case _: ScoreAnswer[_]  => "score"
      case _: ChoiceAnswer[_] => "choice"
      case _: BoxAnswer       => "box"
    }
    assertEquals(kinds, List("noul", "score", "choice", "box"))
  }

  test("a Box answer gives its region at and above the threshold, and never without a confidence") {
    val region = Region(0.1, 0.4, 0.2, 0.8)
    val answer = BoxAnswer(region, Some(Probability.unsafe(0.8)))
    assertEquals(answer.ifConfident(0.8), Some(region))
    assertEquals(answer.ifConfident(0.5), Some(region))
    assertEquals(answer.ifConfident(Math.nextUp(0.8)), None)
    assertEquals(BoxAnswer(region, None).ifConfident(0.0), None)
  }

  test("a region's size does not depend on the order of its edges") {
    val region = Region(top = 0.125, bottom = 0.375, left = 0.25, right = 0.75)
    assertEqualsDouble(region.width, 0.5, 0.0)
    assertEqualsDouble(region.height, 0.25, 0.0)
    val reversed = Region(top = 0.375, bottom = 0.125, left = 0.75, right = 0.25)
    assertEqualsDouble(reversed.width, 0.5, 0.0)
    assertEqualsDouble(reversed.height, 0.25, 0.0)
  }

  test("a region in pixels is its top-left corner and its size, rounded") {
    val region = Region(top = 0.125, bottom = 0.375, left = 0.25, right = 0.75)
    assertEquals(region.inPixels(448, 448), Pixels(x = 112, y = 56, width = 224, height = 112))
    assertEquals(region.inPixels(800, 600), Pixels(x = 200, y = 75, width = 400, height = 150))
    assertEquals(Region(0.375, 0.125, 0.75, 0.25).inPixels(448, 448), Pixels(112, 56, 224, 112))
    assertEquals(Region(0.09, 0.37, 0.23, 0.75).inPixels(100, 10), Pixels(23, 1, 52, 3))
  }
}
