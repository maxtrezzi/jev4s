package io.github.maxtrezzi.jev4s

import java.security.MessageDigest
import java.util.{Arrays, Base64, HexFormat}

/** A PNG or JPEG image for Jev to read, built from the bytes of the image file. Put it in the
  * state: `client.ask(image, questions)`, or [[State]] for an image next to other JSON.
  *
  * Images need `JevConfig(dangerZones = Set(DangerZone.Images))`: TypeSafe calls them experimental.
  * The API refuses an image whose width or height is under 280 pixels, or over a limit between
  * 1,536 and 1,791 pixels: measured on 2026-11-01, as the API does not document it.
  *
  * The image keeps a copy of the bytes, so changing the array later changes nothing. Two images
  * are equal when their bytes are.
  */
final class Image private (private val bytes: Array[Byte], val format: ImageFormat):

  /** The text that stands for this image in the state, such as `image-3f2a…`: put it where the
    * image goes, as in `ujson.Obj("photo" -> image.ref)`. It comes from the bytes, so the same
    * image always has the same `ref`.
    */
  val ref: String = "image-" + HexFormat.of.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes), 0, 16)

  /** The size of the image file, in bytes. */
  def size: Int = bytes.length

  private[jev4s] def base64: String = Base64.getEncoder.encodeToString(bytes)

  override def equals(other: Any): Boolean = other match
    case that: Image => Arrays.equals(bytes, that.bytes)
    case _           => false

  override def hashCode: Int = Arrays.hashCode(bytes)

  override def toString: String = s"Image($format, $size bytes)"

object Image:

  /** An image from the bytes of a PNG or JPEG file, or why they are not one. The format comes from
    * the first bytes of the file, not from its name.
    */
  def apply(bytes: Array[Byte]): Either[ImageError, Image] =
    if bytes.isEmpty then Left(ImageError.Empty)
    else ImageFormat.of(bytes).toRight(ImageError.UnknownFormat).map(format => new Image(bytes.clone, format))

  /** An image from the bytes of a PNG or JPEG file, written in base64. Spaces and line breaks are
    * ignored.
    */
  def fromBase64(text: String): Either[ImageError, Image] =
    val compact = text.filterNot(_.isWhitespace)
    try apply(Base64.getDecoder.decode(compact))
    catch case _: IllegalArgumentException => Left(ImageError.NotBase64)

  given CanEqual[Image, Image] = CanEqual.derived

/** The format of an [[Image]]. */
enum ImageFormat derives CanEqual:
  case Png
  case Jpeg

  /** The name the API reads. */
  private[jev4s] def wire: String = this match
    case Png  => "png"
    case Jpeg => "jpeg"

object ImageFormat:

  private val png  = Array(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a).map(_.toByte)
  private val jpeg = Array(0xff, 0xd8, 0xff).map(_.toByte)

  /** The format whose signature starts `bytes`. */
  private[jev4s] def of(bytes: Array[Byte]): Option[ImageFormat] =
    if bytes.startsWith(png) then Some(Png)
    else Option.when(bytes.startsWith(jpeg))(Jpeg)

/** Why bytes are not an [[Image]]. */
enum ImageError derives CanEqual:

  /** There are no bytes. */
  case Empty

  /** The bytes are not a PNG or a JPEG file. */
  case UnknownFormat

  /** The text is not base64. */
  case NotBase64

  def message: String = this match
    case Empty         => "the image has no bytes"
    case UnknownFormat => "the image is not a PNG or a JPEG file"
    case NotBase64     => "the image text is not base64"
