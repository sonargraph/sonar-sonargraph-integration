/**
 * SonarQube Sonargraph Integration Plugin
 * Copyright (C) 2016 hello2morrow GmbH
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
package com.hello2morrow.sonargraph.integration.sonarqube.api;

import java.util.ArrayList;
import java.util.List;

import org.sonar.api.Extension;
import org.sonar.api.Properties;
import org.sonar.api.Property;
import org.sonar.api.PropertyType;
import org.sonar.api.SonarPlugin;

import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphPluginBase;
import com.hello2morrow.sonargraph.integration.sonarqube.view.SonargraphArchitectureWidget;
import com.hello2morrow.sonargraph.integration.sonarqube.view.SonargraphStructuralDebtWidget;
import com.hello2morrow.sonargraph.integration.sonarqube.view.SonargraphStructureWidget;

/**
 * This class is the container for all others extensions
 */
@Properties({
        @Property(key = SonargraphPluginBase.CURRENCY, defaultValue = SonargraphPluginBase.CURRENCY_DEFAULT, name = "Currency", project = false, module = false, global = true),

        @Property(key = SonargraphPluginBase.COST_PER_INDEX_POINT, defaultValue = "" + SonargraphPluginBase.COST_PER_INDEX_POINT_DEFAULT, name = "Cost per metric point of 'Structural debt index' (0 means not displayed)", project = false, module = false, global = true, type = PropertyType.FLOAT),

        @Property(key = SonargraphPluginBase.METADATA_PATH, defaultValue = "", name = "Path of the directory containing XML files that contain the relevant meta-data. Server needs to be restarted after changes.", project = false, module = false, global = true),

        @Property(key = SonargraphPluginBase.REPORT_PATH, defaultValue = "", name = "Path of the Sonargraph report (empty means default value)", project = true, module = false, global = false) })
public final class SonargraphPlugin extends SonarPlugin
{

    @SuppressWarnings("rawtypes")
    @Override
    public List getExtensions()
    {
        final List<Class<? extends Extension>> list = new ArrayList<>(5);
        list.add(SonargraphRulesRepository.class);
        list.add(SonargraphSensor.class);

        list.add(SonargraphArchitectureWidget.class);
        list.add(SonargraphStructureWidget.class);
        list.add(SonargraphStructuralDebtWidget.class);
        return list;
    }
}
