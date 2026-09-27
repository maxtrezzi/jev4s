package io.github.maxtrezzi.jev4s

/** Any answer. */
sealed trait Answer extends Product with Serializable

/** The answer to a [[Noul]]: the probability of "yes". */
final case class NoulAnswer(probability: Probability) extends Answer {

  /** True when "yes" is at least as likely as "no". */
  def isYes: Boolean = probability >= 0.5

  /** The more likely answer, `true` for "yes" and `false` for "no", if its probability is at
    * least `min`. With `ifConfident(0.8)`, a probability of 0.9 gives `Some(true)`, 0.1 gives
    * `Some(false)`, and 0.6 gives `None`: Jev is not sure enough either way.
    */
  def ifConfident(min: Double): Option[Boolean] =
    Option.when(probability.value.max(1 - probability.value) >= min)(isYes)
}

/** The answer to a [[Score]]: a weighted position on the scale, which may fall between two
  * levels (for example 1.3), the level with the highest probability, how confident Jev is, and
  * the probability of every level. Each level is a value of type `L`: a value of your own type, as
  * in `probabilities.get(Mood.Angry)`, or the level as you gave it, as in
  * `probabilities.get("Calm")`. Every level of the question is in `probabilities`: a reply that
  * leaves a level out is a [[JevError.Decoding]].
  *
  * When two levels have the same highest probability, `mostLikely` is the lower one. It is not the
  * level nearest to `score`: with probabilities 0.5, 0, 0.5, the score is 1 and the middle level
  * has no chance at all.
  *
  * `normalized` is the score on a scale from 0 to 1: the score divided by the highest level of the
  * question, so that Scores with different numbers of levels can be compared and combined.
  */
final case class ScoreAnswer[L](
    score: Double,
    normalized: Double,
    mostLikely: L,
    confidence: Probability,
    probabilities: Map[L, Probability]
) extends Answer

/** The answer to a [[Choice]]: the chosen value, how confident Jev is, and the probability of
  * every option. Every option of the question is in `probabilities`: a reply that leaves an option
  * out is a [[JevError.Decoding]].
  */
final case class ChoiceAnswer[C](choice: C, confidence: Probability, probabilities: Map[C, Probability])
    extends Answer {

  /** The choice, if the confidence is at least `min`. */
  def ifConfident(min: Double): Option[C] = Option.when(confidence >= min)(choice)
}
