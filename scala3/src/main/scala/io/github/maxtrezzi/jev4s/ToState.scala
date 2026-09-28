package io.github.maxtrezzi.jev4s

import scala.annotation.implicitNotFound

/** How a value of your own type becomes the `state` Jev reads: text, or any JSON. */
@implicitNotFound(
  "no ToState[${S}]: give one, for example `given ToState[${S}] = s => ujson.Obj(...)`, or pass a String or a ujson.Value"
)
trait ToState[S]:
  def toState(state: S): ujson.Value

object ToState:
  given ToState[String]                  = ujson.Str(_)
  given [V <: ujson.Value] => ToState[V] = identity(_)
