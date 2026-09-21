package io.github.maxtrezzi.jev4s

/** A probability in [0, 1].
  *
  * The only public way to create one is [[Probability.from]], so a `Probability` is always in
  * range.
  */
final class Probability private (val value: Double) extends AnyVal {
  def >=(threshold: Double): Boolean = value >= threshold
  def >(threshold: Double): Boolean  = value > threshold
  def <=(threshold: Double): Boolean = value <= threshold
  def <(threshold: Double): Boolean  = value < threshold

  override def toString: String = value.toString
}

object Probability {

  /** A probability, or a message when `d` is outside [0, 1]. `NaN` is outside: every comparison
    * with `NaN` is false.
    */
  def from(d: Double): Either[String, Probability] =
    Either.cond(d >= 0.0 && d <= 1.0, new Probability(d), s"probability must be between 0 and 1, got $d")

  /** For values that are already known to be in range, such as test fixtures. */
  private[jev4s] def unsafe(d: Double): Probability = new Probability(d)
}
