/**
 * SonarQube Sonargraph Integration Plugin
 * Copyright (C) 2016-2017 hello2morrow GmbH
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
package com.hello2morrow.sonargraph.integration.sonarqube.foundation;

import com.hello2morrow.sonargraph.integration.access.foundation.Result.ICause;
import com.hello2morrow.sonargraph.integration.access.foundation.Utility;

public final class SonargraphPluginBase
{
    public enum ConfigurationMessageCause implements ICause
    {
        CONFIGURATION_ERROR;

        @Override
        public String getStandardName()
        {
            return Utility.convertConstantNameToStandardName(name());
        }

        @Override
        public String getPresentationName()
        {
            return Utility.convertConstantNameToPresentationName(name());
        }
    }

    public static final String PLUGIN_KEY = "sonargraphintegration";
    public static final String SONARGRAPH_PLUGIN_PRESENTATION_NAME = "Sonargraph Integration";

    //There is a max length of 64 characters for metric keys
    public static final String ABBREVIATION = "sg_i.";
    public static final String CONFIG_PREFIX = "sonar.sonargraph_integration.";
    public static final String COST_PER_INDEX_POINT = CONFIG_PREFIX + "index_point_cost";
    public static final double COST_PER_INDEX_POINT_DEFAULT = 11.0;
    public static final String CURRENCY = CONFIG_PREFIX + "currency";
    public static final String CURRENCY_DEFAULT = "USD";

    private static final String SONARQUBE_SONARGRAPH = "sonarqube.sonargraph_ng.";
    public static final String REPORT_PATH_OLD = SONARQUBE_SONARGRAPH + "report.path";

    public static final String REPORT_PATH = CONFIG_PREFIX + "report.path";
    public static final String METADATA_PATH = CONFIG_PREFIX + "exportmetadata.path";

    /**
     * Allows to override the base path of the system contained in the XML report.
     * Useful, if the report has been generated on a different machine with a different physical base path.
     */
    public static final String SYSTEM_BASE_DIRECTORY = CONFIG_PREFIX + "basedirectory.path";

    private SonargraphPluginBase()
    {
        // Don't instantiate
    }
}
