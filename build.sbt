// Two native modules with no shared code (ADR-0009). Both are called "jev4s", so that
// `%% "jev4s"` resolves to jev4s_3 or jev4s_2.13 from the user's own Scala version.

ThisBuild / organization := "io.github.maxtrezzi"
ThisBuild / licenses     := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / homepage     := Some(url("https://github.com/maxtrezzi/jev4s"))

val munit = "org.scalameta" %% "munit" % "1.3.6" % Test

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
    scalaVersion := "3.7.3", // ADR-0006
    scalacOptions ++= Seq("-deprecation", "-feature", "-Werror", "-Wunused:all", "-language:strictEquality"),
    libraryDependencies += munit,
    goldenFiles,
    fullCoverage,
  )

lazy val scala213 = project
  .settings(
    name         := "jev4s",
    scalaVersion := "2.13.18",
    scalacOptions ++= Seq("-deprecation", "-feature", "-Werror", "-Xlint"),
    libraryDependencies += munit,
    goldenFiles,
    fullCoverage,
  )

lazy val root = project
  .in(file("."))
  .aggregate(scala3, scala213)
  .settings(publish / skip := true)
