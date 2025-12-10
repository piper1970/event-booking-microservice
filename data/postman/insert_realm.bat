@echo off

REM %* contains all command-line arguments
set "filename=%*"

REM Use PowerShell to perform the sed-like replacement
powershell -Command "(Get-Content '%filename%.template') -replace '\$\{KC_REALM\}', '%KC_REALM%' | Set-Content '%filename%'"