package io.github.maxtrezzi.jev4s

import java.net.URI

class JevConfigSuite extends munit.FunSuite:

  private def from(env: (String, String)*) = JevConfig.from("jev-1.13.0", env.toMap.get)

  test("the key from the environment, and the default base URL"):
    assertEquals(
      from("TYPESAFE_API_KEY" -> " k "),
      Right(JevConfig(ApiKey("k"), "jev-1.13.0", URI.create("https://api.typesafe.ai"))),
    )

  test("a base URL from the environment"):
    assertEquals(
      from("TYPESAFE_API_KEY" -> "k", "TYPESAFE_BASE_URL" -> "https://proxy.example.com/jev").map(_.baseUrl),
      Right(URI.create("https://proxy.example.com/jev")),
    )
    assertEquals(
      from("TYPESAFE_API_KEY" -> "k", "TYPESAFE_BASE_URL" -> "http://localhost:8080").map(_.baseUrl),
      Right(URI.create("http://localhost:8080")),
    )
    for local <- List("http://127.0.0.1:8080", "http://[::1]:8080/jev") do
      assertEquals(
        from("TYPESAFE_API_KEY" -> "k", "TYPESAFE_BASE_URL" -> local).map(_.baseUrl),
        Right(URI.create(local)),
      )
    assertEquals(
      from("TYPESAFE_API_KEY" -> "k", "TYPESAFE_BASE_URL" -> " ").map(_.baseUrl),
      Right(JevConfig.defaultBaseUrl),
    )

  test("a missing or blank key"):
    assertEquals(from(), Left(ConfigError.MissingApiKey))
    assertEquals(from("TYPESAFE_API_KEY" -> "  "), Left(ConfigError.MissingApiKey))

  test("a base URL that is not https, http on localhost, or has no host"):
    for bad <- List(
        "ftp://host",
        "not a url",
        "https:///path",
        "mailto:someone@example.com",
        "http://proxy.example.com",
      )
    do assertEquals(from("TYPESAFE_API_KEY" -> "k", "TYPESAFE_BASE_URL" -> bad), Left(ConfigError.InvalidBaseUrl(bad)))

  test("an empty model"):
    assertEquals(JevConfig.from(" ", Map("TYPESAFE_API_KEY" -> "k").get), Left(ConfigError.MissingModel))

  test("fromEnv reads the process environment"):
    assertEquals(JevConfig.fromEnv("jev-1.13.0"), JevConfig.from("jev-1.13.0", sys.env.get))

  test("neither toString nor a test library's printer shows the key"):
    val config = JevConfig(ApiKey("secret-key-123"), "jev-1.13.0")
    for text <- List(config.toString, munitPrint(config)) do
      assert(!text.contains("secret-key-123"), text)
      assert(text.contains("<hidden>"), text)

  test("two keys are equal when their text is"):
    assertEquals(ApiKey("a"), ApiKey("a"))
    assertEquals(ApiKey("a").hashCode, ApiKey("a").hashCode)
    assertNotEquals(ApiKey("a"), ApiKey("b"))
    assert(!ApiKey("a").equals("a"))

  test("every config error has a message"):
    assertEquals(ConfigError.MissingModel.message, "the model is empty: name one, such as jev-1.13.0")
    assertEquals(ConfigError.MissingApiKey.message, "TYPESAFE_API_KEY is not set")
    assertEquals(
      ConfigError.InvalidBaseUrl("x").message,
      "TYPESAFE_BASE_URL must be an https URL, or an http URL on localhost: x",
    )

  test("a client built from a config calls its URL"):
    val server = LocalServer(LocalServer.Reply(200, Golden.file("noul/response.json")))
    try
      val client = JevClient(JevConfig(ApiKey("k"), "jev-1.13.0", server.baseUrl))
      assert(client.ask("text", (is_urgent = Noul("Urgent?"))).isRight)
    finally server.close()

  test("a client built from a config sends with the HttpClient passed to it"):
    val server = LocalServer(LocalServer.Reply(200, Golden.file("noul/response.json")))
    try
      val own    = CountingHttpClient()
      val client = JevClient(JevConfig(ApiKey("k"), "jev-1.13.0", server.baseUrl), httpClient = Some(own))
      assert(client.ask("text", (is_urgent = Noul("Urgent?"))).isRight)
      assertEquals(own.sent.get, 1)
    finally server.close()
