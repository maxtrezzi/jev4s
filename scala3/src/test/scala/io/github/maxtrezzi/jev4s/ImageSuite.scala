package io.github.maxtrezzi.jev4s

import java.util.Base64

class ImageSuite extends munit.FunSuite:

  private def bytes(values: Int*): Array[Byte] = values.map(_.toByte).toArray
  private val png                              = bytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3)
  private val jpeg                             = bytes(0xff, 0xd8, 0xff, 0xe0, 4, 5)
  private def image(data: Array[Byte]): Image  = Image(data).fold(e => fail(e.message), identity)

  // The image of golden/box, drawn by build/capture-golden.py, whose ref the script computed itself.
  private val recorded = Golden.read("box/request.json")

  test("a PNG and a JPEG are known by their first bytes"):
    assertEquals(Image(png).map(_.format), Right(ImageFormat.Png))
    assertEquals(Image(jpeg).map(_.format), Right(ImageFormat.Jpeg))
    assertEquals(Image(png.take(8)).map(_.format), Right(ImageFormat.Png))
    assertEquals(Image(jpeg.take(3)).map(_.format), Right(ImageFormat.Jpeg))

  test("no bytes, too few bytes, or another format are not an image"):
    assertEquals(Image(Array.emptyByteArray), Left(ImageError.Empty))
    assertEquals(Image(png.take(7)), Left(ImageError.UnknownFormat))
    assertEquals(Image(jpeg.take(2)), Left(ImageError.UnknownFormat))
    assertEquals(Image("GIF89a".getBytes("US-ASCII")), Left(ImageError.UnknownFormat))
    for at <- 0 until 8 do
      val wrong = png.clone
      wrong(at) = (wrong(at) + 1).toByte
      assertEquals(Image(wrong), Left(ImageError.UnknownFormat), s"byte $at")

  test("an image keeps a copy of the bytes"):
    val data  = png.clone
    val first = image(data)
    data(9) = 42
    assertEquals(first, image(png))
    assertEquals(first.ref, image(png).ref)

  test("two images are equal when their bytes are"):
    assertEquals(image(png), image(png.clone))
    assertEquals(image(png).hashCode, image(png.clone).hashCode)
    assertNotEquals(image(png), image(png :+ 4.toByte))
    assert(!image(png).equals("an image"))

  test("the ref is image- and the first 16 bytes of the SHA-256, in hex, as the recorded request has it"):
    val drawn = Image.fromBase64(recorded("assets").obj.values.head("data_base64").str).toOption.get
    assertEquals(drawn.ref, recorded("state").str)
    assert(image(png).ref.matches("image-[0-9a-f]{32}"), image(png).ref)
    assertNotEquals(image(png).ref, image(jpeg).ref)

  test("size and toString"):
    assertEquals(image(png).size, png.length)
    assertEquals(image(png).toString, s"Image(Png, ${png.length} bytes)")

  test("base64 text is decoded, with its spaces and line breaks ignored"):
    val text = Base64.getEncoder.encodeToString(png)
    assertEquals(Image.fromBase64(text), Right(image(png)))
    assertEquals(Image.fromBase64(text.take(8) + "\n " + text.drop(8) + "\r\n"), Right(image(png)))
    assertEquals(Image.fromBase64("not base64!"), Left(ImageError.NotBase64))
    assertEquals(
      Image.fromBase64(Base64.getEncoder.encodeToString("GIF89a".getBytes("US-ASCII"))),
      Left(ImageError.UnknownFormat),
    )
    assertEquals(Image.fromBase64(" "), Left(ImageError.Empty))

  test("an image is sent as the base64 of its bytes, under the format's name"):
    assertEquals(image(png).base64, Base64.getEncoder.encodeToString(png))
    assertEquals(ImageFormat.Png.wire, "png")
    assertEquals(ImageFormat.Jpeg.wire, "jpeg")

  test("every image error has its own message"):
    assertEquals(ImageError.Empty.message, "the image has no bytes")
    assertEquals(ImageError.UnknownFormat.message, "the image is not a PNG or a JPEG file")
    assertEquals(ImageError.NotBase64.message, "the image text is not base64")
