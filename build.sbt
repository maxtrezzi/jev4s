// Two native modules with no shared code (ADR-0009). Both are called "jev4s", so that
// `%% "jev4s"` resolves to jev4s_3 or jev4s_2.13 from the user's own Scala version.

ThisBuild / organization := "io.github.maxtrezzi"
ThisBuild / version      := "0.1.0"
ThisBuild / description  := "An unofficial Scala client for Jev, the typed-decision model of TypeSafe AI."
ThisBuild / licenses     := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / homepage     := Some(url("https://github.com/maxtrezzi/jev4s"))
ThisBuild / scmInfo      := Some(
  ScmInfo(url("https://github.com/maxtrezzi/jev4s"), "scm:git:https://github.com/maxtrezzi/jev4s.git")
)
ThisBuild / developers := List(
  Developer("maxtrezzi", "maxtrezzi", "", url("https://github.com/maxtrezzi"))
)
// 0.x while the Jev API is in early access: a change of the minor version may break the API.
ThisBuild / versionScheme := Some("early-semver")

// Maven Central through the Central Portal, with sbt's own publishing: `publishSigned` stages
// the signed artifacts in localStaging, and `sonaRelease` uploads and releases them.
ThisBuild / publishTo := localStaging.value

val munit = "org.scalameta" %% "munit" % "1.3.6" % Test

// ADR-0005: the one runtime dependency.
val ujson = "com.lihaoyi" %% "ujson" % "4.4.3"

// Real Jev JSON, read by the tests of both modules: the one thing the modules share.
val goldenFiles = Test / unmanagedResourceDirectories += (LocalRootProject / baseDirectory).value / "golden"

// ADR-0044: the documents whose compile errors the Scala 3 tests check, on the test classpath as
// `documents/README.md` and `documents/scala3.md`: golden/ has a README.md of its own.
val documentedErrors = Test / resourceGenerators += Def.task {
  val root = (LocalRootProject / baseDirectory).value
  Seq("README.md", "docs/guide/scala3.md").map { path =>
    val copy = (Test / resourceManaged).value / "documents" / file(path).getName
    IO.copyFile(root / path, copy)
    copy
  }
}.taskValue

// ADR-0008: below 100% statement or branch coverage the build fails.
val fullCoverage = Seq(
  coverageMinimumStmtTotal   := 100,
  coverageMinimumBranchTotal := 100,
  coverageFailOnMinimum      := true,
)

// The licence and its notice inside each published jar and sources jar, so that a jar copied on its
// own still carries them: Apache 2.0 asks whoever redistributes it to pass both on.
val licenseFiles = Seq(Compile / packageBin, Compile / packageSrc).map { jar =>
  jar / mappings ++= {
    val root = (LocalRootProject / baseDirectory).value
    Seq(root / "LICENSE" -> "META-INF/LICENSE", root / "NOTICE" -> "META-INF/NOTICE")
  }
}

// ADR-0052: the releases whose API this version keeps. Under early-semver a patch version keeps
// the API of every earlier patch of its minor version, so 0.1.2 is checked against 0.1.0 and
// 0.1.1; a new minor version in 0.x may break it, so 0.2.0 is checked against nothing.
def compatibleReleases(version: String): Set[String] =
  version.takeWhile(_ != '-').split('.') match {
    case Array(major, minor, patch) => (0 until patch.toInt).map(p => s"$major.$minor.$p").toSet
    case _                          => sys.error(s"the version must be major.minor.patch: $version")
  }

// MiMa compares the bytecode with those releases, in every published project. A version x.y.0
// has none, and that is not an error.
ThisBuild / mimaFailOnNoPrevious := false
val binaryCompatibility =
  mimaPreviousArtifacts := compatibleReleases(version.value).map(organization.value %% moduleName.value % _)

// TASTy-MiMa compares the Scala 3 types, which the bytecode erases: a match type such as
// AnswerOf, the evidence of a derivation, a parameter that becomes varargs. Its 1.4.0 release
// reads TASTy up to Scala 3.7; the core 1.4.1 with tasty-query 1.9.0 reads Scala 3.9.
// Only jev4s_3: tasty-query fails with an AssertionError on the test kit's FakeAnswer, a match
// type on a match type bounded by a union, even when nothing changed (docs/tasks, M7).
val tastyCompatibility = Seq(
  tastyMiMaPreviousArtifacts := compatibleReleases(version.value).map(organization.value %% moduleName.value % _),
  tastyMiMaVersionOverride   := Some("1.4.1"),
  tastyMiMaTastyQueryVersionOverride := Some("1.9.0"),
  // It reads the JDK's java.base only; JdkTransport and JevClient use java.net.http, a module of
  // its own.
  tastyMiMaJavaBootClasspath := {
    val base = tastyMiMaJavaBootClasspath.value
    base ++ base.map(_.resolveSibling("java.net.http"))
  },
)

