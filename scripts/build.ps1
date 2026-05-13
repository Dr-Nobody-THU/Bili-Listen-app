$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$mainSrc = Join-Path $root "src\main\java"
$mainOut = Join-Path $root "build\classes\main"

if (-not (Test-Path $mainSrc)) {
    throw "Main source directory not found: $mainSrc"
}

if (Test-Path $mainOut) {
    Remove-Item -LiteralPath $mainOut -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $mainOut | Out-Null

$sources = Get-ChildItem -Path $mainSrc -Recurse -Filter *.java | ForEach-Object { $_.FullName }
if (-not $sources) {
    throw "No Java sources found under $mainSrc"
}

& javac --release 17 -encoding UTF-8 -d $mainOut $sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

Write-Host "Compiled main sources to $mainOut"
