package io.github.maxtrezzi.jev4s

import scala.annotation.implicitNotFound
import scala.deriving.Mirror

/** A question for Jev. The type parameter is the type of its answer, so the compiler knows what
  * comes back.
  *
  * The instructions, the criteria of a Noul, the levels of a Score and the descriptions of a
  * Choice's options are text or JSON: pass a `String`, or a `ujson.Value` such as
  * `ujson.Obj("question" -> "Is it urgent?", "focus" -> "The customer's own words")`. JSON with
  * labelled parts helps Jev when a question has several parts, or needs supporting data.
  *
  * A `ujson.Obj` or a `ujson.Arr` can be changed after it is built. Do not change one after you
  * give it to a question: the question changes too, and the keys of a Score's `probabilities`
  * no longer match its levels. A `String` and a `ujson.Str` cannot change.
  *
  * Two questions are equal when every part of them is equal, the options of a Choice included.
  */
enum Question[A <: Answer]:

  /** A yes/no question. The answer is the probability of "yes". */
  case Noul(instructions: ujson.Value, whenTrue: Option[ujson.Value] = None, whenFalse: Option[ujson.Value] = None)
      extends Question[NoulAnswer]

  /** A position on an ordered scale of 2 to 10 levels, such as `"Calm", "Angry"`. */
  case Score(instructions: ujson.Value, levels: ujson.Value*) extends Question[ScoreAnswer]

  /** One option out of 1 to 255, and the answer is a value of your own type `C`. Usually built
    * with [[io.github.maxtrezzi.jev4s.Choice]], which takes the options from a `JevChoice[C]`.
    */
  case Choice[C](instructions: ujson.Value, options: List[ChoiceOption[C]]) extends Question[ChoiceAnswer[C]]

object Question:
  given CanEqual[Question[?], Question[?]] = CanEqual.derived

export Question.{Noul, Score}

/** A Choice question. */
type Choice[C] = Question.Choice[C]

object Choice:

  /** A Choice whose options come from the given `JevChoice[C]`: `Choice[Dept]("Which team?")`. */
  def apply[C](instructions: ujson.Value)(using choices: JevChoice[C]): Choice[C] =
    Question.Choice(instructions, choices.options)

/** One option of a Choice: your value, the key Jev sees, and an optional description. */
final case class ChoiceOption[C](value: C, key: String, description: Option[ujson.Value] = None)

/** Mix into an enum to give each option a description that Jev reads. */
trait Described:
  def description: String

/** The options of a Choice over `C`. */
@implicitNotFound("no options for Choice[${C}]: define a given JevChoice[${C}]")
trait JevChoice[C]:
  def options: List[ChoiceOption[C]]

object JevChoice:

  /** Options for an enum whose cases have no parameters: `enum Dept derives JevChoice`. Each
    * case's key is its name in snake_case (`TechnicalSupport` → `technical_support`), and its
    * description comes from [[Described]] when the enum mixes it in.
    */
  def derived[C](using m: Mirror.SumOf[C], cases: Cases[C, m.MirroredElemTypes, m.MirroredElemLabels]): JevChoice[C] =
    fromList(cases.list.map((value, label) => ChoiceOption(value, snakeCase(label), describe(value))))

  /** The cases of an enum, with their names, found by the compiler. */
  @implicitNotFound(
    "JevChoice can be derived only for an enum whose cases have no parameters; write the options of ${C} by hand with JevChoice(...)"
  )
  sealed trait Cases[C, Values <: Tuple, Labels <: Tuple]:
    def list: List[(C, String)]

  object Cases:
    given [C] => Cases[C, EmptyTuple, EmptyTuple]:
      def list: List[(C, String)] = Nil

    given [C, H <: C, T <: Tuple, L <: String, LT <: Tuple]
      => (value: ValueOf[H], label: ValueOf[L], rest: Cases[C, T, LT]) => Cases[C, H *: T, L *: LT]:
      def list: List[(C, String)] = (value.value, label.value) :: rest.list

  /** `TechnicalSupport` → `technical_support`, `HTTPError` → `http_error`, `Tier2` → `tier2`. */
  private[jev4s] def snakeCase(name: String): String =
    name
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
      .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
      .toLowerCase(java.util.Locale.ROOT)

  private def describe(value: Any): Option[ujson.Value] = value match
    case d: Described => Some(ujson.Str(d.description))
    case _            => None

  /** Options written by hand. */
  def apply[C](options: ChoiceOption[C]*): JevChoice[C] = fromList(options.toList)

  /** Options for questions built at runtime: each key is also the value. */
  def keys(keys: String*): JevChoice[String] = fromList(keys.toList.map(k => ChoiceOption(k, k)))

  private def fromList[C](opts: List[ChoiceOption[C]]): JevChoice[C] = new JevChoice[C]:
    val options = opts
