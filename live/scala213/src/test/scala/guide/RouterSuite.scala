package guide

// snippet: test
import io.github.maxtrezzi.jev4s._
import io.github.maxtrezzi.jev4s.testkit._

class RouterSuite extends munit.FunSuite {
  import Triage._

  test("an urgent billing ticket goes to Billing today") {
    val client = JevTestkit.answering(team.is(Team.Billing), urgent.is(true), feeling.is(Feeling.Angry))
    assertEquals(new Router(client).route(Tickets.doubleCharge), "Billing, today")
  }

  test("a ticket that can wait goes to its team") {
    val client = JevTestkit.answering(team.is(Team.Technical), urgent.is(false), feeling.is(Feeling.Calm))
    assertEquals(new Router(client).route(Tickets.cannotLogIn), "Technical")
  }

  test("a team that Jev is not sure of goes to a person") {
    val p      = (d: Double) => Probability.from(d).get
    val unsure =
      ChoiceAnswer[Team](
        Team.Billing,
        p(0.6),
        Map(Team.Billing -> p(0.6), Team.Technical -> p(0.0), Team.Sales -> p(0.4))
      )
    val client = JevTestkit.answering(team.is(unsure), urgent.is(true), feeling.is(Feeling.Angry))
    assertEquals(new Router(client).route(Tickets.doubleCharge), "a person, because Jev is not sure of the team")
  }

  test("when Jev does not answer, a person decides") {
    val client = JevTestkit.failing(JevError.Overloaded)
    assertEquals(new Router(client).route(Tickets.doubleCharge), "a person, because Jev did not answer: Overloaded")
  }
}
// end: test
