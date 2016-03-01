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
package com.hello2morrow.sonargraph.integration.sonarqube.view;

import org.sonar.api.web.AbstractRubyTemplate;
import org.sonar.api.web.Description;
import org.sonar.api.web.RubyRailsWidget;
import org.sonar.api.web.WidgetCategory;

import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphPluginBase;

@WidgetCategory(SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME)
@Description("Reports metrics on the structural quality of the project.")
public final class SonargraphStructureWidget extends AbstractRubyTemplate implements RubyRailsWidget
{
    @Override
    protected String getTemplatePath()
    {
        //"D:/00_repos/00_e4-sgng/com.hello2morrow.sonargraph.integration.sonarqube/src/main/resources//sonargraph_structure_widget.html.erb"
        return "/sonargraph_structure_widget.html.erb";
    }

    @Override
    public String getId()
    {
        return SonargraphPluginBase.PLUGIN_KEY.toLowerCase() + "_structure";
    }

    @Override
    public String getTitle()
    {
        return SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + " Structure";
    }
}