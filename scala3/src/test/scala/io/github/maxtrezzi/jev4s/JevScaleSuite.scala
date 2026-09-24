package io.github.maxtrezzi.jev4s

object JevScaleSuite:
  enum Mood derives JevScale:
    case Calm, Annoyed, Angry

class JevScaleSuite extends munit.FunSuite:
  import JevScaleSuite.Mood

  enum Risk(val description: String) extends Described derives JevScale:
    case Low  extends Risk("Nothing to do")
    case High extends Risk("Act now")

  test("derived levels keep the order of the cases, with the name of each case as its text"):
    assertEquals(
      summon[JevScale[Mood]].levels,
      List(ScaleLevel(Mood.Calm, "Calm"), ScaleLevel(Mood.Annoyed, "Annoyed"), ScaleLevel(Mood.Angry, "Angry")),
    )

  test("a Described enum gives each level its description as its text"):
    assertEquals(
      summon[JevScale[Risk]].levels,
      List(ScaleLevel(Risk.Low, "Nothing to do"), ScaleLevel(Risk.High, "Act now")),
    )

  test("a Score over an enum takes its levels from the enum"):
    assertEquals(Score[Mood]("How?").levels, summon[JevScale[Mood]].levels)
