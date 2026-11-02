package guide

import java.nio.file.{Files, Path}

import io.github.maxtrezzi.jev4s._
import io.github.maxtrezzi.jev4s.testkit._

class ImagesSuite extends munit.FunSuite {
  import Images._

  private val photo = Image(Files.readAllBytes(Path.of("live/images/rectangle.png"))).toOption.get

  test("a confident box is given in pixels") {
    val client = JevTestkit.answering(rectangle.is(Region(0.125, 0.375, 0.25, 0.75)))
    assertEquals(locate(client, photo, 448, 448), Right(Some(Pixels(112, 56, 224, 112))))
  }

  test("a box that Jev is not sure of is left out") {
    val unsure = BoxAnswer(Region(0.1, 0.4, 0.2, 0.8), Probability.from(0.3))
    assertEquals(locate(JevTestkit.answering(rectangle.is(unsure)), photo, 448, 448), Right(None))
  }

  test("a parcel sends its photo and its label") {
    val client = JevTestkit.answering(labelMatches.is(true))
    assertEquals(client.ask(Parcel(photo, "TRK-4471"), labelMatches).map(_.isYes), Right(true))
  }
}
