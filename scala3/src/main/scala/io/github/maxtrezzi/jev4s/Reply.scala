package io.github.maxtrezzi.jev4s

/** What a successful reply says besides the answers: the model that answered, which may be newer
  * than the one asked for when you ask for an alias such as `jev-latest`, and the input tokens
  * billed. Output tokens are free, so they are not here.
  */
final case class Reply(model: String, inputTokens: Long) derives CanEqual
