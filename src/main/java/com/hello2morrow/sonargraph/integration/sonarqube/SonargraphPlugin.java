/**
 * SonarQube Sonargraph Integration Plugin
 * Copyright (C) 2016-2018 hello2morrow GmbH
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
        @Property(key = SonargraphBase.RELATIVE_REPORT_PATH, defaultValue = SonargraphBase.RELATIVE_REPORT_PATH_DEFAULT, name = "Relative path to the Sonargraph xml report file (default is '"
                + SonargraphBase.RELATIVE_REPORT_PATH_DEFAULT + "')", project = true, module = true, global = true) })
public final class SonargraphPlugin implements Plugin
{
    public SonargraphPlugin()
    {
        super();
    }

    @Override
    public void define(final Context context)
    {
        context.addExtensions(SonargraphRules.class, SonargraphMetrics.class, SonargraphProfile.class, SonargraphSensor.class);
    }
}