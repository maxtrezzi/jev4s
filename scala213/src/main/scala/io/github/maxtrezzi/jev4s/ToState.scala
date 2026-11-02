package io.github.maxtrezzi.jev4s

import scala.annotation.implicitNotFound

/** How a value of your own type becomes the `state` Jev reads: text, or any JSON, and the images
  * it holds.
  *
  * Write only `toState` for a type with no image. For a type with images, put each image's
  * [[Image.ref]] in the JSON, and give the images in `images` as well.
  */
@implicitNotFound("no ToState[${S}]: define an implicit ToState[${S}], or pass a String or a ujson.Value")
trait ToState[S] {
  def toState(state: S): ujson.Value

  /** The images whose `ref` is in the state. None by default. */
  def images(state: S): List[Image] = Nil
}

object ToState {
  implicit val string: ToState[String]             = ujson.Str(_)
  implicit def value[V <: ujson.Value]: ToState[V] = v => v

  /** An image alone is the whole state. */
  implicit val image: ToState[Image] = new ToState[Image] {
    def toState(image: Image): ujson.Value         = ujson.Str(image.ref)
    override def images(image: Image): List[Image] = List(image)
  }

  implicit val state: ToState[State] = new ToState[State] {
    def toState(state: State): ujson.Value         = state.json
    override def images(state: State): List[Image] = state.images.toList
  }
}

/** A state of JSON with images in it: `State(ujson.Obj("photo" -> photo.ref, "caption" -> text),
  * photo)`. Give every image whose `ref` is in the JSON, and no other.
  */
final case class State(json: ujson.Value, images: Image*)
