package io.github.maxtrezzi.jev4s

import java.net.http.HttpClient
import scala.NamedTuple.NamedTuple

import io.github.maxtrezzi.jev4s.internal.{Codec, Validator}

/** A client for Jev. Build one with `JevClient(config)` to call the real API, or with
  * [[JevClient.withTransport]] to use a transport of your own.
  *
  * Build one client and share it: it is immutable, and safe to use from several threads. A
  * client from `JevClient(config)` keeps a `java.net.http.HttpClient`, with its own threads,
  * until the garbage collector frees it, so a new client for each request wastes threads.
  *
  * `onEvent` receives each [[JevEvent]]: a [[JevEvent.Replied]] for every successful reply, and,
  * over [[JdkTransport]], a [[JevEvent.Retrying]] before each retry. It runs on the calling
  * thread, before `ask` returns. An exception it throws is not caught: it reaches the caller of
  * `ask`. Connect it to your logger or metrics:
  *
  * {{{
  * JevClient(config, onEvent = {
  *   case JevEvent.Replied(reply)             => log.info(s"answered by \${reply.model}")
  *   case JevEvent.Retrying(error, n, delay)  => log.warn(s"retry \$n in \$delay after \$error")
  * })
  * }}}
  */
final class JevClient private (model: String, transport: Transport, onEvent: JevEvent => Unit):

  /** Asks the questions of a named tuple, and returns a named tuple of answers with the same
    * names, each typed by its question:
    *
    * {{{
    * client.ask(ticket, (dept = Choice[Dept]("Which team?"), urgent = Noul("Is it urgent?")))
    *   .map(r => route(r.dept.choice, r.urgent.isYes))   // r.dept.choice is a Dept
    * }}}
    */
  def ask[S, N <: Tuple, V <: Tuple](state: S, questions: NamedTuple[N, V])(using
      toState: ToState[S],
      names: QuestionNames[N],
      values: AllQuestions[V],
  ): Either[JevError, NamedTuple[N, Tuple.Map[V, AnswerOf]]] =
    val pairs = names.list.zip(values.list(questions.toTuple))
    run(toState.toState(state), pairs).map(answers =>
      Tuple.fromArray(answers.toArray).asInstanceOf[NamedTuple[N, Tuple.Map[V, AnswerOf]]]
    )

  /** Asks questions known only at runtime, and returns the answers under the same names. */
  def askMap[S](state: S, questions: Map[String, Question[?]])(using
      toState: ToState[S]
  ): Either[JevError, Map[String, Answer]] =
    val pairs = questions.toList
    run(toState.toState(state), pairs).map(answers => pairs.map(_._1).zip(answers).toMap)

  private def run(state: ujson.Value, questions: List[(String, Question[?])]): Either[JevError, List[Answer]] =
    Validator.validate(questions) match
      case Nil =>
        for
          body    <- transport.send(ujson.write(Codec.encode(model, state, questions)))
          decoded <- Codec.decode(body, questions)
        yield
          onEvent(JevEvent.Replied(decoded.reply))
          decoded.answers
      case problems => Left(JevError.InvalidRequest(problems))

object JevClient:

  /** A client that calls the real API as `config` says, over [[JdkTransport]]. Pass `httpClient`
    * to send the requests with a `java.net.http.HttpClient` of your own: [[JdkTransport]] says
    * what changes, and you close it.
    */
  def apply(
      config: JevConfig,
      onEvent: JevEvent => Unit = _ => (),
      httpClient: Option[HttpClient] = None,
  ): JevClient =
    new JevClient(config.model, JdkTransport(config, onEvent = onEvent, httpClient = httpClient), onEvent)

  /** A client for `model` over a transport of your own, such as a fake in tests. The model has
    * no default, because `jev-latest` moves.
    */
  def withTransport(model: String, transport: Transport, onEvent: JevEvent => Unit = _ => ()): JevClient =
    new JevClient(model, transport, onEvent)
