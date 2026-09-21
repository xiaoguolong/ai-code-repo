# AI 仓库专用：临时切换 JDK 17 与 Maven，不改变系统/用户 JAVA_HOME。
# 用法示例：
#   .\run-maven-jdk17.ps1 -q test
#   .\run-maven-jdk17.ps1 spring-boot:run
#   .\run-maven-jdk17.ps1 -q -DskipTests package

$env:JAVA_HOME = "E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

$jdk17Home = "E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"

& mvn "-Djdk.17.home=$jdk17Home" @args
