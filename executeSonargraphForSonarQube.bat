@rem Sample commandline to execute Sonargraph and SonarQube for the Sonargraph-SonarQube plugin.  
mvn clean package com.hello2morrow:sonargraph-maven-plugin:9.12.0:create-report -Dsonargraph.prepareForSonarQube=true -Dsonargraph.autoUpdate=false -Dsonargraph.installationDirectory=D:\00_repo\sgng-release\products\SonargraphBuild\dist\release\SonargraphBuild -Dsonargraph.licenseFile=C:\Users\Ingmar\AppData\Roaming\hello2morrow\Sonargraph\Standalone\Sonargraph.license sonar:sonar