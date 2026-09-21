package io.github.maxtrezzi.jev4s

/** The answer to a [[Question.Noul]]: the probability of "yes". */
final case class NoulAnswer(probability: Probability):

  /** True when "yes" is at least as likely as "no". */
  def isYes: Boolean = probability >= 0.5

/** The answer to a [[Question.Score]]: a weighted position on the scale, which may fall between
  * two levels (for example 1.3), and how confident Jev is.
  */
final case class ScoreAnswer(score: Double, confidence: Probability)

/** The answer to a [[Question.Choice]]: the chosen value, how confident Jev is, and the
  * probability of every option.
  */
final case class ChoiceAnswer[C](choice: C, confidence: Probability, probabilities: Map[C, Probability]):

  /** The choice, if the confidence is at least `min`. */
  def ifConfident(min: Double): Option[C] = Option.when(confidence >= min)(choice)

/** Any answer. Used where the questions are only known at runtime. */
type Answer = NoulAnswer | ScoreAnswer | ChoiceAnswer[?]
