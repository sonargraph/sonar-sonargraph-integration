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

import java.util.List;
import java.util.Optional;

import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Project;

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

    private static final String WORKSPACE_ID = "Workspace:";
    private static final String GROUP_ARTIFACT_SEPARATOR = ":";
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

    public static boolean isAggregatingProject(final Project project)
    {
        if (project == null)
        {
            return false;
        }
        return project.getModules() != null && !project.getModules().isEmpty();
    }

    public static boolean isRootParentProject(final Project project)
    {
        boolean isRootParentProject = false;
        if (project == null)
        {
            return false;
        }
        final List<Project> modules = project.getModules();
        if (project.getParent() == null && modules != null && !modules.isEmpty())
        {
            isRootParentProject = true;
        }
        return isRootParentProject;
    }

    public static Optional<InputPath> getResource(final FileSystem fileSystem, final String absolutePath)
    {
        assert fileSystem != null : "Parameter 'fileSystem' of method 'getResource' must not be null";
        assert absolutePath != null : "Parameter 'absolutePath' of method 'getResource' must not be null";

        final String normalizedPath = Utility.convertPathToUniversalForm(absolutePath);
        return Optional.ofNullable(fileSystem.inputFile(fileSystem.predicates().hasAbsolutePath(normalizedPath)));
    }

    public static boolean buildUnitMatchesAnalyzedProject(final String buName, final Project project)
    {
        if (buName.equals(project.getName()))
        {
            return true;
        }

        final boolean isBranch = project.getBranch() != null && project.getBranch().length() > 0;
        final String[] elements = project.key().split(GROUP_ARTIFACT_SEPARATOR);
        assert elements.length >= 1 : "project.getKey() must not return an empty string";

        boolean result = false;

        final String groupId = elements[0];
        String artifactId = elements[elements.length - 1];
        /**
         * We need this check to support sonar.branch functionality. Branch tags are appended to the project key
         * <group-id>:<artifact-id>:<branch-tag>
         */
        if (isBranch)
        {
            artifactId = elements[elements.length - 2];
        }

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

    public static boolean areSonargraphRulesActive(final RulesProfile profile)
    {
        assert profile != null : "Parameter 'profile' of method 'areSonargraphRulesActive' must not be null";
        return !profile.getActiveRulesByRepository(SonargraphPluginBase.PLUGIN_KEY).isEmpty();
    }

    public static String getSourceFilePath(final String groupParentPath, final String sourceFilePath)
    {
        final int lastIndexOf = sourceFilePath.lastIndexOf('/');
        final String dirOfSourceFile = sourceFilePath.substring(0, lastIndexOf);
        if (groupParentPath.endsWith(dirOfSourceFile))
        {
            return groupParentPath + sourceFilePath.substring(lastIndexOf);
        }
        return null;
    }

    public static String toLowerCase(String input, final boolean firstLower)
    {
        assert input != null : "Parameter 'input' of method 'toLowerCase' must not be null";

        if (input.isEmpty())
        {
            return input;
        }

        if (input.length() == 1)
        {
            return firstLower ? input.toLowerCase() : input.toUpperCase();
        }

        input = input.toLowerCase();
        return firstLower ? input : Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }
}