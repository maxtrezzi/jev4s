package io.github.maxtrezzi.jev4s

/** A transport that answers every request with `reply`, and keeps each body it was sent. */
final class FakeTransport(reply: Either[JevError, String]) extends Transport:
  var sent: List[String]                           = Nil
  def send(body: String): Either[JevError, String] =
    sent = sent :+ body
    reply

object FakeTransport:
  /** A transport that answers with the recorded response of a case in `golden/`. */
  def golden(recorded: String): FakeTransport = FakeTransport(Right(Golden.file(s"$recorded/response.json")))

/** Reads the files of `golden/`, which is a test resource of this module. */
object Golden:
  def file(name: String): String =
    val stream = getClass.getResourceAsStream(s"/$name")
    try String(stream.readAllBytes(), "UTF-8")
    finally stream.close()

  def read(name: String): ujson.Value = ujson.read(file(name))
