$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
$log = "C:\Users\TS-CONSULT\WebstormProjects\Hemodialyse\frontend\ng22-upgrade.log"
function Log($m){ "$(Get-Date -Format o) $m" | Tee-Object -FilePath $log -Append }

Set-Content -Path $log -Value "=== ng22 upgrade start ==="

$nodeDir = "$env:USERPROFILE\node-portable\node-v22.22.3-win-x64"
if(-not (Test-Path "$nodeDir\node.exe")){ Log "node-portable manquant a $nodeDir"; exit 1 }

# Prepend portable node to PATH for this session
$env:Path = "$nodeDir;$nodeDir\node_modules\npm\bin;" + $env:Path

Set-Location "C:\Users\TS-CONSULT\WebstormProjects\Hemodialyse\frontend"

Log ("node = " + (& "$nodeDir\node.exe" --version))
Log ("npm  = " + (& "$nodeDir\npm.cmd" --version))

Log "--- ng update @angular/core@22 @angular/cli@22 ---"
& "$nodeDir\npm.cmd" exec -- ng update "@angular/core@22" "@angular/cli@22" --allow-dirty --force 2>&1 | Tee-Object -FilePath $log -Append

Log "=== core/cli update done ==="

