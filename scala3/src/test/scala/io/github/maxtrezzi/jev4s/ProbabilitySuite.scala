package io.github.maxtrezzi.jev4s

class ProbabilitySuite extends munit.FunSuite:

  private val belowZero = Math.nextDown(0.0)
  private val aboveOne  = Math.nextUp(1.0)

  test("from accepts both ends of [0, 1] and a value inside"):
    assertEquals(Probability.from(0.0).map(_.value), Some(0.0))
    assertEquals(Probability.from(1.0).map(_.value), Some(1.0))
    assertEquals(Probability.from(0.25).map(_.value), Some(0.25))

  test("from rejects the closest values outside [0, 1]"):
    assertEquals(Probability.from(belowZero), None)
    assertEquals(Probability.from(aboveOne), None)

  test("from rejects NaN"):
    assertEquals(Probability.from(Double.NaN), None)

  test("a literal in range is a Probability, both ends included"):
    assertEquals(Probability(0.0).value, 0.0)
    assertEquals(Probability(0.8).value, 0.8)
    assertEquals(Probability(1.0).value, 1.0)

  test("a literal out of range does not compile"):
    assertEquals(message(compileErrors("Probability(1.5)")), "a Probability must be between 0 and 1")
    assertEquals(message(compileErrors("Probability(-0.1)")), "a Probability must be between 0 and 1")
    assertEquals(message(compileErrors("Probability(Double.NaN)")), "a Probability must be between 0 and 1")

  test("a value known only at runtime does not compile, and the error names from"):
    assertEquals(
      message(compileErrors("val d = 0.5; Probability(d)")),
      "Probability(...) takes a number literal; use Probability.from for a value known only at runtime",
    )

  test("two probabilities compare on both sides and at equality"):
    val p     = Probability.unsafe(0.5)
    val below = Probability.unsafe(Math.nextDown(0.5))
    val above = Probability.unsafe(Math.nextUp(0.5))
    assert(p >= p && p >= below && !(p >= above))
    assert(p > below && !(p > p))
    assert(p <= p && p <= above && !(p <= below))
    assert(p < above && !(p < p))

  test("probabilities sort, and compare with =="):
    val list = List(0.9, 0.1, 0.5).map(Probability.unsafe)
    assertEquals(list.sorted.map(_.value), List(0.1, 0.5, 0.9))
    assert(Probability(0.5) == Probability.unsafe(0.5))

  /** The first line of what `compileErrors` returns, without its `error: ` prefix. */
  private def message(errors: String): String = errors.linesIterator.next().stripPrefix("error: ")
