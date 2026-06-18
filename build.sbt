import org.scalajs.linker.interface.ModuleSplitStyle

ThisBuild / scalaVersion := "3.8.4"
ThisBuild / organization := "org.scalalang"
ThisBuild / version      := "0.1.0-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wunused:all",
  "-Wvalue-discard",
  "-Werror",
)

lazy val V = new {
  val tapir      = "1.13.19"
  val sttp       = "4.0.23"
  val ox         = "1.0.4"
  val magnum     = "2.0.0-M3"
  val scribe     = "3.19.0"
  val laminar    = "17.2.1"
  val waypoint   = "9.0.0"
  val hikari     = "7.0.2"
  val sqlite     = "3.50.1.0"
  val upickle    = "4.4.3"
  val scalajsDom = "2.8.1"
  val munit      = "1.3.2"
}

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("shared"))
  .settings(
    name := "traits-shared",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %%% "tapir-core"         % V.tapir,
      "com.softwaremill.sttp.tapir" %%% "tapir-json-upickle" % V.tapir,
      "com.lihaoyi"                 %%% "upickle"            % V.upickle,
      "org.scalameta"               %%% "munit"              % V.munit % Test,
    ),
  )

lazy val backend = project
  .in(file("backend"))
  .dependsOn(shared.jvm)
  .settings(
    name                          := "traits-backend",
    fork                          := true,
    Compile / run / baseDirectory := (LocalRootProject / baseDirectory).value,
    Compile / mainClass           := Some("org.scalalang.traits.backend.Main"),
    Compile / run / envVars       := Map("TRAITS_ENV" -> "dev"),
    Compile / run / javaOptions += "--enable-native-access=ALL-UNNAMED",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync"  % V.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-files"              % V.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle"  % V.tapir,
      "com.softwaremill.ox"         %% "core"                    % V.ox,
      "com.augustnagro"             %% "magnum"                  % V.magnum,
      "com.zaxxer"                   % "HikariCP"                % V.hikari,
      "org.xerial"                   % "sqlite-jdbc"             % V.sqlite,
      "com.outr"                    %% "scribe"                  % V.scribe,
      "com.outr"                    %% "scribe-slf4j2"           % V.scribe,
      "org.scalameta"               %% "munit"                   % V.munit % Test,
    ),
  )

lazy val frontend = project
  .in(file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(shared.js)
  .settings(
    name                            := "traits-frontend",
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(
          ModuleSplitStyle.SmallModulesFor(List("org.scalalang.traits.frontend"))
        )
    },
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % V.scalajsDom,
      "com.raquo"    %%% "laminar"     % V.laminar,
      "com.raquo"    %%% "waypoint"    % V.waypoint,
      "com.softwaremill.sttp.tapir" %%% "tapir-sttp-client" % V.tapir,
      "org.scalameta"               %%% "munit"             % V.munit % Test,
    ),
  )

lazy val root = project
  .in(file("."))
  .aggregate(shared.jvm, shared.js, backend, frontend)
  .settings(
    name           := "traits",
    publish / skip := true,
  )
