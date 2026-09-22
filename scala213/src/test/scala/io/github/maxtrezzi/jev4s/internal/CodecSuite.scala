package io.github.maxtrezzi.jev4s
package internal

/** The codec on JSON the real API does not produce on demand: every way a reply can be wrong. */
class CodecSuite extends munit.FunSuite {

  private val noul: List[(String, Question[_])]   = List("q" -> Noul("Urgent?"))
  private val score: List[(String, Question[_])]  = List("q" -> Score("How?", List("Low", "High")))
  private val choice: List[(String, Question[_])] = List(
    "q" -> Choice("Which?", List(ChoiceOption(1, "a"), ChoiceOption(2, "b")))
  )

  private def reply(answer: String, model: String = "\"m\"", tokens: String = "3"): String =
    s"""{"model": $model, "answers": {"q": $answer}, "usage": {"input_tokens": $tokens, "output_tokens": 0}}"""

  private def error(body: String, questions: List[(String, Question[_])]): String =
    Codec.decode(body, questions) match {
      case Left(JevError.Decoding(message)) => message
      case other                            => fail(s"expected a Decoding error, got $other")
    }

  private def p(d: Double): Probability = Probability.unsafe(d)

  test("a valid reply of each kind decodes") {
    assertEquals(
      Codec.decode(reply("""{"type": "noul", "noul": 0.25}"""), noul),
      Right(Decoded(List(NoulAnswer(p(0.25))), Reply("m", 3L)))
    )
    assertEquals(
      Codec
        .decode(
          reply("""{"type": "score", "score": 0.5, "confidence": 0.5, "probabilities": {"0": 0.5, "1": 0.5}}"""),
          score
        )
        .map(_.answers),
      Right(
        List[Answer](
          ScoreAnswer(
            0.5,
            p(0.5),
            Map[ujson.Value, Probability](ujson.Str("Low") -> p(0.5), ujson.Str("High") -> p(0.5))
          )
        )
      )
    )
    assertEquals(
      Codec
        .decode(
          reply("""{"type": "choice", "choice": "b", "confidence": 0.75, "probabilities": {"a": 0.25, "b": 0.75}}"""),
          choice
        )
        .map(_.answers),
      Right(List[Answer](ChoiceAnswer(2, p(0.75), Map(1 -> p(0.25), 2 -> p(0.75)))))
    )
  }

  test("a body that is not JSON") {
    assertEquals(error("<html>", noul), "the response is not JSON")
  }

  test("a reply that is not an object, or has no answers") {
    assertEquals(error("[]", noul), "missing field 'answers'")
    assertEquals(error("""{"model": "m"}""", noul), "missing field 'answers'")
  }

  test("a question with no answer") {
    assertEquals(error("""{"model": "m", "answers": {}, "usage": {"input_tokens": 1}}""", noul), "missing field 'q'")
  }

  test("an answer of the wrong type, or with no type") {
    assertEquals(error(reply("""{"type": "score", "noul": 0.5}"""), noul), "'q': expected a noul answer, got score")
    assertEquals(error(reply("""{"noul": 0.5}"""), noul), "missing field 'type'")
    assertEquals(error(reply("""{"type": 1, "noul": 0.5}"""), noul), "'q': expected a string, got 1")
  }

  test("a probability that is out of range or not a number") {
    assertEquals(error(reply("""{"type": "noul", "noul": 1.5}"""), noul), "'q': 1.5 is not a probability")
    assertEquals(error(reply("""{"type": "noul", "noul": "yes"}"""), noul), "'q': expected a number, got \"yes\"")
    assertEquals(
      error(reply("""{"type": "choice", "choice": "a", "confidence": -0.5, "probabilities": {}}"""), choice),
      "'q': -0.5 is not a probability"
    )
  }

  test("a score that is not a number") {
    assertEquals(
      error(reply("""{"type": "score", "score": null, "confidence": 0.5, "probabilities": {}}"""), score),
      "'q': expected a number, got null"
    )
  }

