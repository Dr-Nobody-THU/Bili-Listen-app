$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$mainOut = Join-Path $root "build\classes\main"

& "$PSScriptRoot\build.ps1"
& java -cp $mainOut com.bililisten.prototype.BiliListenCli demo
