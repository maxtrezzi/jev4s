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
  * give it to a question: the question changes too, and looking up a level in a Score's
  * `probabilities` can fail. A `String` and a `ujson.Str` cannot change.
  */
sealed trait Question[A <: Answer] {
  def instructions: ujson.Value

  /** A key that asks this question under `name`, and later reads its answer. */
  def as(name: String): Key[A] = Key(name, this)
}

/** A question with the name it is asked under. `JevClient.ask` takes keys, and returns the answer
  * of each one typed by its question.
  */
final case class Key[A <: Answer](name: String, question: Question[A]) {

  // The client decodes the answer of `question` into an `A`, so the cast cannot fail.
  private[jev4s] def answer(decoded: Answer): A = decoded.asInstanceOf[A]
}

/** A yes/no question. The answer is the probability of "yes". */
final case class Noul(
    instructions: ujson.Value,
    whenTrue: Option[ujson.Value] = None,
    whenFalse: Option[ujson.Value] = None
) extends Question[NoulAnswer]

/** A position on an ordered scale of 2 to 10 levels, from low to high. The answer keys each level
  * by a value of type `L`: the level itself for a Score built from text, or a value of your own
  * type for a Score built with [[Score.of]].
  */
final case class Score[L] private (instructions: ujson.Value, levels: List[ScaleLevel[L]])
    extends Question[ScoreAnswer[L]]

object Score {

  // Private, so that it replaces the public `apply` of the case class: with two public `apply`s,
  // `Score("How?", List("Calm", "Angry"))` would not compile.
  private def apply[L](instructions: ujson.Value, levels: List[ScaleLevel[L]]): Score[L] =
    new Score(instructions, levels)

  /** A Score whose levels are text or JSON, from low to high: `Score("How?", List("Calm",
    * "Angry"))`. The answer keys each probability by the level as you give it here. Ignore the
    * `DummyImplicit`: the compiler fills it in.
    */
  def apply(instructions: ujson.Value, levels: List[ujson.Value])(implicit d: DummyImplicit): Score[ujson.Value] =
    apply[ujson.Value](instructions, levels.map(level => ScaleLevel(level, level)))

  /** A Score whose levels come from the implicit `JevScale[L]`. */
  def of[L](instructions: ujson.Value)(implicit scale: JevScale[L]): Score[L] = apply[L](instructions, scale.levels)
}

/** One level of a Score: your value, and the text or JSON that Jev reads. */
final case class ScaleLevel[L](value: L, text: ujson.Value)

/** The levels of a Score over `L`, from low to high. */
@implicitNotFound("no levels for Score.of[${L}]: define an implicit JevScale[${L}]")
trait JevScale[L] {
  def levels: List[ScaleLevel[L]]
}

object JevScale {

  /** Levels named after your case objects, from low to high in the order you give them:
    * `JevScale.named(Calm, Annoyed, Angry)`. Each level's text is the name of its case object, or
    * its description when it extends [[Described]].
    *
    * `L` can be a sealed trait or a sealed class, with or without `Product`: each case object is
    * a `Product`. List every case: nothing checks that one is missing. Each value's name is its
    * `productPrefix`, so two instances of one case class share a name, and the request fails with
    * [[Problem.DuplicateLevel]].
    */
  def named[L](levels: (L with Product)*): JevScale[L] =
    fromLevels(
      levels.toList.map(level =>
        ScaleLevel[L](level, JevChoice.describe(level).getOrElse(ujson.Str(level.productPrefix)))
      )
    )

  /** Levels written by hand, from low to high. */
  def apply[L](levels: ScaleLevel[L]*): JevScale[L] = fromLevels(levels.toList)

  /** Levels from a list, from low to high. */
  def fromLevels[L](list: List[ScaleLevel[L]]): JevScale[L] = new JevScale[L] {
    val levels: List[ScaleLevel[L]] = list
  }
}

/** One option out of 1 to 255. The answer is a value of your own type `C`. */
final case class Choice[C](instructions: ujson.Value, options: List[ChoiceOption[C]]) extends Question[ChoiceAnswer[C]]

object Choice {

  /** A Choice whose options come from the implicit `JevChoice[C]`. */
  def of[C](instructions: ujson.Value)(implicit choices: JevChoice[C]): Choice[C] =
    Choice(instructions, choices.options)

  /** A Choice over keys known only at runtime: `Choice.keys("Which item?", items: _*)`. Each key
    * is also the value that the answer gives back.
    */
  def keys(instructions: ujson.Value, keys: String*): Choice[String] = of(instructions)(JevChoice.keys(keys: _*))
}

/** One option of a Choice: your value, the key Jev sees, and an optional description. */
final case class ChoiceOption[C](value: C, key: String, description: Option[ujson.Value] = None)

/** Mix into a case object to give its option of a Choice, or its level of a Score, a description
  * that Jev reads. [[JevChoice.named]] and [[JevScale.named]] use it.
  */
trait Described {
  def description: String
}

/** The options of a Choice over `C`. */
@implicitNotFound("no options for Choice[${C}]: define an implicit JevChoice[${C}]")
trait JevChoice[C] {
  def options: List[ChoiceOption[C]]
}

object JevChoice {

  /** Options named after your case objects, in the order you give them:
    * `JevChoice.named(Billing, TechnicalSupport)`. Each key is the name of its case object in
    * snake_case (`TechnicalSupport` → `technical_support`), and its description comes from
    * [[Described]] when the case object extends it.
    *
    * `C` can be a sealed trait or a sealed class, with or without `Product`: each case object is
    * a `Product`. List every case: nothing checks that one is missing. Each value's name is its
    * `productPrefix`, so two instances of one case class share a key, and the request fails with
    * [[Problem.DuplicateOptionKey]].
    */
  def named[C](options: (C with Product)*): JevChoice[C] =
    fromOptions(
      options.toList.map(option => ChoiceOption[C](option, snakeCase(option.productPrefix), describe(option)))
    )

  /** `TechnicalSupport` → `technical_support`, `HTTPError` → `http_error`, `Tier2` → `tier2`. */
  private[jev4s] def snakeCase(name: String): String =
    name
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
      .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
      .toLowerCase(java.util.Locale.ROOT)

  private[jev4s] def describe(value: Any): Option[ujson.Value] = value match {
    case d: Described => Some(ujson.Str(d.description))
    case _            => None
  }

  /** Options written by hand. */
  def apply[C](options: ChoiceOption[C]*): JevChoice[C] = fromOptions(options.toList)

  /** Options from a list. */
  def fromOptions[C](opts: List[ChoiceOption[C]]): JevChoice[C] = new JevChoice[C] {
    val options: List[ChoiceOption[C]] = opts
  }

  /** Options for questions built at runtime: each key is also the value. */
  def keys(keys: String*): JevChoice[String] = fromOptions(keys.toList.map(k => ChoiceOption(k, k)))
}
