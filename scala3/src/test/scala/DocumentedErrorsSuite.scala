// In the empty package, so that the compiler's messages name the documents' own types as a reader
// sees them: `Team`, not `some.package.Team` (ADR-0044).
import scala.io.Source

/** The compile errors that a document shows, each checked against the compiler (ADR-0044).
  *
  * A block of documented errors is the `scala` code block after the line
  * `<!-- compile-errors: <path of the suite> -->`. Each entry starts on a line that ends with
  * `// error: <message>`, and the indented lines after it with no marker belong to it. The message
  * is a list of parts separated by `…`: each part must appear, in order, in the compiler's error
  * text, without the package `io.github.maxtrezzi.jev4s.` and with each run of whitespace made one
  * space. `build/check-docs.py` checks that each entry has its case in the suite, and that the
  * case compiles the same code.
  */
abstract class DocumentedErrorsSuite(document: String) extends munit.FunSuite:

  private lazy val entries: Map[String, String] = DocumentedErrors.entries(document)

  /** A case for the entry whose code is `code`: the code must not compile, with the entry's
    * message. `errors` is `compileErrors` of the same code, written again at the call: passed
    * through an `inline` method, the code can fail with another message, for example an overload
    * error instead of the `@implicitNotFound` text of `Score("How?")`.
    */
  def documented(code: String, errors: String)(using munit.Location): Unit =
    test(code.linesIterator.map(_.trim).mkString(" ")):
      val message = entries.getOrElse(code, fail(s"$document has no documented error with the code:\n$code"))
      assert(errors.nonEmpty, s"compiles, but $document says it does not:\n$code")
      assert(DocumentedErrors.matches(message, errors), s"the message in $document is not the compiler's:\n$errors")

object DocumentedErrors:

  private val Marker = """^<!-- compile-errors: \S+ -->$""".r
  private val Entry  = """^(.*?)\s*// error: (.*)$""".r

  /** Each documented error of `document`, a resource of the tests: its code, and its message. */
  def entries(document: String): Map[String, String] =
    val source = Source.fromResource(document, getClass.getClassLoader)
    val lines  = try source.getLines().toList
    finally source.close()
    blocks(lines).flatMap(parse).toMap

  /** The lines of each code block that follows a marker. */
  private def blocks(lines: List[String]): List[List[String]] =
    lines.indices.toList
      .filter(i => Marker.matches(lines(i).trim) && lines.lift(i + 1).exists(_.startsWith("```")))
      .map(i => lines.drop(i + 2).takeWhile(!_.startsWith("```")))

  private def parse(block: List[String]): List[(String, String)] =
    block
      .foldLeft(List.empty[(String, String)]):
        case (entries, Entry(code, message))                                               => (code, message) :: entries
        case ((code, message) :: rest, line) if line.startsWith(" ") && line.trim.nonEmpty =>
          (s"$code\n${line.stripTrailing}", message) :: rest
        case (entries, _) => entries
      .reverse

  /** True when each part of `message`, between `…`, appears in order in `errors`. */
  def matches(message: String, errors: String): Boolean =
    val text  = normalise(errors.replace("io.github.maxtrezzi.jev4s.", ""))
    val parts = message.split("…").map(normalise).filter(_.nonEmpty).toList
    parts
      .foldLeft(Option(0)): (from, part) =>
        from.flatMap(start => Option(text.indexOf(part, start)).filter(_ >= 0).map(_ + part.length))
      .isDefined

  private def normalise(text: String): String = text.trim.replaceAll("\\s+", " ")
