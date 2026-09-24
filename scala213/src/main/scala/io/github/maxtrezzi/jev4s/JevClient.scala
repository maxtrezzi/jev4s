package io.github.maxtrezzi.jev4s

import java.net.http.HttpClient

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

  /** Asks the question of one key, and returns its answer, typed by the question. With 2 to 10
    * keys, `ask` returns a tuple of their answers, in the order of the keys:
    *
    * {{{
    * val dept   = Choice.of[Dept]("Which team?").as("dept")
    * val urgent = Noul("Is it urgent?").as("urgent")
    * client.ask(ticket, dept, urgent).map { case (d, u) => route(d.choice, u.isYes) } // d.choice is a Dept
    * }}}
    *
    * For more than 10 questions, or questions known only at runtime, use `askMap`.
    */
  def ask[S, A1 <: Answer](state: S, k1: Key[A1])(implicit
      toState: ToState[S]
  ): Either[JevError, A1] =
    byKeys(state, k1).map(a => k1.answer(a(0)))

  /** Asks the questions of 2 keys, and returns their answers in the same order. */
  def ask[S, A1 <: Answer, A2 <: Answer](state: S, k1: Key[A1], k2: Key[A2])(implicit
      toState: ToState[S]
  ): Either[JevError, (A1, A2)] =
    byKeys(state, k1, k2).map(a => (k1.answer(a(0)), k2.answer(a(1))))

  /** Asks the questions of 3 keys, and returns their answers in the same order. */
  def ask[S, A1 <: Answer, A2 <: Answer, A3 <: Answer](state: S, k1: Key[A1], k2: Key[A2], k3: Key[A3])(implicit
      toState: ToState[S]
  ): Either[JevError, (A1, A2, A3)] =
    byKeys(state, k1, k2, k3).map(a => (k1.answer(a(0)), k2.answer(a(1)), k3.answer(a(2))))

  /** Asks the questions of 4 keys, and returns their answers in the same order. */
  def ask[S, A1 <: Answer, A2 <: Answer, A3 <: Answer, A4 <: Answer](
      state: S,
      k1: Key[A1],
      k2: Key[A2],
      k3: Key[A3],
      k4: Key[A4]
  )(implicit
      toState: ToState[S]
  ): Either[JevError, (A1, A2, A3, A4)] =
    byKeys(state, k1, k2, k3, k4).map(a => (k1.answer(a(0)), k2.answer(a(1)), k3.answer(a(2)), k4.answer(a(3))))

  /** Asks the questions of 5 keys, and returns their answers in the same order. */
  def ask[S, A1 <: Answer, A2 <: Answer, A3 <: Answer, A4 <: Answer, A5 <: Answer](
      state: S,
      k1: Key[A1],
      k2: Key[A2],
      k3: Key[A3],
      k4: Key[A4],
      k5: Key[A5]
  )(implicit
      toState: ToState[S]
  ): Either[JevError, (A1, A2, A3, A4, A5)] =
    byKeys(state, k1, k2, k3, k4, k5).map(a =>
      (k1.answer(a(0)), k2.answer(a(1)), k3.answer(a(2)), k4.answer(a(3)), k5.answer(a(4)))
    )

  /** Asks the questions of 6 keys, and returns their answers in the same order. */
  def ask[S, A1 <: Answer, A2 <: Answer, A3 <: Answer, A4 <: Answer, A5 <: Answer, A6 <: Answer](
      state: S,
      k1: Key[A1],
      k2: Key[A2],
      k3: Key[A3],
      k4: Key[A4],
      k5: Key[A5],
      k6: Key[A6]
  )(implicit
      toState: ToState[S]
  ): Either[JevError, (A1, A2, A3, A4, A5, A6)] =
    byKeys(state, k1, k2, k3, k4, k5, k6).map(a =>
      (k1.answer(a(0)), k2.answer(a(1)), k3.answer(a(2)), k4.answer(a(3)), k5.answer(a(4)), k6.answer(a(5)))
    )

  /** Asks the questions of 7 keys, and returns their answers in the same order. */
  def ask[S, A1 <: Answer, A2 <: Answer, A3 <: Answer, A4 <: Answer, A5 <: Answer, A6 <: Answer, A7 <: Answer](
      state: S,
      k1: Key[A1],
      k2: Key[A2],
      k3: Key[A3],
      k4: Key[A4],
      k5: Key[A5],
      k6: Key[A6],
      k7: Key[A7]
  )(implicit
      toState: ToState[S]
  ): Either[JevError, (A1, A2, A3, A4, A5, A6, A7)] =
    byKeys(state, k1, k2, k3, k4, k5, k6, k7).map(a =>
      (
        k1.answer(a(0)),
        k2.answer(a(1)),
        k3.answer(a(2)),
        k4.answer(a(3)),
        k5.answer(a(4)),
        k6.answer(a(5)),
        k7.answer(a(6))
      )
    )

  /** Asks the questions of 8 keys, and returns their answers in the same order. */
  def ask[
      S,
      A1 <: Answer,
      A2 <: Answer,
      A3 <: Answer,
      A4 <: Answer,
      A5 <: Answer,
      A6 <: Answer,
      A7 <: Answer,
      A8 <: Answer
  ](state: S, k1: Key[A1], k2: Key[A2], k3: Key[A3], k4: Key[A4], k5: Key[A5], k6: Key[A6], k7: Key[A7], k8: Key[A8])(
      implicit toState: ToState[S]
  ): Either[JevError, (A1, A2, A3, A4, A5, A6, A7, A8)] =
    byKeys(state, k1, k2, k3, k4, k5, k6, k7, k8).map(a =>
      (
        k1.answer(a(0)),
        k2.answer(a(1)),
        k3.answer(a(2)),
        k4.answer(a(3)),
        k5.answer(a(4)),
        k6.answer(a(5)),
        k7.answer(a(6)),
        k8.answer(a(7))
      )
    )

  /** Asks the questions of 9 keys, and returns their answers in the same order. */
  def ask[
      S,
      A1 <: Answer,
      A2 <: Answer,
      A3 <: Answer,
      A4 <: Answer,
      A5 <: Answer,
      A6 <: Answer,
      A7 <: Answer,
      A8 <: Answer,
      A9 <: Answer
  ](
      state: S,
      k1: Key[A1],
      k2: Key[A2],
      k3: Key[A3],
      k4: Key[A4],
      k5: Key[A5],
      k6: Key[A6],
      k7: Key[A7],
      k8: Key[A8],
      k9: Key[A9]
  )(implicit
      toState: ToState[S]
  ): Either[JevError, (A1, A2, A3, A4, A5, A6, A7, A8, A9)] =
    byKeys(state, k1, k2, k3, k4, k5, k6, k7, k8, k9).map(a =>
      (
        k1.answer(a(0)),
        k2.answer(a(1)),
        k3.answer(a(2)),
        k4.answer(a(3)),
        k5.answer(a(4)),
        k6.answer(a(5)),
        k7.answer(a(6)),
        k8.answer(a(7)),
        k9.answer(a(8))
      )
    )

  /** Asks the questions of 10 keys, and returns their answers in the same order. */
  def ask[
      S,
      A1 <: Answer,
      A2 <: Answer,
      A3 <: Answer,
      A4 <: Answer,
      A5 <: Answer,
      A6 <: Answer,
      A7 <: Answer,
      A8 <: Answer,
      A9 <: Answer,
      A10 <: Answer
  ](
      state: S,
      k1: Key[A1],
      k2: Key[A2],
      k3: Key[A3],
      k4: Key[A4],
      k5: Key[A5],
      k6: Key[A6],
      k7: Key[A7],
      k8: Key[A8],
      k9: Key[A9],
      k10: Key[A10]
  )(implicit
      toState: ToState[S]
  ): Either[JevError, (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10)] =
    byKeys(state, k1, k2, k3, k4, k5, k6, k7, k8, k9, k10).map(a =>
      (
        k1.answer(a(0)),
        k2.answer(a(1)),
        k3.answer(a(2)),
        k4.answer(a(3)),
        k5.answer(a(4)),
        k6.answer(a(5)),
        k7.answer(a(6)),
        k8.answer(a(7)),
        k9.answer(a(8)),
        k10.answer(a(9))
      )
    )

  /** Asks questions known only at runtime, and returns the answers under the same names. Match
    * on each answer to read it: `case a: NoulAnswer => a.isYes`.
    */
  def askMap[S](state: S, questions: Map[String, Question[_]])(implicit
      toState: ToState[S]
  ): Either[JevError, Map[String, Answer]] = {
    val pairs = questions.toList
    run(toState.toState(state), pairs).map(answers => pairs.map(_._1).zip(answers).toMap)
  }

  /** The answers of `keys`, in their order: the answer at position `i` is the one of `keys(i)`. */
  private def byKeys[S](state: S, keys: Key[_ <: Answer]*)(implicit
      toState: ToState[S]
  ): Either[JevError, Vector[Answer]] =
    run(toState.toState(state), keys.toList.map(k => k.name -> k.question)).map(_.toVector)

  private def run(state: ujson.Value, questions: List[(String, Question[_])]): Either[JevError, List[Answer]] =
    Validator.validate(questions) match {
      case Nil =>
        for {
          body    <- transport.send(ujson.write(Codec.encode(model, state, questions)))
          decoded <- Codec.decode(body, questions)
        } yield {
          onEvent(JevEvent.Replied(decoded.reply))
          decoded.answers
        }
      case problems => Left(JevError.InvalidRequest(problems))
    }
}

object JevClient {

  /** A client that calls the real API as `config` says, over [[JdkTransport]]. Pass `httpClient`
    * to send the requests with a `java.net.http.HttpClient` of your own: [[JdkTransport]] says
    * what changes, and you close it.
    */
  def create(
      config: JevConfig,
      onEvent: JevEvent => Unit = _ => (),
      httpClient: Option[HttpClient] = None
  ): JevClient =
    new JevClient(config.model, new JdkTransport(config, onEvent = onEvent, httpClient = httpClient), onEvent)

  /** A client for `model` over a transport of your own, such as a fake in tests. The model has
    * no default, because `jev-latest` moves.
    */
  def withTransport(model: String, transport: Transport, onEvent: JevEvent => Unit = _ => ()): JevClient =
    new JevClient(model, transport, onEvent)
}
