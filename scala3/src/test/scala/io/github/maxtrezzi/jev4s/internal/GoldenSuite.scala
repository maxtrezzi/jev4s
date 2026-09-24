package io.github.maxtrezzi.jev4s
package internal

/** The codec against real requests and responses, recorded in `golden/`. Expected values are read
  * from the files, never repeated here: a new recording changes them.
  */
class GoldenSuite extends munit.FunSuite:

  private val model  = "jev-1.13.0"
  private val ticket = ujson.Str("Help! My payouts have been failing for 3 days and I have a launch tomorrow.")

  private val urgent     = "is_urgent"  -> Noul("Does this convey urgency?")
  private val department = "department" -> Question.Choice(
    "Which team should handle this?",
    List(
      ChoiceOption("billing", "billing", Some("Payments, invoicing, refunds")),
      ChoiceOption("technical", "technical", Some("Bugs, outages, integrations")),
      ChoiceOption("sales", "sales"),
    ),
  )
  private val frustration =
    "frustration" -> Score("How frustrated is the customer?", "Calm", "Frustrated", "Very angry")

  /** The levels of `frustration` as an enum, with the text of the recorded request. */
  enum Frustration(val description: String) extends Described derives JevScale:
    case Calm      extends Frustration("Calm")
    case Upset     extends Frustration("Frustrated")
    case VeryAngry extends Frustration("Very angry")

  private val mixedState = ujson.Obj("channel" -> "email", "customer_tier" -> "pro", "message" -> ticket)
  private val mixed      = List(
    "urgent"     -> Noul("Does `message` convey urgency?"),
    "department" -> Question.Choice(
      "Which team should handle `message`?",
      List(
        ChoiceOption("billing", "billing", Some("Payments, invoicing, refunds")),
        ChoiceOption("technical", "technical"),
      ),
    ),
    frustration,
  )

  // The questions of golden/structured, with each part taken from the recorded request: JSON in
  // every place that takes it, and one Score level left as text.
  private val recorded   = read("structured/request.json")
  private val structured =
    val q = recorded("questions")
    List(
      "requests_credentials" -> Noul(
        q("requests_credentials")("instructions"),
        Some(q("requests_credentials")("criteria")("true")),
        Some(q("requests_credentials")("criteria")("false")),
      ),
      "department" -> Question.Choice(
        q("department")("instructions"),
        q("department")("criteria").obj.toList.map((key, description) =>
          ChoiceOption(key, key, Option.when(!description.isNull)(description))
        ),
      ),
      "risk" -> Score(q("risk")("instructions"), q("risk")("criteria").arr.toSeq*),
    )

  private def read(name: String): ujson.Value = ujson.read(file(name))
  private def file(name: String): String      =
    val stream = getClass.getResourceAsStream(s"/$name")
    try String(stream.readAllBytes(), "UTF-8")
    finally stream.close()

  private def answerOf(recorded: String, name: String): ujson.Value = read(s"$recorded/response.json")("answers")(name)
  private def probability(json: ujson.Value): Probability           = Probability.from(json.num).get
  private def mostLikelyIndex(answer: ujson.Value): Int = answer("probabilities").obj.maxBy(_._2.num)._1.toInt
  private def decodeOk(recorded: String, questions: List[(String, Question[?])]) =
    Codec.decode(file(s"$recorded/response.json"), questions).fold(e => fail(s"$recorded: $e"), identity)

  test("encode produces the recorded requests"):
    val criteria =
      "is_urgent" -> Noul("Does this convey urgency?", Some("Explicitly time-sensitive"), Some("No urgency expressed"))
    assertEquals(Codec.encode(model, ticket, List(urgent)), read("noul/request.json"))
    assertEquals(Codec.encode(model, ticket, List(criteria)), read("noul-criteria/request.json"))
    assertEquals(Codec.encode(model, ticket, List(department)), read("choice/request.json"))
    assertEquals(Codec.encode(model, ticket, List(frustration)), read("score/request.json"))
    val typed = "frustration" -> Score[Frustration]("How frustrated is the customer?")
    assertEquals(Codec.encode(model, ticket, List(typed)), read("score/request.json"))
    assertEquals(Codec.encode(model, mixedState, mixed), read("mixed/request.json"))

  test("encode puts JSON instructions, criteria, options and levels where the API reads them"):
    assertEquals(Codec.encode(model, recorded("state"), structured), recorded)

  test("a Score with JSON levels keys each probability by its level, which the legend repeats"):
    val json   = answerOf("structured", "risk")
    val levels = recorded("questions")("risk")("criteria").arr.toList
    val answer = ScoreAnswer(
      json("score").num,
      levels(mostLikelyIndex(json)),
      probability(json("confidence")),
      json("probabilities").obj.map((k, v) => levels(k.toInt) -> probability(v)).toMap,
    )
    assertEquals(decodeOk("structured", structured).answers.last, answer)
    assertEquals(json("legend").obj.toList.sortBy(_._1.toInt).map(_._2), levels)
    assert(answer.probabilities.contains("Harmless"))

  test("a typed Score keys each probability by a case of the enum"):
    val json     = answerOf("score", "frustration")
    val typed    = "frustration" -> Score[Frustration]("How frustrated is the customer?")
    val levels   = Frustration.values.toList
    val expected = ScoreAnswer(
      json("score").num,
      levels(mostLikelyIndex(json)),
      probability(json("confidence")),
      json("probabilities").obj.map((k, v) => levels(k.toInt) -> probability(v)).toMap,
    )
    assertEquals(decodeOk("score", List(typed)).answers, List(expected))

  test("a Noul answer is the probability of yes"):
    val decoded = decodeOk("noul", List(urgent))
    assertEquals(decoded.answers, List(NoulAnswer(probability(answerOf("noul", "is_urgent")("noul")))))

  test("a Choice answer maps every key back to its option"):
    val json     = answerOf("choice", "department")
    val decoded  = decodeOk("choice", List(department))
    val expected = ChoiceAnswer(
      json("choice").str,
      probability(json("confidence")),
      json("probabilities").obj.map((k, v) => k -> probability(v)).toMap,
    )
    assertEquals(decoded.answers, List(expected))

  test("a Score answer keys each probability by the text of its level"):
    val json     = answerOf("score", "frustration")
    val decoded  = decodeOk("score", List(frustration))
    val levels   = List[ujson.Value]("Calm", "Frustrated", "Very angry")
    val expected = ScoreAnswer(
      json("score").num,
      levels(mostLikelyIndex(json)),
      probability(json("confidence")),
      json("probabilities").obj.map((k, v) => levels(k.toInt) -> probability(v)).toMap,
    )
    assertEquals(decoded.answers, List(expected))

  test("the reply carries the model that answered and the input tokens"):
    val json = read("noul/response.json")
    assertEquals(
      decodeOk("noul", List(urgent)).reply,
      Reply(json("model").str, json("usage")("input_tokens").num.toLong),
    )

  test("answers follow the order of the questions, not of the JSON"):
    val decoded = decodeOk("mixed", mixed.reverse)
    assertEquals(
      decoded.answers.map(_.getClass.getSimpleName),
      List("ScoreAnswer", "ChoiceAnswer", "NoulAnswer"),
    )

  test("a 400 body gives its message"):
    assertEquals(
      Codec.errorMessage(file("error-400/response.json")),
      read("error-400/response.json")("detail")("message").str,
    )

  test("a 401 body gives its message"):
    assertEquals(
      Codec.errorMessage(file("error-401/response.json")),
      read("error-401/response.json")("detail")("message").str,
    )

  test("a 422 body gives each field that failed, with its message"):
    val item = read("error-422/response.json")("detail")(0)
    assertEquals(
      Codec.errorMessage(file("error-422/response.json")),
      s"${item("loc").arr.map(_.str).mkString(".")}: ${item("msg").str}",
    )
