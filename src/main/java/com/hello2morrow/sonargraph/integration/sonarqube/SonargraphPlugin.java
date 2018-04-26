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

import java.util.ArrayList;
import java.util.List;

import org.sonar.api.Plugin;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;

public final class SonargraphPlugin implements Plugin
{
    private static final List<PropertyDefinition> PROPERTY_DEFINITIONS = new ArrayList<>(2);

    static
    {
        PROPERTY_DEFINITIONS.add(PropertyDefinition.builder(SonargraphPluginBase.METADATA_PATH).name("Path of meta data xml file")
                .description(
                        "Path of the directory containing XML files that contain the relevant meta-data. Server needs to be restarted after changes.")
                .defaultValue("").type(PropertyType.TEXT).build());
        PROPERTY_DEFINITIONS.add(PropertyDefinition.builder(SonargraphPluginBase.REPORT_PATH).name("Path of report xml file")
                .description("Path of the Sonargraph xml report file (empty means default value).").defaultValue("").type(PropertyType.TEXT).build());

    }

    public SonargraphPlugin()
    {
        super();
    }

    @Override
    public void define(final Context context)
    {
        assert context != null : "Parameter 'context' of method 'define' must not be null";
        //TODO Add version info?
        context.addExtension(PROPERTY_DEFINITIONS);
        context.addExtensions(SonargraphRulesRepository.class, SonargraphSensor.class);
    }
}