package io.github.maxtrezzi.jev4s

/** The typical mistakes are compile errors, and the ones jev4s controls say what to do. */
class CompileErrorSuite extends munit.FunSuite:

  // Referenced from the snippets below, which compile in this scope; not private, so the unused
  // check does not flag it.
  val client = JevClient.withTransport("m", FakeTransport(Left(JevError.Unauthorized)))

  /** The first line of the first message in what `compileErrors` returns. A message that spans
    * several lines starts on the line after `error:`.
    */
  private def message(errors: String): String =
    errors.linesIterator.map(_.stripPrefix("error:").trim).find(_.nonEmpty).getOrElse("")

  test("a value that is not a question"):
    assertEquals(
      message(compileErrors("""client.ask("s", (urgent = Noul("U?"), count = 3))""")),
      "every value in the named tuple must be a question: Noul, Score or Choice.",
    )

  test("a state type with no ToState"):
    assertEquals(
      message(compileErrors("""client.ask(42, (urgent = Noul("U?")))""")),
      "no ToState[Int]: give one, for example `given ToState[Int] = s => ujson.Obj(...)`, or pass a String or a ujson.Value.",
    )

  test("a name that was not asked"):
    assert(
      message(compileErrors("""client.ask("s", (urgent = Noul("U?"))).map(_.nope)""")).startsWith(
        "value nope is not a member of"
      )
    )

  test("an answer read as the wrong type"):
    val errors =
      compileErrors(
        """val a: Either[JevError, ScoreAnswer[ujson.Value]] = client.ask("s", (urgent = Noul("U?"))).map(_.urgent)"""
      )
    assert(errors.contains("Found:    io.github.maxtrezzi.jev4s.NoulAnswer"), errors)
    assert(errors.contains("Required: io.github.maxtrezzi.jev4s.ScoreAnswer[ujson.Value]"), errors)

  test("a tuple without names"):
    assertEquals(
      message(compileErrors("""client.ask("s", (Noul("U?"), Noul("V?")))""")),
      "the questions must be a named tuple, such as (urgent = Noul(\"Is it urgent?\")).",
    )

  test("a Choice over a type with no JevChoice"):
    assertEquals(
      message(compileErrors("""Choice[java.time.DayOfWeek]("Which day?")""")),
      "no options for Choice[java.time.DayOfWeek]: define a given JevChoice[java.time.DayOfWeek]",
    )

  test("JevChoice derived for an enum case with parameters"):
    assertEquals(
      message(compileErrors("enum Shape derives JevChoice:\n  case Point\n  case Circle(radius: Double)")),
      "JevChoice and JevScale can be derived only for an enum whose cases have no parameters; write the options or the levels of Shape by hand with JevChoice(...) or JevScale(...).",
    )

  test("JevScale derived for an enum case with parameters"):
    assertEquals(
      message(compileErrors("enum Shape derives JevScale:\n  case Point\n  case Circle(radius: Double)")),
      "JevChoice and JevScale can be derived only for an enum whose cases have no parameters; write the options or the levels of Shape by hand with JevChoice(...) or JevScale(...).",
    )

  test("a Score with no levels"):
    assertEquals(
      message(compileErrors("""Score("How?")""")),
      "a Score needs its levels: give them, as in Score(\"How?\", \"Calm\", \"Angry\"), or name an enum that derives JevScale, as in Score[Mood](\"How?\")",
    )

  test("a typed Score over a type with no JevScale"):
    assertEquals(
      message(compileErrors("""Score[java.time.DayOfWeek]("How?")""")),
      "a Score needs its levels: give them, as in Score(\"How?\", \"Calm\", \"Angry\"), or name an enum that derives JevScale, as in Score[Mood](\"How?\")",
    )

  test("JevScale derived for an enum of one case, or of eleven"):
    val expected = "a Score needs 2 to 10 levels: JevScale can be derived only for an enum of 2 to 10 cases."
    assertEquals(message(compileErrors("enum One derives JevScale:\n  case A")), expected)
    assertEquals(
      message(compileErrors("enum Eleven derives JevScale:\n  case A, B, C, D, E, F, G, H, I, J, K")),
      expected,
    )
    assertEquals(compileErrors("enum Two derives JevScale:\n  case A, B"), "")
    assertEquals(compileErrors("enum Ten derives JevScale:\n  case A, B, C, D, E, F, G, H, I, J"), "")

  test("a typed Score is not read with a text key"):
    assert(
      message(compileErrors("""(null: ScoreAnswer[JevScaleSuite.Mood]).probabilities("Calm")""")).startsWith(
        "Found:    (\"Calm\" : String)"
      )
    )
