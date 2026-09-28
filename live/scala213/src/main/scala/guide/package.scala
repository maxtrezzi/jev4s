// The examples of docs/guide/scala213.md, which quotes them from here (build/check-docs.py).
// Each main makes one paid call: sbt "scala213Live/runMain guide.FirstQuestion".
import io.github.maxtrezzi.jev4s._

package object guide {

  // snippet: client
  /** One client for the whole program. It is safe to share between threads. */
  lazy val client: JevClient =
    JevConfig.fromEnv("jev-1.13.0") match {
      case Right(config) => JevClient.create(config)
      case Left(problem) => sys.error(problem.message)
    }
  // end: client
}
