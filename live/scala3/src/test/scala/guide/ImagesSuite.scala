package guide

import java.nio.file.{Files, Path}

import io.github.maxtrezzi.jev4s.*
import io.github.maxtrezzi.jev4s.testkit.JevTestkit

class ImagesSuite extends munit.FunSuite:

  private val photo = Image(Files.readAllBytes(Path.of("live/images/rectangle.png"))).toOption.get
  private val boxes = (rectangle = Box("The red rectangle"))

  test("a confident box is given in pixels"):
    val client = JevTestkit.answering(boxes)((rectangle = Region(0.125, 0.375, 0.25, 0.75)))
    assertEquals(locate(client, photo, 448, 448), Right(Some((x = 112, y = 56, width = 224, height = 112))))

  test("a box that Jev is not sure of is left out"):
    val unsure = BoxAnswer(Region(0.1, 0.4, 0.2, 0.8), Some(Probability(0.3)))
    assertEquals(locate(JevTestkit.answering(boxes)((rectangle = unsure)), photo, 448, 448), Right(None))

  test("a parcel sends its photo and its label"):
    val client = JevTestkit.answering((matches = labelMatches))((matches = true))
    assertEquals(client.ask(Parcel(photo, "TRK-4471"), (matches = labelMatches)).map(_.matches.isYes), Right(true))
