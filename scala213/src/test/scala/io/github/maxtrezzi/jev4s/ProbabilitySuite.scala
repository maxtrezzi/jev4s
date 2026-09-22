package io.github.maxtrezzi.jev4s

class ProbabilitySuite extends munit.FunSuite {

  private val belowZero = Math.nextDown(0.0)
  private val aboveOne  = Math.nextUp(1.0)

  test("from accepts both ends of [0, 1] and a value inside") {
    assertEquals(Probability.from(0.0).map(_.value), Some(0.0))
    assertEquals(Probability.from(1.0).map(_.value), Some(1.0))
    assertEquals(Probability.from(0.25).map(_.value), Some(0.25))
  }

  test("from rejects the closest values outside [0, 1]") {
    assertEquals(Probability.from(belowZero), None)
    assertEquals(Probability.from(aboveOne), None)
  }

  test("from rejects NaN") {
    assertEquals(Probability.from(Double.NaN), None)
  }

  test("probabilities sort by value") {
    val list = List(0.9, 0.1, 0.5).map(Probability.unsafe)
    assertEquals(list.sorted.map(_.value), List(0.1, 0.5, 0.9))
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
