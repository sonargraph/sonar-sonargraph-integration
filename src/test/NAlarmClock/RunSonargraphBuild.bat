@ECHO OFF

REM Sonargraph system to be used is provided by calling script!
REM set SONARGRAPH_SYSTEM= ...

set BUILD=11.2.0.100_2021-05-04-15-00
set CURRENT_DIR=%~dp0
set CONFIG=%CURRENT_DIR%startup.xml
set INST_DIR=D:\00_repo\sgng-master\products\SonargraphBuild\dist\release\SonargraphBuild
set OSGI_VERSION=3.16.100.v20201030-1916
set BUILD_CLIENT=%INST_DIR%\client\com.hello2morrow.sonargraph.build.client_%BUILD%.jar
set OSGI=%INST_DIR%\plugins\org.eclipse.osgi_%OSGI_VERSION%.jar

set LICENSE=C:\Users\Ingmar\AppData\Roaming\hello2morrow\Sonargraph\Build\SonargraphBuild.license

java -cp %BUILD_CLIENT%;%OSGI% -Xmx370m com.hello2morrow.sonargraph.build.client.SonargraphBuildRunner %CONFIG% installationDirectory=%INST_DIR% licenseFile=%LICENSE% systemDirectory=%SONARGRAPH_SYSTEM% 

echo For more verbose output, change the logLevel to debug.

if %ERRORLEVEL% EQU 0 (
   rem echo Success
) else (
   rem echo Error Level %errorlevel%
   exit /b %errorlevel%
)
