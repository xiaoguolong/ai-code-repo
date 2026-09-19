# AI ????????? JDK 17 ? Maven????????????/?? JAVA_HOME?
$env:JAVA_HOME = "E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$jdk17Home = "E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
& mvn "-Djdk.17.home=$jdk17Home" @args
