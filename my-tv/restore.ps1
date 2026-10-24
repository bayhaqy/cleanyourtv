# Put the TV back the way it was before this setup.
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File my-tv\restore.ps1 -TvHost <tv ip>
# Needs USB debugging + Network debugging switched on again on the TV (Developer options).
param([Parameter(Mandatory = $true)][string]$TvHost)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo
$env:TV_HOST = $TvHost
$py = 'tools\venv\Scripts\python.exe'

# 1. Re-enable every package the list disabled, and Home goes back to the stock launcher.
& tools\restore.ps1 -TvHost $TvHost my-tv\debloat.txt -Stock com.google.android.tvlauncher

# 2. Stock launcher as Home, Ambient screensaver, normal animation speed (the setup used 0.5).
& $py tools\tv.py 'pm enable --user 0 com.google.android.tvlauncher'
& $py tools\tv.py 'cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity'
& $py tools\tv.py 'settings put secure screensaver_components com.google.android.backdrop/.Backdrop'
& $py tools\tv.py 'settings put global window_animation_scale 1.0; settings put global transition_animation_scale 1.0; settings put global animator_duration_scale 1.0'

# 3. Optional: remove the launcher and the add-ons this setup installed.
#    & $py tools\tv.py 'pm uninstall com.gathin.tvhome; pm uninstall local.tvcare; pm uninstall io.github.maxhayim.tvhub'
& $py tools\tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1'
Write-Host 'Done. Apps uninstalled from the Play Store during the setup can be installed again from the Play Store.'
