package io.github.maxtrezzi.jev4s

import scala.annotation.implicitNotFound

/** A question for Jev. The type parameter is the type of its answer, so the compiler knows what
  * comes back.
  */
enum Question[A]:

  /** A yes/no question. The answer is the probability of "yes". */
  case Noul(instructions: String, whenTrue: Option[String] = None, whenFalse: Option[String] = None)
      extends Question[NoulAnswer]

  /** A position on an ordered scale of 2 to 10 levels, such as `"Calm", "Angry"`. */
  case Score(instructions: String, levels: String*) extends Question[ScoreAnswer]

  /** One option out of 1 to 255. The options come from the given `JevChoice[C]`, and the answer
    * is a value of your own type `C`.
    */
  case Choice[C](instructions: String)(using val choices: JevChoice[C]) extends Question[ChoiceAnswer[C]]

export Question.{Noul, Score, Choice}

/** One option of a Choice: your value, the key Jev sees, and an optional description. */
final case class ChoiceOption[C](value: C, key: String, description: Option[String] = None)

/** Mix into an enum to give each option a description that Jev reads. */
trait Described:
  def description: String

/** The options of a Choice over `C`. */
@implicitNotFound("no options for Choice[${C}]: define a given JevChoice[${C}]")
trait JevChoice[C]:
  def options: List[ChoiceOption[C]]

object JevChoice:

  /** Options written by hand. */
  def apply[C](options: ChoiceOption[C]*): JevChoice[C] = fromList(options.toList)

  /** Options for questions built at runtime: each key is also the value. */
  def keys(keys: String*): JevChoice[String] = fromList(keys.toList.map(k => ChoiceOption(k, k)))

  private def fromList[C](opts: List[ChoiceOption[C]]): JevChoice[C] = new JevChoice[C]:
    val options = opts
