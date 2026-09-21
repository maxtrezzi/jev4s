package io.github.maxtrezzi.jev4s

import scala.annotation.implicitNotFound

/** A question for Jev. The type parameter is the type of its answer. */
sealed trait Question[A] {
  def instructions: String
}

/** A yes/no question. The answer is the probability of "yes". */
final case class Noul(instructions: String, whenTrue: Option[String] = None, whenFalse: Option[String] = None)
    extends Question[NoulAnswer]

/** A position on an ordered scale of 2 to 10 levels. */
final case class Score(instructions: String, levels: List[String]) extends Question[ScoreAnswer]

/** One option out of 1 to 255. The answer is a value of your own type `C`. */
final case class Choice[C](instructions: String, options: List[ChoiceOption[C]]) extends Question[ChoiceAnswer[C]]

object Choice {

  /** A Choice whose options come from the implicit `JevChoice[C]`. */
  def of[C](instructions: String)(implicit choices: JevChoice[C]): Choice[C] = Choice(instructions, choices.options)
}

/** One option of a Choice: your value, the key Jev sees, and an optional description. */
final case class ChoiceOption[C](value: C, key: String, description: Option[String] = None)

/** Mix into your options to give each one a description that Jev reads. */
trait Described {
  def description: String
}

/** The options of a Choice over `C`. */
@implicitNotFound("no options for Choice[${C}]: define an implicit JevChoice[${C}]")
trait JevChoice[C] {
  def options: List[ChoiceOption[C]]
}

object JevChoice {

  /** Options written by hand. */
  def apply[C](options: ChoiceOption[C]*): JevChoice[C] = fromOptions(options.toList)

  /** Options from a list. */
  def fromOptions[C](opts: List[ChoiceOption[C]]): JevChoice[C] = new JevChoice[C] {
    val options: List[ChoiceOption[C]] = opts
  }

  /** Options for questions built at runtime: each key is also the value. */
  def keys(keys: String*): JevChoice[String] = fromOptions(keys.toList.map(k => ChoiceOption(k, k)))
}
