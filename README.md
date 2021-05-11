[![Build Status](https://api.travis-ci.org/sonargraph/sonar-sonargraph-integration.svg)](https://travis-ci.org/sonargraph/sonar-sonargraph-integration) [![Nemo Quality Gate status](https://sonarqube.com/api/badges/gate?key=com.hello2morrow%3Asonar-sonargraph-integration)](https://sonarqube.com/overview?id=com.hello2morrow%3Asonar-sonargraph-integration)                                                                                                                                                                                                                                                                                        

Sonargraph Integration Plugin
=================

- License: Apache License 2
- Author: [hello2morrow](https://www.hello2morrow.com)

Compatible with Sonargraph 8.7 and higher versions.
Requires Java 8 runtime.
Currently only supports Sonargraph systems containing Java or C# modules. 

Check the <a href="https://github.com/sonargraph/sonar-sonargraph-integration/wiki/Sonargraph-8--Integration-with-SonarQube">GitHub Wiki</a> for information about compatibilities with SonarQube versions and Sonargraph versions.


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
1. Download the latest SonarQube version.
2. Download the latest Sonargraph plugin and copy it into &lt;sonarqube-inst&gt;/extensions/plugins (or use the Update Center, once it is available there).
3. Start the SonarQube server.
4. Use the built-in Sonargraph quality profile or add individual "Sonargraph Integration" rules to the profile you want to use. Assign your project to this profile.
5. For the full functionality of Sonargraph, you need an "Architect" license. If you don't have one, just register on our <a href="">hello2morrow web site</a> and request a trial license.  
   Alternatively, use a free Sonargraph Explorer license with reduced feature set (no architecture checks, no scripts execution, etc.) 
6. Configure your build to run SonargraphBuild **prior** to the SonarQube scanner.  
   Check the <a href="http://eclipse.hello2morrow.com/doc/build/content/">online documentation of SonargraphBuild</a>. There are specific chapters for the integration with Ant, Maven, Gradle and the command shell.
   **Don't forget to configure the "prepareForSonarQube" flag!**    
   There are various build files and batch files available that demonstrate how the analysis can be executed.  
   Example Maven command-line to create a report (check the <a href="http://eclipse.hello2morrow.com/doc/build/content/integrating_with_maven.html">online documentation</a> for configuration details):
           <code>mvn clean package sonargraph:create-report</code>   
7. Execute the build and check in the console log that the Sonargraph Integration plugin has been executed.
8. If you have difficulties setting up the integration, check first the online documentation (it's searchable!). If that does not provide any answer, feel free to send an email to support at hello2morrow.com. 
   It certainly helps us to help you, if you include the console log in the email.
