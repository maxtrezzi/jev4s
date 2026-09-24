package io.github.maxtrezzi.jev4s

import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

/** A caller's own `HttpClient`: it sends through `real`, and counts the requests it sends. */
final class CountingHttpClient(real: HttpClient = HttpClient.newHttpClient()) extends HttpClient {
  val sent = new AtomicInteger()

  def send[T](request: HttpRequest, handler: HttpResponse.BodyHandler[T]): HttpResponse[T] = {
    sent.incrementAndGet()
    real.send(request, handler)
  }

  def sendAsync[T](request: HttpRequest, handler: HttpResponse.BodyHandler[T]): CompletableFuture[HttpResponse[T]] =
    real.sendAsync(request, handler)
  def sendAsync[T](
      request: HttpRequest,
      handler: HttpResponse.BodyHandler[T],
      push: HttpResponse.PushPromiseHandler[T]
  ): CompletableFuture[HttpResponse[T]] = real.sendAsync(request, handler, push)
  def cookieHandler()                   = real.cookieHandler()
  def connectTimeout()                  = real.connectTimeout()
  def followRedirects()                 = real.followRedirects()
  def proxy()                           = real.proxy()
  def sslContext()                      = real.sslContext()
  def sslParameters()                   = real.sslParameters()
  def authenticator()                   = real.authenticator()
  def version()                         = real.version()
  def executor()                        = real.executor()
}
