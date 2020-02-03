addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.7")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.2.5")

// sbt-scoverage 1.5.0 is the first that supports scala 2.12.
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")

addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "1.3.2")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.0.0")