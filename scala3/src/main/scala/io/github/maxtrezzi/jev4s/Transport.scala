package io.github.maxtrezzi.jev4s

/** Sends one request body to `POST /v1/systemone` and returns the body of a successful response,
  * or why there is none. The client never sees HTTP: status codes, headers and retries belong to
  * the transport. Implement it to put a fake in your own tests.
  *
  * The client does not retry. [[JdkTransport]] does, and sends a [[JevEvent.Retrying]] before
  * each retry; a transport of your own retries only if it does so itself.
  */
trait Transport:
  def send(body: String): Either[JevError, String]
