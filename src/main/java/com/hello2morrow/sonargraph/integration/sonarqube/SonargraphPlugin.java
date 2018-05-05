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
import org.sonar.api.PropertyType;

@Properties({
        @Property(key = SonargraphBase.CURRENCY, defaultValue = SonargraphBase.CURRENCY_DEFAULT, name = "Currency", project = false, module = false, global = true),
        @Property(key = SonargraphBase.COST_PER_INDEX_POINT, defaultValue = ""
                + SonargraphBase.COST_PER_INDEX_POINT_DEFAULT, name = "Cost per metric point of 'Structural debt index' (0 means not displayed)", project = false, module = false, global = true, type = PropertyType.FLOAT),
        @Property(key = SonargraphBase.METADATA_PATH, defaultValue = "", name = "Path of the directory containing XML files that contain the relevant meta-data. Server needs to be restarted after changes.", project = false, module = false, global = true),
        @Property(key = SonargraphBase.REPORT_PATH, defaultValue = "", name = "Path of the Sonargraph report (empty means default value)", project = true, module = false, global = false) })
public final class SonargraphPlugin implements Plugin
{
    public SonargraphPlugin()
    {
        super();
    }

    @Override
    public void define(final Context context)
    {
        assert context != null : "Parameter 'context' of method 'define' must not be null";
        context.addExtensions(SonargraphRulesAndMetrics.class, SonargraphProfile.class, SonargraphSensor.class);
    }
}