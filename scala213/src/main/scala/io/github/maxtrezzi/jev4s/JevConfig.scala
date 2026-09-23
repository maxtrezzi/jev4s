package io.github.maxtrezzi.jev4s

import java.net.URI
import scala.concurrent.duration._
import scala.util.Try

/** What a client needs to reach Jev. The API key is an [[ApiKey]], so nothing prints it.
  *
  * Only [[JevConfig.fromEnv]] checks that the base URL is `https`. A config that you build
  * yourself, with `JevConfig(...)` or `copy`, is not checked: give it an `https` base URL, or the
  * API key travels unencrypted.
  */
final case class JevConfig(
    apiKey: ApiKey,
    model: String,
    baseUrl: URI = JevConfig.defaultBaseUrl,
    timeout: FiniteDuration = 10.seconds,
    retry: RetryPolicy = RetryPolicy()
)

object JevConfig {

  /** `https://api.typesafe.ai`, the default of TypeSafe's official SDKs. */
  @SuppressWarnings(Array("stryker4s.mutation.StringLiteral")) // static: applied by hand (ADR-0020)
  val defaultBaseUrl: URI = URI.create("https://api.typesafe.ai")

  /** A config for `model`, with the API key from `TYPESAFE_API_KEY` and, when it is set, the base
    * URL from `TYPESAFE_BASE_URL`: the variable names of TypeSafe's official SDKs. The model has
    * no default, because `jev-latest` moves.
    *
    * The base URL must be `https`. Plain `http` is accepted only for `localhost`, `127.0.0.1` and
    * `[::1]`, such as a local proxy, because the API key would travel unencrypted.
    */
  def fromEnv(model: String): Either[ConfigError, JevConfig] = from(model, sys.env.get)

  private[jev4s] def from(model: String, env: String => Option[String]): Either[ConfigError, JevConfig] = {
    def set(name: String) = env(name).map(_.trim).filter(_.nonEmpty)
    for {
      _   <- Either.cond(model.trim.nonEmpty, (), ConfigError.MissingModel)
      key <- set("TYPESAFE_API_KEY").toRight(ConfigError.MissingApiKey)
      url <- set("TYPESAFE_BASE_URL").fold[Either[ConfigError, URI]](Right(defaultBaseUrl))(httpUrl)
    } yield JevConfig(new ApiKey(key), model, url)
  }

  private def httpUrl(value: String): Either[ConfigError, URI] =
    Try(URI.create(value)).toOption
      .filter(uri =>
        uri.getHost != null && (uri.getScheme == "https" || (uri.getScheme == "http" && local(uri.getHost)))
      )
      .toRight(ConfigError.InvalidBaseUrl(value))

  private def local(host: String): Boolean = List("localhost", "127.0.0.1", "[::1]").contains(host)
}

/** Why a [[JevConfig]] could not be built from the environment. */
sealed abstract class ConfigError extends Product with Serializable {
  def message: String = this match {
    case ConfigError.MissingModel          => "the model is empty: name one, such as jev-1.13.0"
    case ConfigError.MissingApiKey         => "TYPESAFE_API_KEY is not set"
    case ConfigError.InvalidBaseUrl(value) =>
      s"TYPESAFE_BASE_URL must be an https URL, or an http URL on localhost: $value"
  }
}

object ConfigError {
  case object MissingModel                       extends ConfigError
  case object MissingApiKey                      extends ConfigError
  final case class InvalidBaseUrl(value: String) extends ConfigError
}
