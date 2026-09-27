package guide

import java.net.{InetSocketAddress, ProxySelector}
import java.net.http.HttpClient
import scala.concurrent.duration.*

import io.github.maxtrezzi.jev4s.*

// snippet: config
val patient: Either[ConfigError, JevConfig] =
  JevConfig
    .fromEnv("jev-1.13.0")
    .map(_.copy(timeout = 30.seconds, retry = RetryPolicy(maxRetries = 4, maxElapsed = 2.minutes)))

val noRetries: Either[ConfigError, JevConfig] = JevConfig.fromEnv("jev-1.13.0").map(_.copy(retry = RetryPolicy.none))
// end: config

// snippet: by-hand
def fromVault(secret: String): JevConfig = JevConfig(ApiKey(secret), model = "jev-1.13.0")
// end: by-hand

// snippet: http-client
/** A client over an HTTP client of yours: here, one that goes through your company's proxy. */
def throughProxy(config: JevConfig, proxy: InetSocketAddress): JevClient =
  val http = HttpClient.newBuilder().proxy(ProxySelector.of(proxy)).build()
  JevClient(config, httpClient = Some(http))
// end: http-client
