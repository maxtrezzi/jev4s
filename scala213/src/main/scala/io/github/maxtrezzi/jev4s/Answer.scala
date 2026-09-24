package io.github.maxtrezzi.jev4s

/** Any answer. */
sealed trait Answer extends Product with Serializable

/** The answer to a [[Noul]]: the probability of "yes". */
final case class NoulAnswer(probability: Probability) extends Answer {

  /** True when "yes" is at least as likely as "no". */
  def isYes: Boolean = probability >= 0.5
}

/** The answer to a [[Score]]: a weighted position on the scale, which may fall between two
  * levels (for example 1.3), the level with the highest probability, how confident Jev is, and
  * the probability of every level. Each level is a value of type `L`: a value of your own type, as
  * in `probabilities.get(Mood.Angry)`, or the level as you gave it, as in
  * `probabilities.get("Calm")`.
  *
  * When two levels have the same highest probability, `mostLikely` is the lower one. It is not the
  * level nearest to `score`: with probabilities 0.5, 0, 0.5, the score is 1 and the middle level
  * has no chance at all.
  */
final case class ScoreAnswer[L](
    score: Double,
    mostLikely: L,
    confidence: Probability,
    probabilities: Map[L, Probability]
) extends Answer

/** The answer to a [[Choice]]: the chosen value, how confident Jev is, and the probability of
  * every option.
  */
final case class ChoiceAnswer[C](choice: C, confidence: Probability, probabilities: Map[C, Probability])
    extends Answer {

  /** The choice, if the confidence is at least `min`. */
  def ifConfident(min: Double): Option[C] = Option.when(confidence >= min)(choice)
}
