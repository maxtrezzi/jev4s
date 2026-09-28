package io.github.maxtrezzi.jev4s
package testkit

import scala.NamedTuple.NamedTuple

/** What a test gives as the answer to a question of type `Q`:
  *
  *   - for a `Noul`, `true` or `false`, or the `Probability` of "yes";
  *   - for a `Score[L]`, a level, an `L`, or a whole `ScoreAnswer[L]`;
  *   - for a `Choice[C]`, an option, a `C`, or a whole `ChoiceAnswer[C]`.
  */
type FakeAnswer[Q] = AnswerOf[Q] match
  case NoulAnswer      => Boolean | Probability
  case ScoreAnswer[l]  => l | ScoreAnswer[l]
  case ChoiceAnswer[c] => c | ChoiceAnswer[c]

/** Clients for tests, which answer with the values you give, and never use the network.
  *
  * {{{
  * val client = JevTestkit.answering(triage)((team = Team.Billing, urgent = true, feeling = Feeling.Annoyed))
  * assertEquals(Router(client).route(ticket), "Billing, today")
  * }}}
  */
object JevTestkit:

  /** A client that answers every request with `answers`, one for each question of `questions`,
    * under the same names.
    *
    * The client decodes the answers from a reply in the format of the API, as it decodes a real
    * one, so what your code reads is what a real reply with these answers gives. A level or an
    * option means all the probability on it, and a confidence of 1: `mostLikely`, `normalized`
    * and `probabilities` follow from it. For another confidence, or other probabilities, give a
    * whole `ScoreAnswer` or `ChoiceAnswer`. The reply names `model`, [[defaultModel]] unless you
    * give one, and reports no input tokens.
    *
    * The client does not look at what your code asks. Questions under other names get a
    * [[JevError.Decoding]] error, as a real reply without their answers would.
    *
    * @throws IllegalArgumentException
    *   when an answer is not one of its question's levels or options, or a whole answer leaves one
    *   out of its probabilities: a mistake in the test, reported at once, as an assertion is.
    */
  def answering[N <: Tuple, V <: Tuple](
      questions: NamedTuple[N, V],
      model: String = JevTestkit.defaultModel,
      onEvent: JevEvent => Unit = _ => (),
  )(answers: NamedTuple[N, Tuple.Map[V, FakeAnswer]])(using
      names: QuestionNames[N],
      values: AllQuestions[V],
  ): JevClient =
    val triples = names.list.lazyZip(values.list(questions.toTuple)).lazyZip(answers.toTuple.productIterator.toList)
    val body    = FakeReply.body(model, triples.toList)
    JevClient.withTransport(model, _ => Right(body), onEvent)

  /** A client whose every call fails with `error`, as a real call would after its retries. */
  def failing(error: JevError): JevClient = JevClient.withTransport(defaultModel, _ => Left(error))

  /** The model that a client of the testkit names, unless you give one: `jev4s-testkit`. */
  def defaultModel: String = "jev4s-testkit"

/** The reply that the API would send with the given answers. Format:
  * https://docs.typesafe.ai/api.md, checked against the files in `golden/` by the tests.
  */
private[testkit] object FakeReply:

  def body(model: String, answers: List[(String, Question[?], Any)]): String =
    val json = answers.map((name, question, answer) => name -> encode(name, question, answer))
    ujson.write(ujson.Obj("model" -> model, "answers" -> ujson.Obj.from(json)))

  private def encode(name: String, question: Question[?], answer: Any): ujson.Value = question match
    case Question.Noul(_, _, _) =>
      val yes = answer match
        case yes: Boolean => if yes then 1.0 else 0.0
        case p: Double    => p
      ujson.Obj("type" -> "noul", "noul" -> yes)
    case Question.Score(_, levels) =>
      val values              = levels.map(_.value)
      def index(level: Any)   = position(name, "level", values, level)
      val (score, confidence) = answer match
        case a: ScoreAnswer[?] => (a.score, a.confidence.value)
        case level             => (index(level).toDouble, 1.0)
      val probabilities = answer match
        case a: ScoreAnswer[?] =>
          val stated = a.probabilities.toList.map((level, p) => index(level).toString -> p.value)
          complete(name, "level", values, a.probabilities.keys)
          stated
        case level => oneHot(values.indices.map(_.toString).toList, index(level).toString)
      ujson.Obj(
        "type"          -> "score",
        "score"         -> score,
        "confidence"    -> confidence,
        "probabilities" -> ujson.Obj.from(probabilities.map((k, p) => k -> ujson.Num(p))),
      )
    case Question.Choice(_, options) =>
      val keys                                = options.map(_.key)
      def key(choice: Any)                    = keys(position(name, "option", options.map(_.value), choice))
      val (chosen, confidence, probabilities) = answer match
        case a: ChoiceAnswer[?] =>
          val chosen = key(a.choice)
          val stated = a.probabilities.toList.map((c, p) => key(c) -> p.value)
          complete(name, "option", options.map(_.value), a.probabilities.keys)
          (chosen, a.confidence.value, stated)
        case choice => (key(choice), 1.0, oneHot(keys, key(choice)))
      ujson.Obj(
        "type"          -> "choice",
        "choice"        -> chosen,
        "confidence"    -> confidence,
        "probabilities" -> ujson.Obj.from(probabilities.map((k, p) => k -> ujson.Num(p))),
      )

  /** Where `value` is in `values`, compared with `equals`, as a `Map` of the answer compares keys. */
  private def position(name: String, kind: String, values: List[Any], value: Any): Int =
    values.indexWhere(_.equals(value)) match
      case -1    => throw IllegalArgumentException(s"'$name': $value is not one of its ${kind}s")
      case index => index

  /** A whole answer gives every level or option a probability, as a real reply does (ADR-0053). */
  private def complete(name: String, kind: String, values: List[Any], stated: Iterable[Any]): Unit =
    values
      .find(value => !stated.exists(_.equals(value)))
      .foreach: value =>
        throw IllegalArgumentException(s"'$name': the answer gives no probability to its $kind $value")

  /** Probability 1 for `chosen`, and 0 for every other key. */
  private def oneHot(keys: List[String], chosen: String): List[(String, Double)] =
    keys.map(k => k -> (if k == chosen then 1.0 else 0.0))