  test("a Score level key that is not a level") {
    val base = """{"type": "score", "score": 0.5, "confidence": 0.5, "probabilities": """
    assertEquals(error(reply(base + """{"2": 0.5}}"""), score), "'q': unknown key '2' in probabilities")
    assertEquals(error(reply(base + """{"-1": 0.5}}"""), score), "'q': unknown key '-1' in probabilities")
    assertEquals(error(reply(base + """{"low": 0.5}}"""), score), "'q': unknown key 'low' in probabilities")
  }

  test("a Choice key that is not an option") {
    assertEquals(
      error(reply("""{"type": "choice", "choice": "c", "confidence": 0.5, "probabilities": {}}"""), choice),
      "'q': 'c' is not one of its options"
    )
    assertEquals(
      error(reply("""{"type": "choice", "choice": "a", "confidence": 0.5, "probabilities": {"c": 0.5}}"""), choice),
      "'q': unknown key 'c' in probabilities"
    )
  }

  test("probabilities that are not an object") {
    assertEquals(
      error(reply("""{"type": "choice", "choice": "a", "confidence": 0.5, "probabilities": [0.5]}"""), choice),
      "'q': probabilities is not an object"
    )
  }

  test("a reply with no model, or a model that is not a string") {
    assertEquals(error("""{"answers": {"q": {"type": "noul", "noul": 0.5}}}""", noul), "missing field 'model'")
    assertEquals(
      error(reply("""{"type": "noul", "noul": 0.5}""", model = "7"), noul),
      "'model': expected a string, got 7"
    )
  }

  test("input tokens that are missing or not a whole number") {
    assertEquals(
      error("""{"model": "m", "answers": {"q": {"type": "noul", "noul": 0.5}}}""", noul),
      "missing field 'usage'"
    )
    assertEquals(
      error(reply("""{"type": "noul", "noul": 0.5}""", tokens = "3.5"), noul),
      "'input_tokens': expected a whole number, got 3.5"
    )
  }

  test("a Noul encodes only the criteria it has") {
    def criteria(q: Question[_]) = Codec.encode("m", ujson.Null, List("q" -> q))("questions")("q").obj.get("criteria")
    assertEquals(criteria(Noul("Urgent?")), None)
    assertEquals(criteria(Noul("Urgent?", whenTrue = Some("yes"))), Some[ujson.Value](ujson.Obj("true" -> "yes")))
    assertEquals(criteria(Noul("Urgent?", whenFalse = Some("no"))), Some[ujson.Value](ujson.Obj("false" -> "no")))
  }

  test("encode keeps the order of the questions") {
    val names = Codec.encode("m", ujson.Null, List("b" -> Noul("B?"), "a" -> Noul("A?")))("questions").obj.keys.toList
    assertEquals(names, List("b", "a"))
  }

  test("an error body that is not the documented shape comes back as it is") {
    assertEquals(Codec.errorMessage("Bad Gateway"), "Bad Gateway")
    assertEquals(Codec.errorMessage("""{"error": "x"}"""), """{"error": "x"}""")
    assertEquals(Codec.errorMessage("""{"detail": {"error_type": "x"}}"""), """{"detail": {"error_type": "x"}}""")
    assertEquals(Codec.errorMessage("""{"detail": "text"}"""), """{"detail": "text"}""")
  }

  test("an error body that is not the documented shape is cut to 200 characters") {
    val page = "<html>" + "x" * 300
    assertEquals(Codec.errorMessage(page.take(200)), page.take(200))
    assertEquals(Codec.errorMessage(page.take(201)), page.take(200) + "…")
    val detail = s"""{"detail": {"error_type": "${"x" * 300}"}}"""
    assertEquals(Codec.errorMessage(detail), detail.take(200) + "…")
  }

  test("a 422 item with no location or no message") {
    assertEquals(Codec.errorMessage("""{"detail": [{"msg": "bad"}, {"loc": ["body", 0]}, 7]}"""), "?: bad; body.0: ?")
  }

  test("a 422 location that is neither a name nor an index is written as JSON") {
    assertEquals(
      Codec.errorMessage("""{"detail": [{"loc": ["body", 0.5, true], "msg": "bad"}]}"""),
      "body.0.5.true: bad"
    )
  }
}
