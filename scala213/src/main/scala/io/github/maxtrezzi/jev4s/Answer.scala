package io.github.maxtrezzi.jev4s

/** Any answer. */
sealed trait Answer extends Product with Serializable

/** The answer to a [[Noul]]: the probability of "yes". */
final case class NoulAnswer(probability: Probability) extends Answer {

  /** True when "yes" is at least as likely as "no". */
  def isYes: Boolean = probability >= 0.5
}

/** The answer to a [[Score]]: a weighted position on the scale, which may fall between two
  * levels (for example 1.3), and how confident Jev is.
  */
final case class ScoreAnswer(score: Double, confidence: Probability) extends Answer

/** The answer to a [[Choice]]: the chosen value, how confident Jev is, and the probability of
  * every option.
  */
final case class ChoiceAnswer[C](choice: C, confidence: Probability, probabilities: Map[C, Probability])
    extends Answer {

  /** The choice, if the confidence is at least `min`. */
  def ifConfident(min: Double): Option[C] = Option.when(confidence >= min)(choice)
}
