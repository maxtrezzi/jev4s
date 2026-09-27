package io.github.maxtrezzi.jev4s

/** Clients for tests: [[testkit.JevTestkit]], and `is` on a key to give it an answer. */
package object testkit {

  /** The answer a test gives to a Noul: `urgent.is(true)`, or the probability of "yes". */
  implicit class NoulAnswers(private val key: Key[NoulAnswer]) extends AnyVal {
    def is(yes: Boolean): Answered             = new Answered(key.name, key.question, yes)
    def is(probability: Probability): Answered = new Answered(key.name, key.question, probability)
  }

  /** The answer a test gives to a Score: `feeling.is(Feeling.Annoyed)`, or a whole answer. */
  implicit class ScoreAnswers[L](private val key: Key[ScoreAnswer[L]]) extends AnyVal {
    def is(level: L): Answered               = new Answered(key.name, key.question, level)
    def is(answer: ScoreAnswer[L]): Answered = new Answered(key.name, key.question, answer)
  }

  /** The answer a test gives to a Choice: `team.is(Team.Billing)`, or a whole answer. */
  implicit class ChoiceAnswers[C](private val key: Key[ChoiceAnswer[C]]) extends AnyVal {
    def is(choice: C): Answered               = new Answered(key.name, key.question, choice)
    def is(answer: ChoiceAnswer[C]): Answered = new Answered(key.name, key.question, answer)
  }
}
