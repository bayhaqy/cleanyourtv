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

# 2b. Tuning from the second pass: Private DNS off, sleep after 24 h again (was 10 min), background limits lifted.
& $py tools	v.py 'settings put global private_dns_mode opportunistic; settings delete global private_dns_specifier; settings put secure sleep_timeout 86400000'
& $py tools	v.py 'for p in com.vidio.android.tv com.catchplay.asiaplay.common.tv com.phlox.tvwebbrowser io.github.maxhayim.tvhub; do cmd appops set $p RUN_ANY_IN_BACKGROUND allow; done'
# 1080p supersampling override back to the panel's own 720p.
& $py tools	v.py 'wm size reset; wm density reset'
# The system language was switched to Indonesian in Settings > Device Preferences > Language; switch it back there.

# 3. Optional: remove the launcher and the add-ons this setup installed.
#    & $py tools\tv.py 'pm uninstall com.gathin.tvhome; pm uninstall io.github.bayhaqy.tvcare; pm uninstall io.github.maxhayim.tvhub; pm uninstall com.phlox.tvwebbrowser'
& $py tools\tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1'
Write-Host 'Done. Apps uninstalled from the Play Store during the setup can be installed again from the Play Store.'
