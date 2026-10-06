# AI 仓库专用：临时切换 JDK 17 与 Maven，不改变系统/用户 JAVA_HOME。
# 用法示例：
#   .\run-maven-jdk17.ps1 -q test
#   .\run-maven-jdk17.ps1 spring-boot:run
#   .\run-maven-jdk17.ps1 -q -DskipTests package
#
# Week 19：本脚本额外支持「可选挂载 SkyWalking Java Agent」。
#   $env:SKYWALKING_AGENT_PATH = "E:\skywalking-agent\skywalking-agent.jar"
#   $env:SW_AGENT_NAME          = "ai-code-gateway"
#   $env:SW_AGENT_COLLECTOR_BACKEND_SERVICES = "192.168.132.128:11800"
# 未设置 SKYWALKING_AGENT_PATH 时行为与其它模块完全一致（不挂 Agent，零差异）。
# 注意：Agent 只对「本次 Maven 启动的 JVM」（spring-boot:run 的 fork 进程 / surefire JVM）生效；
# 单测不建议挂 Agent（会拖慢且引入网络上报），需要时再显式设置。

$env:JAVA_HOME = "E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

$jdk17Home = "E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"

if ($env:SKYWALKING_AGENT_PATH -and (Test-Path $env:SKYWALKING_AGENT_PATH)) {
    Write-Host "[week19] SkyWalking Agent: $env:SKYWALKING_AGENT_PATH"
    if ($env:SW_AGENT_NAME) { Write-Host "[week19] service_name   : $env:SW_AGENT_NAME" }
    if ($env:SW_AGENT_COLLECTOR_BACKEND_SERVICES) { Write-Host "[week19] backend        : $env:SW_AGENT_COLLECTOR_BACKEND_SERVICES" }
    & mvn "-Djdk.17.home=$jdk17Home" "-Dspring-boot.run.jvmArguments=-javaagent:$env:SKYWALKING_AGENT_PATH" @args
} else {
    & mvn "-Djdk.17.home=$jdk17Home" @args
}
