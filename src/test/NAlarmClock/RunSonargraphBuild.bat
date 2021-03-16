@ECHO OFF
set CONFIG=./startup.xml
set BUILD=11.1.0.100_2021-03-15-12-00
set OSGI_VERSION=3.16.100.v20201030-1916
set INST_DIR=D:\00_repo\sgng-master\products\SonargraphBuild\dist\release\SonargraphBuild
set BUILD_CLIENT=%INST_DIR%\client\com.hello2morrow.sonargraph.build.client_%BUILD%.jar
set OSGI=%INST_DIR%\plugins\org.eclipse.osgi_%OSGI_VERSION%.jar

java -cp %BUILD_CLIENT%;%OSGI% -Xmx370m com.hello2morrow.sonargraph.build.client.SonargraphBuildRunner %CONFIG%

echo For more verbose output, change the logLevel to debug.

if %ERRORLEVEL% EQU 0 (
   rem echo Success
) else (
   rem echo Error Level %errorlevel%
   exit /b %errorlevel%
)
