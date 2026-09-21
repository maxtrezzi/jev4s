package io.github.maxtrezzi.jev4s

class ProbabilitySuite extends munit.FunSuite {

  private val belowZero = Math.nextDown(0.0)
  private val aboveOne  = Math.nextUp(1.0)

  test("from accepts both ends of [0, 1] and a value inside") {
    assertEquals(Probability.from(0.0).map(_.value), Right(0.0))
    assertEquals(Probability.from(1.0).map(_.value), Right(1.0))
    assertEquals(Probability.from(0.25).map(_.value), Right(0.25))
  }

  test("from rejects the closest values outside [0, 1], with the value in the message") {
    assertEquals(Probability.from(belowZero).map(_.value), Left(s"probability must be between 0 and 1, got $belowZero"))
    assertEquals(Probability.from(aboveOne).map(_.value), Left(s"probability must be between 0 and 1, got $aboveOne"))
  }

  test("from rejects NaN") {
    assertEquals(Probability.from(Double.NaN).map(_.value), Left("probability must be between 0 and 1, got NaN"))
  }

  test("comparisons against a threshold, on both sides and at equality") {
    val p = Probability.unsafe(0.5)
    assert(p >= 0.5)
    assert(p >= Math.nextDown(0.5))
    assert(!(p >= Math.nextUp(0.5)))
    assert(p > Math.nextDown(0.5))
    assert(!(p > 0.5))
    assert(p <= 0.5)
    assert(p <= Math.nextUp(0.5))
    assert(!(p <= Math.nextDown(0.5)))
    assert(p < Math.nextUp(0.5))
    assert(!(p < 0.5))
  }

  test("toString is the value, whatever the locale") {
    val saved = java.util.Locale.getDefault
    try {
      java.util.Locale.setDefault(java.util.Locale.ITALY)
      assertEquals(Probability.unsafe(0.25).toString, "0.25")
    } finally java.util.Locale.setDefault(saved)
  }
}
