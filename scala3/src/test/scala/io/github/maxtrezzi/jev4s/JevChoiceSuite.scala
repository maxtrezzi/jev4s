package io.github.maxtrezzi.jev4s

class JevChoiceSuite extends munit.FunSuite:

  enum Team derives JevChoice:
    case Billing, TechnicalSupport

  enum Plan(val description: String) extends Described derives JevChoice:
    case Free extends Plan("No payment")
    case Pro  extends Plan("Paying")

  test("derived options keep the order of the cases, with snake_case keys"):
    assertEquals(
      summon[JevChoice[Team]].options,
      List(ChoiceOption(Team.Billing, "billing"), ChoiceOption(Team.TechnicalSupport, "technical_support")),
    )

  test("a Described enum gives each option its description"):
    assertEquals(
      summon[JevChoice[Plan]].options,
      List(ChoiceOption(Plan.Free, "free", Some("No payment")), ChoiceOption(Plan.Pro, "pro", Some("Paying"))),
    )

  test("snake_case splits words, acronyms and digits"):
    assertEquals(JevChoice.snakeCase("billing"), "billing")
    assertEquals(JevChoice.snakeCase("Billing"), "billing")
    assertEquals(JevChoice.snakeCase("TechnicalSupport"), "technical_support")
    assertEquals(JevChoice.snakeCase("HTTPError"), "http_error")
    assertEquals(JevChoice.snakeCase("getHTTPResponseCode"), "get_http_response_code")
    assertEquals(JevChoice.snakeCase("Tier2Plan"), "tier2_plan")
    assertEquals(JevChoice.snakeCase("ABC"), "abc")

  test("snake_case does not depend on the default locale"):
    val saved = java.util.Locale.getDefault
    try
      java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"))
      assertEquals(JevChoice.snakeCase("Invoice"), "invoice")
    finally java.util.Locale.setDefault(saved)