lazy val scala3 = project
  .settings(
    name         := "jev4s",
    scalaVersion := "3.9.0", // ADR-0017
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-Werror",
      "-Wunused:all",
      "-language:strictEquality",
      "-java-output-version:17", // JDK 17's API and bytecode, whatever JDK builds the release
    ),
    libraryDependencies ++= Seq(ujson, munit),
    goldenFiles,
    documentedErrors,
    fullCoverage,
    licenseFiles,
    binaryCompatibility,
    tastyCompatibility,
  )

lazy val scala213 = project
  .settings(
    name         := "jev4s",
    scalaVersion := "2.13.16", // ADR-0036
    scalacOptions ++= Seq("-deprecation", "-feature", "-Werror", "-Xlint", "-release:17"),
    libraryDependencies ++= Seq(ujson, munit),
    goldenFiles,
    fullCoverage,
    licenseFiles,
    binaryCompatibility,
  )

// ADR-0048: a test kit for each module, published as jev4s-testkit next to jev4s. Each one
// depends on its module and nothing else, and is held to the same coverage and mutants.
lazy val scala3Testkit = project
  .in(file("testkit/scala3"))
  .dependsOn(scala3)
  .settings(
    name         := "jev4s-testkit",
    scalaVersion := (scala3 / scalaVersion).value,
    scalacOptions ++= (scala3 / scalacOptions).value,
    libraryDependencies += munit,
    goldenFiles,
    fullCoverage,
    licenseFiles,
    binaryCompatibility,
  )

lazy val scala213Testkit = project
  .in(file("testkit/scala213"))
  .dependsOn(scala213)
  .settings(
    name         := "jev4s-testkit",
    scalaVersion := (scala213 / scalaVersion).value,
    scalacOptions ++= (scala213 / scalacOptions).value,
    libraryDependencies += munit,
    goldenFiles,
    fullCoverage,
    licenseFiles,
    binaryCompatibility,
  )

// Tests and examples against the real API (M5). They cost money, so they are separate projects
// that the root does not aggregate: `sbt test`, coverage and Stryker4s never run them. Each test
// of LiveSuite is skipped unless TYPESAFE_API_KEY is set. Run with `sbt scala3Live/test`,
// `sbt scala3Live/run`.
// The projects also hold the examples that the README and the guides quote, each with its own
// main: `run` starts the example of M5, and `runMain guide.firstQuestion` starts one of the others.
// The tests of the guides (package guide) use a fake transport or a local server, cost nothing,
// and run in CI: `sbt "scala3Live/testOnly guide.*"`.
lazy val scala3Live = project
  .in(file("live/scala3"))
  .dependsOn(scala3, scala3Testkit % Test) // chapter 12 of the tutorial tests with the test kit
  .settings(
    scalaVersion := (scala3 / scalaVersion).value,
    scalacOptions ++= (scala3 / scalacOptions).value,
    libraryDependencies += munit,
    publish / skip            := true,
    Compile / run / mainClass := Some("example"),
  )

lazy val scala213Live = project
  .in(file("live/scala213"))
  .dependsOn(scala213, scala213Testkit % Test) // chapter 12 of the tutorial tests with the test kit
  .settings(
    scalaVersion := (scala213 / scalaVersion).value,
    scalacOptions ++= (scala213 / scalacOptions).value,
    libraryDependencies += munit,
    publish / skip            := true,
    Compile / run / mainClass := Some("Example"),
  )

lazy val root = project
  .in(file("."))
  .aggregate(scala3, scala213, scala3Testkit, scala213Testkit)
  .settings(publish / skip := true)

// M8: an Apache Spark example of the 2.13 module, in a project of its own so that Spark's
// dependencies stay out of scala213Live. Spark is Provided: a cluster brings its own.
lazy val scala213Spark = project
  .in(file("live/spark"))
  .dependsOn(scala213Live, scala213Testkit % Test) // the Pacer of the guide, and so the scala213 module
  .settings(
    scalaVersion := (scala213 / scalaVersion).value,
    scalacOptions ++= (scala213 / scalacOptions).value,
    libraryDependencies ++= Seq("org.apache.spark" %% "spark-sql" % "4.0.4" % Provided, munit),
    publish / skip := true,
    // `run` and the tests start Spark with the Provided classes, in a JVM of their own.
    Compile / run := Defaults
      .runTask(Compile / fullClasspath, Compile / run / mainClass, Compile / run / runner)
      .evaluated,
    Compile / runMain := Defaults.runMainTask(Compile / fullClasspath, Compile / run / runner).evaluated,
    fork              := true,
  )
