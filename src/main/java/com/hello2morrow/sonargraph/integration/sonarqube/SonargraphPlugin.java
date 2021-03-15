/**
 * SonarQube Sonargraph Integration Plugin
 * Copyright (C) 2016-2020 hello2morrow GmbH
 * mailto: support AT hello2morrow DOT com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hello2morrow.sonargraph.integration.sonarqube;

import org.sonar.api.Plugin;
import org.sonar.api.Properties;
import org.sonar.api.Property;

@Properties({
        @Property(key = SonargraphBase.XML_REPORT_FILE_PATH_KEY, name = "XML report file path", project = true, module = false, global = false, description = ""
                + "The Sonargraph integration reads the XML report file and adds the issues and metrics to the corresponding elements."
                + " Per default the XML report file is expected under each module (project and sub-modules) at the relative path '"
                + SonargraphBase.XML_REPORT_FILE_PATH_DEFAULT + "'."
                + " It is also possible to use an absolute path, in that case you need to provide the XML report file only at 1 location."
                + " In both cases make sure that the xml report file is there before running the SonarQube analysis scan.") })
public final class SonargraphPlugin implements Plugin
{
    public SonargraphPlugin()
    {
        super();
    }

    @Override
    public void define(final Context context)
    {
        context.addExtensions(SonargraphRules.class, SonargraphMetrics.class, StandardSonargraphProfileJava.class,
                StrictSonargraphProfileJava.class, StandardSonargraphProfileCSharp.class,
                StrictSonargraphProfileCSharp.class, SonargraphSensor.class);
    }
}