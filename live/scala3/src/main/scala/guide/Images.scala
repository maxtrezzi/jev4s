package guide

import java.nio.file.{Files, Path}

import io.github.maxtrezzi.jev4s.*

// snippet: image-client
/** A client for questions about images: Jev 1.14, with the images danger zone turned on. */
lazy val imageClient: JevClient =
  JevConfig.fromEnv("jev-1.14.0").map(_.copy(dangerZones = Set(DangerZone.Images))) match
    case Right(config) => JevClient(config)
    case Left(problem) => sys.error(problem.message)
// end: image-client

// snippet: image-locate
/** Where the red rectangle is in `photo`, in pixels, if Jev is sure enough. */
def locate(client: JevClient, photo: Image, width: Int, height: Int) =
  client
    .ask(photo, (rectangle = Box("The red rectangle")))
    .map(_.rectangle.ifConfident(Probability(0.8)).map(_.inPixels(width, height)))
// end: image-locate

// snippet: image-state
/** A photo of a parcel, and the label that your system read from it. */
final case class Parcel(photo: Image, label: String)

given ToState[Parcel]:
  def toState(parcel: Parcel): ujson.Value         = ujson.Obj("photo" -> parcel.photo.ref, "label" -> parcel.label)
  override def images(parcel: Parcel): List[Image] = List(parcel.photo)

val labelMatches = Noul("Does the label on the parcel in `photo` show the text in `label`?")
// end: image-state

// snippet: image-ask
@main def images(): Unit =
  Image(Files.readAllBytes(Path.of("live/images/rectangle.png"))) match
    case Left(error)  => println(s"Not an image: ${error.message}")
    case Right(photo) =>
      locate(imageClient, photo, width = 448, height = 448) match
        case Right(Some(p)) => println(s"The rectangle is at (${p.x}, ${p.y}), ${p.width} × ${p.height} pixels")
        case Right(None)    => println("Jev is not sure where the rectangle is")
        case Left(error)    => println(s"Jev did not answer: $error")
// end: image-ask
