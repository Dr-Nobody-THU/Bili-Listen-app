$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$mainSrc = Join-Path $root "src\main\java"
$testSrc = Join-Path $root "src\test\java"
$mainOut = Join-Path $root "build\classes\main"
$testOut = Join-Path $root "build\classes\test"

if (Test-Path $mainOut) {
    Remove-Item -LiteralPath $mainOut -Recurse -Force
}

if (Test-Path $testOut) {
    Remove-Item -LiteralPath $testOut -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $mainOut | Out-Null
New-Item -ItemType Directory -Force -Path $testOut | Out-Null

$mainSources = Get-ChildItem -Path $mainSrc -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$testSources = Get-ChildItem -Path $testSrc -Recurse -Filter *.java | ForEach-Object { $_.FullName }

if (-not $mainSources) {
    throw "No main Java sources found under $mainSrc"
}

if (-not $testSources) {
    throw "No test Java sources found under $testSrc"
}

& javac --release 17 -encoding UTF-8 -d $mainOut $mainSources
if ($LASTEXITCODE -ne 0) {
    throw "Main javac failed with exit code $LASTEXITCODE"
}

& javac --release 17 -encoding UTF-8 -cp $mainOut -d $testOut $testSources
if ($LASTEXITCODE -ne 0) {
    throw "Test javac failed with exit code $LASTEXITCODE"
}

& java -cp "$mainOut;$testOut" com.bililisten.prototype.SelfCheck
if ($LASTEXITCODE -ne 0) {
    throw "Self-check failed with exit code $LASTEXITCODE"
}
