import sbt.librarymanagement.CrossVersion

enablePlugins(JavaAppPackaging)
//enablePlugins(JDKPackagerPlugin)


val osName: SettingKey[String] = SettingKey[String]("osName")

osName := (System.getProperty("os.name") match {
	case name if name.startsWith("Linux")   => "linux"
	case name if name.startsWith("Mac")     => "mac"
	case name if name.startsWith("Windows") => "win"
	case _                                  => throw new Exception("Unknown platform!")
})

lazy val quickstyle = project.in(file(".")).settings(
	organization := "net.kurobako",
	name := "quickstyle",
	version := "0.1.0-SNAPSHOT",
	scalaVersion := "2.12.6",
	scalacOptions ++= Seq(
		"-target:jvm-1.8",
		"-encoding", "UTF-8",
		"-unchecked",
		"-deprecation",
		"-explaintypes",
		"-feature",
		"-Xfuture",

		"-language:existentials",
		"-language:experimental.macros",
		"-language:higherKinds",
		"-language:postfixOps",
		"-language:implicitConversions",

		"-Xlint:adapted-args",
		"-Xlint:by-name-right-associative",
		"-Xlint:constant",
		"-Xlint:delayedinit-select",
		"-Xlint:doc-detached",
		"-Xlint:inaccessible",
		"-Xlint:infer-any",
		"-Xlint:missing-interpolator",
		"-Xlint:nullary-override",
		"-Xlint:nullary-unit",
		"-Xlint:option-implicit",
		//		"-Xlint:package-object-classes", // TODO enable after project works
		"-Xlint:poly-implicit-overload",
		"-Xlint:private-shadow",
		"-Xlint:stars-align",
		"-Xlint:type-parameter-shadow",
		"-Xlint:unsound-match",

		"-Yno-adapted-args",
		"-Ywarn-dead-code",
		"-Ywarn-extra-implicit",
		"-Ywarn-inaccessible",
		"-Ywarn-infer-any",
		"-Ywarn-nullary-override",
		"-Ywarn-nullary-unit",
		"-Ywarn-numeric-widen",
		"-Ywarn-unused:implicits",
		//		"-Ywarn-unused:imports",
		"-Ywarn-unused:locals",
		"-Ywarn-unused:params",
		"-Ywarn-unused:patvars",
		"-Ywarn-unused:privates",
		"-Ywarn-value-discard",
		"-Ypartial-unification",

		// TODO enable to Scala 2.12.5
		"-Ybackend-parallelism", "4",
		//				"-Ycache-plugin-class-loader:last-modified",
		//				"-Ycache-macro-class-loader:last-modified",

		// XXX enable for macro debug
		//		"-Ymacro-debug-lite",
		//			"-Xlog-implicits",
		"-P:bm4:no-filtering:y",
		"-P:bm4:no-tupling:y",
		"-P:bm4:no-map-id:y",
	),
	javacOptions ++= Seq(
		"-target", "1.8",
		"-source", "1.8",
		"-Xlint:all"),
	addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),
	addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6"),
	addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4"),
	resolvers += Resolver.sonatypeRepo("releases"),
	mainClass in Compile := Some("net.kurobako.deltacc.Application"),
	jdkPackagerType := "installer",
	libraryDependencies ++= Seq(

		"com.google.guava" % "guava" % "26.0-jre",

		"com.github.pathikrit" %% "better-files" % "3.4.0",
		"net.kurobako" %% "better-monadic-files" % "0.1.0-SNAPSHOT",


		"com.chuusai" %% "shapeless" % "2.3.3",
		"org.typelevel" %% "cats-core" % "1.4.0",
		"org.typelevel" %% "cats-effect" % "1.0.0",
		"co.fs2" %% "fs2-core" % "1.0.0-M5",
		"co.fs2" %% "fs2-io" % "1.0.0-M5",
		"io.estatico" %% "newtype" % "0.4.2",

		"io.chrisdavenport" %% "log4cats-slf4j" % "0.1.1",

		"ch.qos.logback" % "logback-classic" % "1.2.3",

		"com.beachape" %% "enumeratum" % "1.5.12",

		"org.scalafx" %% "scalafx" % "8.0.144-R12",
		"org.controlsfx" % "controlsfx" % "9.0.0",

		"com.jakewharton.byteunits" % "byteunits" % "0.9.1",

		"org.openjfx" % "javafx-controls" % "11" classifier osName.value,
		"org.openjfx" % "javafx-fxml" % "11" classifier osName.value,
		"org.openjfx" % "javafx-graphics" % "11" classifier osName.value,
		"org.openjfx" % "javafx-base" % "11" classifier osName.value,
		"org.openjfx" % "javafx-web" % "11" classifier osName.value,
		"org.openjfx" % "javafx-media" % "11" classifier osName.value,

		"org.scalatest" %% "scalatest" % "3.0.1" % Test
	)
)
