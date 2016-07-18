[![Build Status](https://api.travis-ci.org/sonargraph/sonar-sonargraph-integration.svg)](https://travis-ci.org/sonargraph/sonar-sonargraph-integration) [![Nemo Quality Gate status](https://sonarqube.com/api/badges/gate?key=com.hello2morrow%3Asonar-sonargraph-integration)](https://sonarqube.com/overview?id=com.hello2morrow%3Asonar-sonargraph-integration)                                                                                                                                                                                                                                                                                        

Sonargraph Integration Plugin
=================

- License: Apache License 2
- Author: [hello2morrow](https://www.hello2morrow.com)

Compatible with Sonargraph 8.7 and higher versions.
Requires Java 8 runtime.

Check the GitHub Wiki for information about compatibilities with SonarQube versions and Sonargraph versions.  

## Description / Features
This plugin for [SonarQube](http://www.sonarsource.com/) can be used to check the conformance of your code base to a 
formal architecture definition created with [Sonargraph](https://www.hello2morrow.com/products/sonargraph/architect9) version 8.7 and higher. 
A free license is available to allow you to check and measure the overall coupling and the level of cyclic dependencies at the package level. 
This license can be requested on the website of [Sonargraph Explorer](https://www.hello2morrow.com/products/sonargraph/explorer).
Usually a high level of coupling and cyclic dependencies points to a high level of 
structural erosion. If structural erosion grows over a certain level this will have a negative impact on testability, maintainability and 
comprehensibility of your code.

A detailed description of the capabilities and configuration can be found here: <a href="http://eclipse.hello2morrow.com/doc/build/content/sonarqube_integration.html">http://eclipse.hello2morrow.com/doc/build/content/sonarqube_integration.html</a> 

## Getting Started ##
- Download the latest SonarQube version
- Download the latest Sonargraph plugin and copy it into <sonarqube-inst>/extensions/plugins
- Start SonarQube server
- Change the current quality profile or create a new one that include at least one of the "Sonargraph Integration" rules. Assign your project to this profile.
- Change the dashboard configuration to include the "Sonargraph Integration" widgets.
- For the full functionality of Sonargraph, you need an "Architect" license. If you don't have one, just register on our <a href="">hello2morrow web site</a> and request a trial license.
  Alternatively, use a free Sonargraph Explorer license with reduced feature set (no architecture checks, no scripts execution, etc.) 
- Configure your build to run SonargraphBuild prior to the SonarQube scanner. Check the <a href="http://eclipse.hello2morrow.com/doc/build/content/">online documentation</a> of SonargraphBuild.
  There are specific chapters for the integration with Ant, Maven, Gradle and the command shell. Don't forget to configure the "prepareForSonarQube" flag!
  Check the example multi-maven project contained in this repository at <a href="https://github.com/sonargraph/sonar-sonargraph-integration/tree/master/src/test/AlarmClockMain">src/test/AlarmClockMain</a>.
  There are various build files and batch files available that demonstrate how the analysis can be executed.
- Execute the build, check the console log, that the Sonargraph Integration plugin has been executed. In SonarQube the widgets should now display metrics determined by Sonargraph and if your
  projects contains architecture violations or cyclic dependencies, these should be visible as issues.
- If you have difficulties setting up the integration, check first the online documentation (it's searchable!). If that does not provide any answer, feel free to send a mail to the <a href="https://groups.google.com/forum/#!forum/sonarqube">SonarQube Google group</a>
  or directly to support at hello2morrow.com.  
  
 

