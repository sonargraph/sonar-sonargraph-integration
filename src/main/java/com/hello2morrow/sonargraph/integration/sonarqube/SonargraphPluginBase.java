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

import org.sonar.api.batch.fs.InputModule;

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

    private static final String SONARQUBE_SONARGRAPH = "sonarqube.sonargraph_ng.";
    private static final String WORKSPACE_ID = "Workspace:";
    private static final String GROUP_ARTIFACT_SEPARATOR = ":";

    public static final String PLUGIN_KEY = "sonargraphintegration";
    public static final String SONARGRAPH_PLUGIN_PRESENTATION_NAME = "Sonargraph Integration";
    //There is a max length of 64 characters for metric keys
    public static final String ABBREVIATION = "sg_i.";
    public static final String CONFIG_PREFIX = "sonar.sonargraph_integration.";
    public static final String COST_PER_INDEX_POINT = CONFIG_PREFIX + "index_point_cost";
    public static final double COST_PER_INDEX_POINT_DEFAULT = 11.0;
    public static final String CURRENCY = CONFIG_PREFIX + "currency";
    public static final String CURRENCY_DEFAULT = "USD";
    public static final String REPORT_PATH_OLD = SONARQUBE_SONARGRAPH + "report.path";
    public static final String REPORT_PATH = CONFIG_PREFIX + "report.path";
    public static final String METADATA_PATH = CONFIG_PREFIX + "exportmetadata.path";
    public static final String UNKNOWN = "<UNKNOWN>";
    /**
     * Allows to override the base path of the system contained in the XML report.
     * Useful, if the report has been generated on a different machine with a different physical base path.
     */
    public static final String SYSTEM_BASE_DIRECTORY = CONFIG_PREFIX + "basedirectory.path";

    private SonargraphPluginBase()
    {
        super();
    }

    public static String getBuildUnitName(final String fqName)
    {
        if (fqName == null)
        {
            return UNKNOWN;
        }

        if (fqName.startsWith(WORKSPACE_ID))
        {
            return fqName.substring(WORKSPACE_ID.length(), fqName.length());
        }

        return UNKNOWN;
    }

    public static boolean buildUnitMatchesAnalyzedProject(final String buName, final InputModule project)
    {
        assert buName != null : "Parameter 'buName' of method 'buildUnitMatchesAnalyzedProject' must not be null";
        assert project != null : "Parameter 'project' of method 'buildUnitMatchesAnalyzedProject' must not be null";

        //        if (buName.equals(project.getName()))
        //        {
        //            return true;
        //        }

        //        final boolean isBranch = project.getBranch() != null && project.getBranch().length() > 0;
        final String[] elements = project.key().split(GROUP_ARTIFACT_SEPARATOR);
        assert elements.length >= 1 : "project.getKey() must not return an empty string";

        boolean result = false;

        final String groupId = elements[0];
        final String artifactId = elements[elements.length - 1];
        /**
         * We need this check to support sonar.branch functionality. Branch tags are appended to the project key
         * <group-id>:<artifact-id>:<branch-tag>
         */
        //        if (isBranch)//TODO
        //        {
        //            artifactId = elements[elements.length - 2];
        //        }

        final String longName = artifactId + "[" + groupId + "]";
        final String longName2 = groupId + ':' + artifactId;

        if (buName.equalsIgnoreCase(artifactId))
        {
            result = true;
        }
        if (buName.equalsIgnoreCase(longName))
        {
            result = true;
        }
        if (buName.equalsIgnoreCase(longName2))
        {
            result = true;
        }

        if (buName.startsWith("...") && longName2.endsWith(buName.substring(2)))
        {
            result = true;
        }

        return result;
    }
}