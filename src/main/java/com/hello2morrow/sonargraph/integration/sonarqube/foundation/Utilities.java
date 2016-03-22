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
package com.hello2morrow.sonargraph.integration.sonarqube.foundation;

import java.util.List;
import java.util.Optional;

import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Project;

import com.hello2morrow.sonargraph.integration.access.foundation.FileUtility;

public final class Utilities
{
    private static final String WORKSPACE_ID = "Workspace:";
    private static final String GROUP_ARTIFACT_SEPARATOR = ":";
    public static final String UNKNOWN = "<UNKNOWN>";

    private Utilities()
    {
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

        final String normalizedPath = FileUtility.convertPathToUniversalForm(absolutePath);
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

}
