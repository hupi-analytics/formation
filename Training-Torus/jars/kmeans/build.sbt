import AssemblyKeys._

name := "kmeans"

version := "1.0"

scalaVersion := "2.11.4"

resolvers += "SnowPlow Repo" at "http://maven.snplow.com/releases/"
resolvers += "Job Server Bintray" at "https://dl.bintray.com/spark-jobserver/maven"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "2.0.0" % "provided",
  "org.apache.spark" %% "spark-mllib" % "2.0.1" % "provided",
  "org.apache.spark" %% "spark-sql" % "2.0.0" % "provided",
  "org.scala-lang" % "scala-reflect" %  "2.11.4",
  "com.typesafe.akka" %% "akka-actor" % "2.3.5",
  "org.joda" % "joda-convert" % "1.9",
  "joda-time" % "joda-time" % "2.9.1",
  "com.typesafe" % "config" % "1.3.1",
  "org.apache.hadoop" % "hadoop-client" % "2.7.1",
  "org.locationtech.geotrellis" % "geotrellis-spark_2.11" % "1.2.0"
)

retrieveManaged := true

assemblySettings

mergeStrategy in assembly := {
  case m if m.toLowerCase.endsWith("manifest.mf")          => MergeStrategy.discard
  case m if m.toLowerCase.matches("meta-inf.*\\.sf$")      => MergeStrategy.discard
  case "log4j.properties"                                  => MergeStrategy.discard
  case m if m.toLowerCase.startsWith("meta-inf/services/") => MergeStrategy.filterDistinctLines
  case "reference.conf"                                    => MergeStrategy.concat
  case _                                                   => MergeStrategy.first
}
