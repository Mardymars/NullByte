$ErrorActionPreference = "Stop"

$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path
$toolRoot = Join-Path $workspace "Tools"
$jdkRoot = Join-Path $toolRoot "jdk-17-extract"
$gradleDir = Join-Path $toolRoot "gradle-9.3.1"
$gradleHome = Join-Path $workspace ".gradle-home"
$tmpDir = Join-Path $workspace ".tmp"
$androidRoot = Join-Path $toolRoot "android-sdk"
$javaHome = (Get-ChildItem $jdkRoot -Directory | Select-Object -First 1).FullName

if (-not $javaHome) {
    throw "No workspace JDK found under Tools\\jdk-17-extract."
}

if (-not (Test-Path (Join-Path $gradleDir "bin\\gradle.bat"))) {
    throw "No workspace Gradle install found under Tools\\gradle-9.3.1."
}

New-Item -ItemType Directory -Force -Path $gradleHome, $tmpDir | Out-Null

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $androidRoot
$env:ANDROID_SDK_ROOT = $androidRoot
$env:GRADLE_USER_HOME = $gradleHome
$env:HOME = $gradleHome
$env:TEMP = $tmpDir
$env:TMP = $tmpDir
$env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$tmpDir -Duser.home=$gradleHome"
$env:PATH = "$javaHome\\bin;$gradleDir\\bin;$androidRoot\\platform-tools;$env:PATH"

& (Join-Path $gradleDir "bin\\gradle.bat") assembleDebug --no-daemon
