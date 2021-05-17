CALL RunSonargraphBuild.bat
set PATH=%PATH%;C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\MSBuild\Current\Bin\amd64\
SonarScanner.MSBuild.exe begin /k:"NAlarmClock" /d:sonar.verbose=false
MSBuild.exe ./NAlarmClock.sln /t:Rebuild /p:Configuration=Debug /p:Platform=x86
SonarScanner.MSBuild.exe end