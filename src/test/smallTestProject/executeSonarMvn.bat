set JAVA_HOME=C:\Programs\Jdk\openjdk-11.0.1+13
set PATH=%JAVA_HOME%\bin;%PATH%
mvn clean package -Dmaven.test.skip=true com.hello2morrow:sonargraph-maven-plugin:10.4.0:create-report -Dsonargraph.systemDirectory=./AlarmClock.sonargraph -Dsonargraph.prepareForSonarQube=true -Dsonargraph.autoUpdate=false -Dsonargraph.installationDirectory=D:\00_repo\sgng\products\SonargraphBuild\dist\release\SonargraphBuild -Dsonargraph.licenseFile=C:\Users\Ingmar\AppData\Roaming\hello2morrow\Sonargraph\Standalone\Sonargraph.license sonar:sonar -e

rem mvnDebug sonar:sonar -Dsonar.sonargraph.integration:report.path=./AlarmClock.xml