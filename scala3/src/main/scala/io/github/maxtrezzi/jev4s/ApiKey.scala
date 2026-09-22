package io.github.maxtrezzi.jev4s

/** An API key that is never printed. Its `toString` is `<hidden>`. It is not a case class, so a
  * printer that shows the fields of a case class, such as the diff of a failed test, shows
  * `<hidden>` too.
  */
final class ApiKey(private[jev4s] val value: String):
  override def toString: String = "<hidden>"

  override def equals(other: Any): Boolean = other match
    case key: ApiKey => key.value == value
    case _           => false

  override def hashCode: Int = value.hashCode
