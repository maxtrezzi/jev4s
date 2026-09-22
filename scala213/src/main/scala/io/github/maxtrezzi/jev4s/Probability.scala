package io.github.maxtrezzi.jev4s

/** A probability in [0, 1].
  *
  * The only public way to create one is [[Probability.from]], so a `Probability` is always in
  * range. Thresholds are plain `Double`s: Scala 2.13 cannot check a literal at compile time.
  */
final class Probability private (val value: Double) extends AnyVal {
  def >=(threshold: Double): Boolean = value >= threshold
  def >(threshold: Double): Boolean  = value > threshold
  def <=(threshold: Double): Boolean = value <= threshold
  def <(threshold: Double): Boolean  = value < threshold

  override def toString: String = value.toString
}

object Probability {

  /** A probability, or `None` when `d` is outside [0, 1]. `NaN` is outside: every comparison
    * with `NaN` is false.
    */
  def from(d: Double): Option[Probability] = Option.when(d >= 0.0 && d <= 1.0)(new Probability(d))

  /** For values that are already known to be in range, such as test fixtures. */
  private[jev4s] def unsafe(d: Double): Probability = new Probability(d)

  implicit val ordering: Ordering[Probability] =
    Ordering.by[Probability, Double](_.value)(Ordering.Double.TotalOrdering)
}
