# StockBuddy installer for Windows 10 / 11.
# Copies StockBuddy to your user folder (no admin rights needed), makes sure Java is available,
# and creates Desktop and Start-menu shortcuts.
$ErrorActionPreference = 'Stop'
$src  = Split-Path -Parent $MyInvocation.MyCommand.Path
$dest = Join-Path $env:LOCALAPPDATA 'StockBuddy'

function Say($text, $color = 'White') { Write-Host "  $text" -ForegroundColor $color }

function Get-JavaMajor([string]$javaw) {
    $java = Join-Path (Split-Path $javaw) 'java.exe'
    if (-not (Test-Path $java)) { return 0 }
    $out = cmd /c "`"$java`" -version 2>&1" | Out-String
    if ($out -match 'version "(\d+)(\.(\d+))?') {
        $major = [int]$Matches[1]
        if ($major -eq 1 -and $Matches[3]) { $major = [int]$Matches[3] }
        return $major
    }
    return 0
}

function Find-Javaw {
    $candidates = @()
    $cmd = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($cmd) { $candidates += $cmd.Source }
    $roots = @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft",
               "$env:ProgramFiles\Zulu", "$env:ProgramFiles\Amazon Corretto", "$env:LOCALAPPDATA\Programs\Eclipse Adoptium")
    foreach ($r in $roots) {
        if (Test-Path $r) {
            $candidates += Get-ChildItem -Path $r -Recurse -Filter javaw.exe -ErrorAction SilentlyContinue |
                           Sort-Object FullName -Descending | ForEach-Object { $_.FullName }
        }
    }
    foreach ($c in $candidates) { if ((Get-JavaMajor $c) -ge 17) { return $c } }
    return $null
}

Write-Host ''
Say 'StockBuddy installer' 'Cyan'
Say '--------------------' 'Cyan'

# ---------------------------------------------------------------- 1. Java
$javaw = Find-Javaw
if (-not $javaw) {
    Say 'StockBuddy needs Java (version 17 or newer), which is free.' 'Yellow'
    $winget = Get-Command winget.exe -ErrorAction SilentlyContinue
    if ($winget) {
        $answer = Read-Host '  Install Java automatically now? (Y/N)'
        if ($answer -match '^[Yy]') {
            Say 'Installing Eclipse Temurin Java 21 (this can take a few minutes)...'
            & winget install --id EclipseAdoptium.Temurin.21.JRE -e --accept-package-agreements --accept-source-agreements
            $javaw = Find-Javaw
        }
    }
    if (-not $javaw) {
        Say 'Please install Java from the page that is opening now:' 'Yellow'
        Say '  choose "JRE", "Windows", "x64", download the .msi file and run it.' 'Yellow'
        Say 'Then double-click "Install StockBuddy" again.' 'Yellow'
        Start-Process 'https://adoptium.net/temurin/releases/?os=windows&package=jre&version=21'
        exit 1
    }
}
Say "Java found: $javaw" 'Green'

# ---------------------------------------------------------------- 2. Files
Say "Copying StockBuddy to $dest ..."
New-Item -ItemType Directory -Force -Path $dest, "$dest\data" | Out-Null
Copy-Item "$src\StockBuddy.jar", "$src\stockbuddy.ico", "$src\StockBuddy.vbs", "$src\uninstall.ps1" -Destination $dest -Force
Get-ChildItem "$src\data" | ForEach-Object {
    $target = Join-Path "$dest\data" $_.Name
    if (-not (Test-Path $target)) { Copy-Item $_.FullName $target }   # never overwrite your own watchlist/settings
}
Set-Content -Path "$dest\java-path.txt" -Value $javaw -Encoding ASCII
Get-ChildItem $dest -Recurse | Unblock-File -ErrorAction SilentlyContinue

# ---------------------------------------------------------------- 3. Shortcuts
$shell = New-Object -ComObject WScript.Shell
function New-Shortcut([string]$path, [string]$arguments, [string]$description) {
    $s = $shell.CreateShortcut($path)
    $s.TargetPath = Join-Path $env:WINDIR 'System32\wscript.exe'
    $s.Arguments = "`"$dest\StockBuddy.vbs`" $arguments".Trim()
    $s.WorkingDirectory = $dest
    $s.IconLocation = "$dest\stockbuddy.ico,0"
    $s.Description = $description
    $s.Save()
}
$desktop = [Environment]::GetFolderPath('Desktop')
$menu = Join-Path ([Environment]::GetFolderPath('Programs')) 'StockBuddy'
New-Item -ItemType Directory -Force -Path $menu | Out-Null
New-Shortcut "$desktop\StockBuddy.lnk" '' 'Your friendly daily stock helper'
New-Shortcut "$desktop\StockBuddy Widget.lnk" '--widget' 'StockBuddy desktop widget'
New-Shortcut "$menu\StockBuddy.lnk" '' 'Your friendly daily stock helper'
New-Shortcut "$menu\StockBuddy Widget.lnk" '--widget' 'StockBuddy desktop widget'
New-Shortcut "$menu\StockBuddy (practice mode).lnk" '--demo' 'StockBuddy with made-up practice data'
$u = $shell.CreateShortcut("$menu\Uninstall StockBuddy.lnk")
$u.TargetPath = Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe'
$u.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$dest\uninstall.ps1`""
$u.IconLocation = "$dest\stockbuddy.ico,0"
$u.Save()

Write-Host ''
Say 'Done! StockBuddy is installed.' 'Green'
Say 'Open it with the "StockBuddy" icon on your Desktop (or from the Start menu).'
Say 'The "StockBuddy Widget" icon opens just the small desktop widget.'
$open = Read-Host '  Open StockBuddy now? (Y/N)'
if ($open -match '^[Yy]') { Start-Process (Join-Path $env:WINDIR 'System32\wscript.exe') -ArgumentList "`"$dest\StockBuddy.vbs`"" -WorkingDirectory $dest }
