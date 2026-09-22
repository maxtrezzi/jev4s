package io.github.maxtrezzi.jev4s

import scala.compiletime.{constValueOpt, error}

/** A probability in [0, 1].
  *
  * At runtime it is a plain `Double`, with no wrapper and no boxing. There are two ways to create
  * one, and both check the range, so a `Probability` is always in [0, 1]:
  *
  *   - `Probability(0.8)` for a number literal, checked by the compiler;
  *   - [[Probability.from]] for a value known only at runtime.
  */
opaque type Probability = Double

object Probability:

  /** A probability from a number literal, such as `Probability(0.8)`. The compiler checks the
    * range: `Probability(1.5)` does not compile. Write `1.0`, not `1`: the literal must be a
    * `Double`. For a value known only at runtime, use [[from]].
    */
  @SuppressWarnings(
    Array(
      "stryker4s.mutation.EqualityOperator",
      "stryker4s.mutation.LogicalOperator",
      "stryker4s.mutation.ConditionalExpression",
      "stryker4s.mutation.StringLiteral",
    )
  )
  inline def apply[D <: Double & Singleton](inline d: D): Probability =
    inline constValueOpt[D] match
      case None =>
        error("Probability(...) takes a number literal; use Probability.from for a value known only at runtime")
      case Some(_) =>
        inline if d >= 0.0 && d <= 1.0 then d
        else error("a Probability must be between 0 and 1")

  /** A probability, or `None` when `d` is outside [0, 1]. `NaN` is outside: every comparison
    * with `NaN` is false.
    */
  def from(d: Double): Option[Probability] = Option.when(d >= 0.0 && d <= 1.0)(d)

  /** For values that are already known to be in range, such as test fixtures. */
  private[jev4s] def unsafe(d: Double): Probability = d

  given Ordering[Probability]              = Ordering.Double.TotalOrdering
  given CanEqual[Probability, Probability] = CanEqual.derived

  extension (p: Probability)
    def value: Double                   = p
    def >=(other: Probability): Boolean = p >= other
    def >(other: Probability): Boolean  = p > other
    def <=(other: Probability): Boolean = p <= other
    def <(other: Probability): Boolean  = p < other
