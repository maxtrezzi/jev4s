package guide

import java.net.URI

import io.github.maxtrezzi.jev4s.*

// snippet: openrouter
// TYPESAFE_BASE_URL=https://openrouter.ai/api, and your OpenRouter key in TYPESAFE_API_KEY
val openRouter: Either[ConfigError, JevConfig] = JevConfig.fromEnv("jev-1.13")
// end: openrouter

// snippet: vercel
val vercel: Option[JevConfig] =
  sys.env
    .get("AI_GATEWAY_API_KEY")
    .map(_.trim) // a key read from a file often ends with a newline
    .map(key => JevConfig(ApiKey(key), "typesafe-ai/jev", URI.create("https://ai-gateway.vercel.sh/typesafe")))
// end: vercel
