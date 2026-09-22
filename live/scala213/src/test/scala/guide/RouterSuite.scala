package guide

import io.github.maxtrezzi.jev4s._

// snippet: test
class RouterSuite extends munit.FunSuite {

  /** A reply in the format of the API, for the questions of `Triage`. */
  val reply = """{
    "model": "jev-1.13.0",
    "answers": {
      "team": {"type": "choice", "choice": "billing", "confidence": 0.9,
               "probabilities": {"billing": 0.95, "technical": 0.03, "sales": 0.02}},
      "urgent": {"type": "noul", "noul": 0.97},
      "feeling": {"type": "score", "score": 1.2, "confidence": 0.7,
                  "probabilities": {"0": 0.0, "1": 0.8, "2": 0.2}}
    },
    "usage": {"input_tokens": 300, "output_tokens": 40}
  }"""

  test("an urgent billing ticket goes to Billing today") {
    val client = JevClient.withTransport("jev-1.13.0", _ => Right(reply))
    assertEquals(new Router(client).route(Tickets.doubleCharge), "Billing, today")
  }

  test("when Jev does not answer, a person decides") {
    val client = JevClient.withTransport("jev-1.13.0", _ => Left(JevError.Overloaded))
    assertEquals(new Router(client).route(Tickets.doubleCharge), "a person, because Jev did not answer: Overloaded")
  }
}
// end: test
