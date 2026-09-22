package io.github.maxtrezzi.jev4s

import io.github.maxtrezzi.jev4s.internal.{Codec, Validator}

/** A client for Jev. Build one with [[JevClient.create]] to call the real API, or with
  * [[JevClient.withTransport]] to use a transport of your own.
  *
  * Build one client and share it: it is immutable, and safe to use from several threads. A
  * client from `JevClient.create(config)` keeps a `java.net.http.HttpClient`, with its own threads,
  * until the garbage collector frees it, so a new client for each request wastes threads.
  *
  * `onEvent` receives each [[JevEvent]]: a [[JevEvent.Replied]] for every successful reply, and,
  * over [[JdkTransport]], a [[JevEvent.Retrying]] before each retry. It runs on the calling
  * thread, before `ask` returns. An exception it throws is not caught: it reaches the caller of
  * `ask`. Connect it to your logger or metrics:
  *
  * {{{
  * JevClient.create(config, onEvent = {
  *   case JevEvent.Replied(reply)            => log.info(s"answered by \${reply.model}")
  *   case JevEvent.Retrying(error, n, delay) => log.warn(s"retry \$n in \$delay after \$error")
  * })
  * }}}
  */
final class JevClient private (model: String, transport: Transport, onEvent: JevEvent => Unit) {

  /** Asks the questions of `keys`, and returns the answers, read with the same keys:
    *
    * {{{
    * val dept   = Choice.of[Dept]("Which team?").as("dept")
    * val urgent = Noul("Is it urgent?").as("urgent")
    * client.ask(ticket, dept, urgent).map(a => route(a.get(dept), a.get(urgent)))
    * }}}
    */
  def ask[S](state: S, keys: Key[_ <: Answer]*)(implicit toState: ToState[S]): Either[JevError, Answers] = {
    val questions: List[(String, Question[_])] = keys.toList.map(k => k.name -> k.question)
    Validator.validate(questions) match {
      case Nil =>
        for {
          body    <- transport.send(ujson.write(Codec.encode(model, toState.toState(state), questions)))
          decoded <- Codec.decode(body, questions)
        } yield {
          onEvent(JevEvent.Replied(decoded.reply))
          new Answers(
            questions.zip(decoded.answers).map { case ((name, question), answer) => name -> (question -> answer) }.toMap
          )
        }
      case problems => Left(JevError.InvalidRequest(problems))
    }
  }
}

object JevClient {

  /** A client that calls the real API as `config` says, over [[JdkTransport]]. */
  def create(config: JevConfig, onEvent: JevEvent => Unit = _ => ()): JevClient =
    new JevClient(config.model, new JdkTransport(config, onEvent = onEvent), onEvent)

  /** A client for `model` over a transport of your own, such as a fake in tests. The model has
    * no default, because `jev-latest` moves.
    */
  def withTransport(model: String, transport: Transport, onEvent: JevEvent => Unit = _ => ()): JevClient =
    new JevClient(model, transport, onEvent)
}
