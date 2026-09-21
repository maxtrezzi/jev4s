package io.github.maxtrezzi.jev4s

/** A probability in [0, 1].
  *
  * At runtime it is a plain `Double`, with no wrapper and no boxing. The only public way to
  * create one is [[Probability.from]], so a `Probability` is always in range.
  */
opaque type Probability = Double

object Probability:

  /** A probability, or a message when `d` is outside [0, 1]. `NaN` is outside: every comparison
    * with `NaN` is false.
    */
  def from(d: Double): Either[String, Probability] =
    Either.cond(d >= 0.0 && d <= 1.0, d, s"probability must be between 0 and 1, got $d")

  /** For values that are already known to be in range, such as test fixtures. */
  private[jev4s] def unsafe(d: Double): Probability = d

  extension (p: Probability)
    def value: Double                  = p
    def >=(threshold: Double): Boolean = p >= threshold
    def >(threshold: Double): Boolean  = p > threshold
    def <=(threshold: Double): Boolean = p <= threshold
    def <(threshold: Double): Boolean  = p < threshold
