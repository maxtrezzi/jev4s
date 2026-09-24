package guide

import io.github.maxtrezzi.jev4s._

// snippet: domain
final case class Customer(name: String, plan: String)
final case class Order(id: String, item: String, chargesUsd: List[Double])
final case class Ticket(customer: Customer, order: Order, message: String)
// end: domain

// snippet: to-state
object Ticket {

  /** The shop's rules, the same for every ticket. */
  val refundPolicy =
    "A duplicate charge is refunded at once. Any other refund needs the item back, unused, within 30 days."

  implicit val toState: ToState[Ticket] = t =>
    ujson.Obj(
      "message"       -> t.message,
      "customer"      -> ujson.Obj("name" -> t.customer.name, "plan" -> t.customer.plan),
      "order"         -> ujson.Obj("id" -> t.order.id, "item" -> t.order.item, "charges_usd" -> t.order.chargesUsd),
      "refund_policy" -> refundPolicy
    )
}
// end: to-state

// snippet: tickets
/** Three tickets, used in every chapter. */
object Tickets {
  val doubleCharge = Ticket(
    Customer("Ana", "pro"),
    Order("A-104", "Hiking boots", List(89.0, 89.0)),
    "You charged me twice for my boots! I want the second payment back before Friday."
  )

  val wrongSize = Ticket(
    Customer("Luca", "free"),
    Order("A-221", "Running shoes", List(120.0)),
    "The shoes are size 42 but I ordered 43. Can I change them? I prefer to write in Italian."
  )

  val cannotLogIn = Ticket(
    Customer("Mia", "business"),
    Order("B-007", "Team subscription", List(300.0)),
    "Nobody on our team can log in since this morning. This is the third time this month!"
  )
}
// end: tickets

// snippet: state
object State {
  // `order.charges_usd` names a field of the JSON that the ToState above makes.
  val duplicate = Noul("Does `order.charges_usd` contain the same amount twice?").as("duplicate")

  def main(args: Array[String]): Unit =
    client.ask(Tickets.doubleCharge, duplicate) match {
      case Right(d)    => println(s"Duplicate charge: ${d.isYes}")
      case Left(error) => println(s"Jev did not answer: $error")
    }
}
// end: state

object Json {
  // snippet: json
  val sameStateAsJson = ujson.Obj(
    "message"  -> "You charged me twice for my boots! I want the second payment back before Friday.",
    "customer" -> ujson.Obj("name" -> "Ana", "plan" -> "pro")
  )
  // end: json
}
