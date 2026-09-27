package guide

// snippet: test
import io.github.maxtrezzi.jev4s.*
import io.github.maxtrezzi.jev4s.testkit.JevTestkit

class RouterSuite extends munit.FunSuite:

  test("an urgent billing ticket goes to Billing today"):
    val client = JevTestkit.answering(triage)((team = Team.Billing, urgent = true, feeling = Feeling.Angry))
    assertEquals(Router(client).route(doubleCharge), "Billing, today")

  test("a ticket that can wait goes to its team"):
    val client = JevTestkit.answering(triage)((team = Team.Technical, urgent = false, feeling = Feeling.Calm))
    assertEquals(Router(client).route(cannotLogIn), "Technical")

  test("a team that Jev is not sure of goes to a person"):
    val unsure = ChoiceAnswer(
      Team.Billing,
      Probability(0.6),
      Map(Team.Billing -> Probability(0.6), Team.Sales -> Probability(0.4)),
    )
    val client = JevTestkit.answering(triage)((team = unsure, urgent = true, feeling = Feeling.Angry))
    assertEquals(Router(client).route(doubleCharge), "a person, because Jev is not sure of the team")

  test("when Jev does not answer, a person decides"):
    val client = JevTestkit.failing(JevError.Overloaded)
    assertEquals(Router(client).route(doubleCharge), "a person, because Jev did not answer: Overloaded")
// end: test
