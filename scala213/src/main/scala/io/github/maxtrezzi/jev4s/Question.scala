package io.github.maxtrezzi.jev4s

import scala.annotation.implicitNotFound

/** A question for Jev. The type parameter is the type of its answer.
  *
  * The instructions, the criteria of a Noul, the levels of a Score and the descriptions of a
  * Choice's options are text or JSON: pass a `String`, or a `ujson.Value` such as
  * `ujson.Obj("question" -> "Is it urgent?", "focus" -> "The customer's own words")`. JSON with
  * labelled parts helps Jev when a question has several parts, or needs supporting data.
  *
  * A `ujson.Obj` or a `ujson.Arr` can be changed after it is built. Do not change one after you
  * give it to a question: the question changes too, and the keys of a Score's `probabilities`
  * no longer match its levels. A `String` and a `ujson.Str` cannot change.
  */
sealed trait Question[A <: Answer] {
  def instructions: ujson.Value

  /** A key that asks this question under `name`, and later reads its answer. */
  def as(name: String): Key[A] = Key(name, this)
}

/** A question with the name it is asked under. [[Answers.get]] reads its answer, typed. */
final case class Key[A <: Answer](name: String, question: Question[A])

/** A yes/no question. The answer is the probability of "yes". */
final case class Noul(
    instructions: ujson.Value,
    whenTrue: Option[ujson.Value] = None,
    whenFalse: Option[ujson.Value] = None
) extends Question[NoulAnswer]

/** A position on an ordered scale of 2 to 10 levels. */
final case class Score(instructions: ujson.Value, levels: List[ujson.Value]) extends Question[ScoreAnswer]

/** One option out of 1 to 255. The answer is a value of your own type `C`. */
final case class Choice[C](instructions: ujson.Value, options: List[ChoiceOption[C]]) extends Question[ChoiceAnswer[C]]

object Choice {

  /** A Choice whose options come from the implicit `JevChoice[C]`. */
  def of[C](instructions: ujson.Value)(implicit choices: JevChoice[C]): Choice[C] =
    Choice(instructions, choices.options)
}

/** One option of a Choice: your value, the key Jev sees, and an optional description. */
final case class ChoiceOption[C](value: C, key: String, description: Option[ujson.Value] = None)

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
