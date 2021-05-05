@ECHO OFF
set SONARGRAPH_SYSTEM=%~dp0/NAlarmClock.sonargraph
call RunSonargraphBuild.bat
echo For more verbose output, change the logLevel to debug.

if %ERRORLEVEL% EQU 0 (
   rem echo Success
) else (
   rem echo Error Level %errorlevel%
   exit /b %errorlevel%
)
