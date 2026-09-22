package io.github.maxtrezzi.jev4s

import scala.annotation.implicitNotFound

/** How a value of your own type becomes the `state` Jev reads: text, or any JSON. */
@implicitNotFound("no ToState[${S}]: define an implicit ToState[${S}], or pass a String or a ujson.Value")
trait ToState[S] {
  def toState(state: S): ujson.Value
}

object ToState {
  implicit val string: ToState[String]             = ujson.Str(_)
  implicit def value[V <: ujson.Value]: ToState[V] = v => v
}
