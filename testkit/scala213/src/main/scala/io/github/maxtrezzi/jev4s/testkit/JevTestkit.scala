package io.github.maxtrezzi.jev4s
package testkit

/** A key with the answer a test gives it: `urgent.is(true)`, `team.is(Team.Billing)`. Import
  * `io.github.maxtrezzi.jev4s.testkit._` to write them.
  */
final class Answered private[testkit] (val name: String, val question: Question[_], val answer: Any)

/** Clients for tests, which answer with the values you give, and never use the network.
  *
  * {{{
  * import io.github.maxtrezzi.jev4s.testkit._
  *
  * val client = JevTestkit.answering(team.is(Team.Billing), urgent.is(true), feeling.is(Feeling.Annoyed))
  * assertEquals(new Router(client).route(ticket), "Billing, today")
  * }}}
  */
object JevTestkit {

  /** A client that answers every request with `answers`, each under the name of its key.
    *
    * The client decodes the answers from a reply in the format of the API, as it decodes a real
    * one, so what your code reads is what a real reply with these answers gives. A level or an
    * option means all the probability on it, and a confidence of 1: `mostLikely`, `normalized`
    * and `probabilities` follow from it. For another confidence, or other probabilities, give a
    * whole `ScoreAnswer` or `ChoiceAnswer`. The reply names [[defaultModel]], and reports no input
    * tokens.
    *
    * The client does not look at what your code asks. Questions under other names get a
    * `JevError.Decoding` error, as a real reply without their answers would.
    *
    * An answer that is not one of its question's levels or options, or two answers under the same
    * name, throw an `IllegalArgumentException` at once: it is a mistake in the test, reported as an
    * assertion is.
    */
  def answering(answers: Answered*): JevClient = answering(defaultModel, _ => ())(answers: _*)

  /** As `answering(answers)`, with the model that the reply names, and a function that receives
    * each `JevEvent`.
    */
  def answering(model: String, onEvent: JevEvent => Unit)(answers: Answered*): JevClient = {
    val names = answers.map(_.name)
    names.diff(names.distinct).headOption.foreach { name =>
      throw new IllegalArgumentException(s"'$name' has more than one answer")
    }
    val body = FakeReply.body(model, answers.toList.map(a => (a.name, a.question, a.answer)))
    JevClient.withTransport(model, _ => Right(body), onEvent)
  }

  /** A client whose every call fails with `error`, as a real call would after its retries. */
  def failing(error: JevError): JevClient = JevClient.withTransport(defaultModel, _ => Left(error))

  /** The model that a client of the testkit names, unless you give one: `jev4s-testkit`. */
  def defaultModel: String = "jev4s-testkit"
}

/** The reply that the API would send with the given answers. Format:
  * https://docs.typesafe.ai/api.md, checked against the files in `golden/` by the tests.
  */
private[testkit] object FakeReply {

  def body(model: String, answers: List[(String, Question[_], Any)]): String = {
    val json = answers.map { case (name, question, answer) => name -> encode(name, question, answer) }
    ujson.write(ujson.Obj("model" -> model, "answers" -> ujson.Obj.from(json)))
  }

  private def encode(name: String, question: Question[_], answer: Any): ujson.Value = question match {
    case _: Noul =>
      val yes = answer match {
        case yes: Boolean => if (yes) 1.0 else 0.0
        case p            => p.asInstanceOf[Probability].value // `is` gives a Noul a Boolean or a Probability
      }
      ujson.Obj("type" -> "noul", "noul" -> yes)
    case score: Score[_] =>
      val values               = score.levels.map(_.value)
      def index(level: Any)    = position(name, "level", values, level)
      val (points, confidence) = answer match {
        case a: ScoreAnswer[_] => (a.score, a.confidence.value)
        case level             => (index(level).toDouble, 1.0)
      }
      val probabilities = answer match {
        case a: ScoreAnswer[_] => a.probabilities.toList.map { case (level, p) => index(level).toString -> p.value }
        case level             => oneHot(values.indices.map(_.toString).toList, index(level).toString)
      }
      ujson.Obj(
        "type"          -> "score",
        "score"         -> points,
        "confidence"    -> confidence,
        "probabilities" -> ujson.Obj.from(probabilities.map { case (k, p) => k -> ujson.Num(p) })
      )
    case choice: Choice[_] =>
      val keys                                = choice.options.map(_.key)
      def key(value: Any)                     = keys(position(name, "option", choice.options.map(_.value), value))
      val (chosen, confidence, probabilities) = answer match {
        case a: ChoiceAnswer[_] =>
          (key(a.choice), a.confidence.value, a.probabilities.toList.map { case (c, p) => key(c) -> p.value })
        case value => (key(value), 1.0, oneHot(keys, key(value)))
      }
      ujson.Obj(
        "type"          -> "choice",
        "choice"        -> chosen,
        "confidence"    -> confidence,
        "probabilities" -> ujson.Obj.from(probabilities.map { case (k, p) => k -> ujson.Num(p) })
      )
  }

  /** Where `value` is in `values`, compared as a `Map` of the answer compares its keys. */
  private def position(name: String, kind: String, values: List[Any], value: Any): Int =
    values.indexWhere(_ == value) match {
      case -1    => throw new IllegalArgumentException(s"'$name': $value is not one of its ${kind}s")
      case index => index
    }

  /** Probability 1 for `chosen`, and 0 for every other key. */
  private def oneHot(keys: List[String], chosen: String): List[(String, Double)] =
    keys.map(k => k -> (if (k == chosen) 1.0 else 0.0))
}
