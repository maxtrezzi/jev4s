package io.github.maxtrezzi.jev4s

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.net.{InetSocketAddress, URI}
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch}
import scala.jdk.CollectionConverters.*

/** An HTTP server on localhost that answers with `responses`, in order, and records each request.
  * A `Hang` response never answers until the server stops.
  */
final class LocalServer(responses: LocalServer.Response*) extends AutoCloseable:
  import LocalServer.*

  private val queue   = ConcurrentLinkedQueue[Response](responses.asJava)
  private val release = CountDownLatch(1)
  private val server  = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  val requests        = ConcurrentLinkedQueue[Request]()

  server.createContext("/", new HttpHandler { def handle(exchange: HttpExchange): Unit = answer(exchange) })
  server.start()

  val baseUrl: URI = URI.create(s"http://127.0.0.1:${server.getAddress.getPort}")

  private def answer(exchange: HttpExchange): Unit =
    val headers = exchange.getRequestHeaders.asScala.map((k, v) => k.toLowerCase -> v.asScala.mkString(",")).toMap
    requests.add(
      Request(
        exchange.getRequestMethod,
        exchange.getRequestURI.getPath,
        headers,
        String(exchange.getRequestBody.readAllBytes(), "UTF-8"),
      )
    )
    Option(queue.poll()).getOrElse(Reply(500, "no response left")) match
      case Hang                        => release.await()
      case Reply(status, body, extra*) =>
        extra.foreach((k, v) => exchange.getResponseHeaders.add(k, v))
        val bytes = body.getBytes("UTF-8")
        exchange.sendResponseHeaders(status, bytes.length.toLong)
        exchange.getResponseBody.write(bytes)
    exchange.close()

  def close(): Unit =
    release.countDown()
    server.stop(0)

object LocalServer:
  sealed trait Response derives CanEqual
  final case class Reply(status: Int, body: String, headers: (String, String)*) extends Response
  case object Hang                                                              extends Response
  final case class Request(method: String, path: String, headers: Map[String, String], body: String)

  /** A URL on localhost where nothing listens, so a connection is refused at once. A plain socket
    * is bound and closed: a stopped `HttpServer` may still accept connections for a while, and a
    * request to it times out instead.
    */
  def closedUrl(): URI =
    val socket = java.net.ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))
    val port   = socket.getLocalPort
    socket.close()
    URI.create(s"http://127.0.0.1:$port")
