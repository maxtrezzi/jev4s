// Two native modules with no shared code (ADR-0009). Both are called "jev4s", so that
// `%% "jev4s"` resolves to jev4s_3 or jev4s_2.13 from the user's own Scala version.

ThisBuild / organization := "io.github.maxtrezzi"
ThisBuild / licenses     := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / homepage     := Some(url("https://github.com/maxtrezzi/jev4s"))

val munit = "org.scalameta" %% "munit" % "1.3.6" % Test

// ADR-0005: the one runtime dependency.
val ujson = "com.lihaoyi" %% "ujson" % "4.4.3"

// Real Jev JSON, read by the tests of both modules: the one thing the modules share.
val goldenFiles = Test / unmanagedResourceDirectories += (LocalRootProject / baseDirectory).value / "golden"

// ADR-0008: below 100% statement or branch coverage the build fails.
val fullCoverage = Seq(
  coverageMinimumStmtTotal   := 100,
  coverageMinimumBranchTotal := 100,
  coverageFailOnMinimum      := true,
)

lazy val scala3 = project
  .settings(
    name         := "jev4s",
    scalaVersion := "3.9.0", // ADR-0017
    scalacOptions ++= Seq("-deprecation", "-feature", "-Werror", "-Wunused:all", "-language:strictEquality"),
    libraryDependencies ++= Seq(ujson, munit),
    goldenFiles,
    fullCoverage,
  )

lazy val scala213 = project
  .settings(
    name         := "jev4s",
    scalaVersion := "2.13.16", // ADR-0036
    scalacOptions ++= Seq("-deprecation", "-feature", "-Werror", "-Xlint"),
    libraryDependencies ++= Seq(ujson, munit),
    goldenFiles,
    fullCoverage,
  )

// Tests and examples against the real API (M5). They cost money, so they are separate projects
// that the root does not aggregate: `sbt test`, coverage and Stryker4s never run them. Each test
// is skipped unless TYPESAFE_API_KEY is set. Run with `sbt scala3Live/test`, `sbt scala3Live/run`.
// The projects also hold the examples that the README and the guides quote, each with its own
// main: `run` starts the example of M5, and `runMain guide.firstQuestion` starts one of the others.
lazy val scala3Live = project
  .in(file("live/scala3"))
  .dependsOn(scala3)
  .settings(
    scalaVersion := (scala3 / scalaVersion).value,
    scalacOptions ++= (scala3 / scalacOptions).value,
    libraryDependencies += munit,
    publish / skip            := true,
    Compile / run / mainClass := Some("example"),
  )

lazy val scala213Live = project
  .in(file("live/scala213"))
  .dependsOn(scala213)
  .settings(
    scalaVersion := (scala213 / scalaVersion).value,
    scalacOptions ++= (scala213 / scalacOptions).value,
    libraryDependencies += munit,
    publish / skip            := true,
    Compile / run / mainClass := Some("Example"),
  )

lazy val root = project
  .in(file("."))
  .aggregate(scala3, scala213)
  .settings(publish / skip := true)
