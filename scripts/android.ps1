param(
    [string[]] $Tasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug')
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$javaCandidates = @(@(
    'E:\Program Files\Android\Android Studio\jbr',
    'C:\Program Files\Android\Android Studio\jbr',
    $env:JAVA_HOME
) | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe')) })
if (-not $javaCandidates) { throw 'Install Android Studio (JDK 21) or set JAVA_HOME first.' }
$env:JAVA_HOME = $javaCandidates[0]
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user' }
$toolchains = @($env:JAVA_HOME, 'E:\Program Files\Java\jdk-17.0.2') |
    Where-Object { Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe') }

# The Windows Java launcher reads @argument files using the native code page.
# Gradle must write the same encoding when the project path contains Chinese.
$nativeEncoding = [System.Text.Encoding]::Default.WebName
if ($nativeEncoding -eq 'gb2312') { $nativeEncoding = 'GBK' }
$gradleArgs = @('--console=plain', "-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=$nativeEncoding",
    "-Porg.gradle.java.installations.paths=$($toolchains -join ',')")
Push-Location $projectRoot
try {
    & .\gradlew.bat @Tasks @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}
