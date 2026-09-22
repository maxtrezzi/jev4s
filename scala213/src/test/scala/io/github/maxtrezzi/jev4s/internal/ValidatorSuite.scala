package io.github.maxtrezzi.jev4s
package internal

class ValidatorSuite extends munit.FunSuite {

  private def levels(n: Int)  = Score("How?", List.tabulate(n)(i => s"level $i"))
  private def options(n: Int) = Choice.of[String]("Which?")(JevChoice.keys(List.tabulate(n)(i => s"k$i"): _*))

  test("a valid request has no problems") {
    assertEquals(Validator.validate(List("urgent" -> Noul("Urgent?"), "mood" -> levels(3), "dept" -> options(2))), Nil)
  }

  test("a request with no questions") {
    assertEquals(Validator.validate(Nil), List(Problem.NoQuestions))
  }

  test("a blank name is empty") {
    assertEquals(Validator.validate(List("  " -> Noul("Urgent?"))), List(Problem.EmptyName))
  }

  test("a name used three times is reported once") {
    val q = Noul("Urgent?")
    assertEquals(Validator.validate(List("a" -> q, "a" -> q, "b" -> q, "a" -> q)), List(Problem.DuplicateName("a")))
  }

  test("a Score needs 2 to 10 levels") {
    assertEquals(Validator.validate(List("s" -> levels(1))), List(Problem.ScoreLevels("s", 1)))
    assertEquals(Validator.validate(List("s" -> levels(2))), Nil)
    assertEquals(Validator.validate(List("s" -> levels(10))), Nil)
    assertEquals(Validator.validate(List("s" -> levels(11))), List(Problem.ScoreLevels("s", 11)))
  }

  test("a Score may not repeat a level") {
    val score = Score("How?", List("Low", "High", "Low", "High", "Low"))
    assertEquals(
      Validator.validate(List("s" -> score)),
      List(Problem.DuplicateLevel("s", "Low"), Problem.DuplicateLevel("s", "High"))
    )
    val json = ujson.Obj("summary" -> "Low")
    assertEquals(
      Validator.validate(List("s" -> Score("How?", List(json, "High", json)))),
      List(Problem.DuplicateLevel("s", """{"summary":"Low"}"""))
    )
  }

  test("a Choice needs 1 to 255 options") {
    assertEquals(Validator.validate(List("c" -> options(0))), List(Problem.ChoiceOptions("c", 0)))
    assertEquals(Validator.validate(List("c" -> options(1))), Nil)
    assertEquals(Validator.validate(List("c" -> options(255))), Nil)
    assertEquals(Validator.validate(List("c" -> options(256))), List(Problem.ChoiceOptions("c", 256)))
  }

  test("a Choice may not repeat an option key") {
    val choice = Choice("Which?", List(ChoiceOption(1, "a"), ChoiceOption(2, "a"), ChoiceOption(3, "b")))
    assertEquals(Validator.validate(List("c" -> choice)), List(Problem.DuplicateOptionKey("c", "a")))
  }

  test("every problem is reported at once, in a stable order") {
    val problems = Validator.validate(List("x" -> levels(1), "x" -> Noul("q"), " " -> options(0)))
    assertEquals(
      problems,
      List(Problem.DuplicateName("x"), Problem.ScoreLevels("x", 1), Problem.EmptyName, Problem.ChoiceOptions(" ", 0))
    )
  }
}
