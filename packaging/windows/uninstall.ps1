# Removes StockBuddy, its shortcuts and its "start with Windows" entry.
$dest = Join-Path $env:LOCALAPPDATA 'StockBuddy'
Set-Location $env:TEMP
$answer = Read-Host 'Remove StockBuddy and all its settings, watchlist and reports? (Y/N)'
if ($answer -notmatch '^[Yy]') { exit }
$desktop = [Environment]::GetFolderPath('Desktop')
$menu = Join-Path ([Environment]::GetFolderPath('Programs')) 'StockBuddy'
Remove-Item "$desktop\StockBuddy.lnk", "$desktop\StockBuddy Widget.lnk" -ErrorAction SilentlyContinue
Remove-Item $menu -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path ([Environment]::GetFolderPath('Startup')) 'StockBuddyWidget.vbs') -ErrorAction SilentlyContinue
Remove-Item $dest -Recurse -Force -ErrorAction SilentlyContinue
Write-Host 'StockBuddy has been removed.' -ForegroundColor Green
Start-Sleep -Seconds 3
