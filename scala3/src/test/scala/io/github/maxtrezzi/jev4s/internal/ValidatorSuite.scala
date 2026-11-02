package io.github.maxtrezzi.jev4s
package internal

class ValidatorSuite extends munit.FunSuite:

  private def levels(n: Int)  = Score("How?", List.tabulate(n)(i => s"level $i")*)
  private def options(n: Int) = Choice[String]("Which?")(using JevChoice.keys(List.tabulate(n)(i => s"k$i")*))

  test("a valid request has no problems"):
    assertEquals(Validator.validate(List("urgent" -> Noul("Urgent?"), "mood" -> levels(3), "dept" -> options(2))), Nil)

  test("a request with no questions"):
    assertEquals(Validator.validate(Nil), List(Problem.NoQuestions))

  test("a blank name is empty"):
    assertEquals(Validator.validate(List("  " -> Noul("Urgent?"))), List(Problem.EmptyName))

  test("a name used three times is reported once"):
    val q = Noul("Urgent?")
    assertEquals(Validator.validate(List("a" -> q, "a" -> q, "b" -> q, "a" -> q)), List(Problem.DuplicateName("a")))

  test("a Score needs 2 to 10 levels"):
    assertEquals(Validator.validate(List("s" -> levels(1))), List(Problem.ScoreLevels("s", 1)))
    assertEquals(Validator.validate(List("s" -> levels(2))), Nil)
    assertEquals(Validator.validate(List("s" -> levels(10))), Nil)
    assertEquals(Validator.validate(List("s" -> levels(11))), List(Problem.ScoreLevels("s", 11)))

  test("a Score may not repeat a level"):
    val score = Score("How?", "Low", "High", "Low", "High", "Low")
    assertEquals(
      Validator.validate(List("s" -> score)),
      List(Problem.DuplicateLevel("s", "Low"), Problem.DuplicateLevel("s", "High")),
    )
    val json = ujson.Obj("summary" -> "Low")
    assertEquals(
      Validator.validate(List("s" -> Score("How?", json, "High", json))),
      List(Problem.DuplicateLevel("s", """{"summary":"Low"}""")),
    )

  test("a Choice needs 1 to 255 options"):
    assertEquals(Validator.validate(List("c" -> options(0))), List(Problem.ChoiceOptions("c", 0)))
    assertEquals(Validator.validate(List("c" -> options(1))), Nil)
    assertEquals(Validator.validate(List("c" -> options(255))), Nil)
    assertEquals(Validator.validate(List("c" -> options(256))), List(Problem.ChoiceOptions("c", 256)))

  test("a Choice may not repeat an option key"):
    val choice = Question.Choice("Which?", List(ChoiceOption(1, "a"), ChoiceOption(2, "a"), ChoiceOption(3, "b")))
    assertEquals(Validator.validate(List("c" -> choice)), List(Problem.DuplicateOptionKey("c", "a")))

  test("a Score may not give one value to two levels"):
    val score = Question.Score("How?", List(ScaleLevel(1, "Low"), ScaleLevel(2, "High"), ScaleLevel(1, "Very low")))
    assertEquals(Validator.validate(List("s" -> score)), List(Problem.DuplicateLevelValue("s", "1")))

  test("a repeated level with the same value is one problem, not two"):
    val score = Question.Score("How?", List(ScaleLevel(1, "Low"), ScaleLevel(2, "High"), ScaleLevel(1, "Low")))
    assertEquals(Validator.validate(List("s" -> score)), List(Problem.DuplicateLevel("s", "Low")))

  test("a Choice may not give one value to two options"):
    val choice = Question.Choice("Which?", List(ChoiceOption(1, "a"), ChoiceOption(2, "b"), ChoiceOption(1, "c")))
    assertEquals(Validator.validate(List("c" -> choice)), List(Problem.DuplicateOptionValue("c", "1")))

  test("a repeated option key with the same value is one problem, not two"):
    val choice = Question.Choice("Which?", List(ChoiceOption(1, "a"), ChoiceOption(2, "b"), ChoiceOption(1, "a")))
    assertEquals(Validator.validate(List("c" -> choice)), List(Problem.DuplicateOptionKey("c", "a")))

  test("every problem is reported at once, in a stable order"):
    val problems = Validator.validate(List("x" -> levels(1), "x" -> Noul("q"), " " -> options(0)))
    assertEquals(
      problems,
      List(Problem.DuplicateName("x"), Problem.ScoreLevels("x", 1), Problem.EmptyName, Problem.ChoiceOptions(" ", 0)),
    )

  private def image(last: Int): Image =
    Image(Array(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, last).map(_.toByte)).toOption.get
  private val photo = image(1)
  private val other = image(2)
  private val box   = "dog" -> Box("The dog")

  test("a Box needs an image in the state"):
    assertEquals(
      Validator.validate(List(box, "cat" -> Box("The cat")), "a photo of a dog"),
      List(
        Problem.BoxWithoutImage("dog"),
        Problem.BoxWithoutImage("cat"),
      ),
    )
    assertEquals(Validator.validate(List(box), photo.ref, List(photo)), Nil)
    assertEquals(Validator.validate(List("q" -> Noul("Urgent?")), "text"), Nil)

  test("every image ref in the state is given, and every image given is in the state"):
    assertEquals(
      Validator.validate(List(box), photo.ref, List(other)),
      List(
        Problem.UnknownImage(photo.ref),
        Problem.UnusedImage(other.ref),
      ),
    )

  test("refs are found in every string of the JSON, inside longer text too, and each is reported once"):
    val state = ujson.Obj(
      "photos"  -> ujson.Arr(ujson.Obj("main" -> photo.ref), 3, ujson.Null),
      "caption" -> s"Compare ${photo.ref} with ${other.ref}.",
      "again"   -> other.ref,
    )
    assertEquals(Validator.validate(List(box), state, List(photo, other, photo)), Nil)
    assertEquals(Validator.validate(List(box), state, List(photo)), List(Problem.UnknownImage(other.ref)))
    assertEquals(
      Validator.validate(List(box), ujson.Obj(photo.ref -> "a key is not a place for an image"), List(photo)),
      List(Problem.UnusedImage(photo.ref)),
    )
