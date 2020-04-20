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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.measures.Metric;

import com.hello2morrow.sonargraph.integration.access.foundation.Utility;
import com.hello2morrow.sonargraph.integration.access.model.IIssueType;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;
import com.hello2morrow.sonargraph.integration.access.model.IModule;
import com.hello2morrow.sonargraph.integration.access.model.IRootDirectory;
import com.hello2morrow.sonargraph.integration.access.model.ISoftwareSystem;
import com.hello2morrow.sonargraph.integration.access.model.Severity;

final class SonargraphBase
{
    static final String SONARGRAPH_PLUGIN_KEY = "sonargraphintegration";
    static final String SONARGRAPH_PLUGIN_PRESENTATION_NAME = "Sonargraph Integration";
    static final String SONARGRAPH_RULE_TAG = "sonargraph-integration";
    static final String JAVA = "java";
    static final String METRIC_ID_PREFIX = "sg_i.";//There is a max length of 64 characters for metric keys

    static final String CONFIG_PREFIX = "sonar.sonargraph.integration";
    static final String SONARGRAPH_BASE_DIR_KEY = CONFIG_PREFIX + ":" + "system.basedir";
    static final String XML_REPORT_FILE_PATH_KEY = CONFIG_PREFIX + ":" + "report.path";
    static final String XML_REPORT_FILE_PATH_DEFAULT = "target/sonargraph/sonargraph-sonarqube-report.xml";

    static final String SCRIPT_ISSUE_CATEGORY = "ScriptBased";
    static final String SCRIPT_ISSUE_CATEGORY_PRESENTATION_NAME = "Script Based";
    static final String SCRIPT_ISSUE_NAME = "ScriptIssue";
    static final String SCRIPT_ISSUE_PRESENTATION_NAME = "Script Issue";

    static final String PLUGIN_ISSUE_CATEGORY = "PluginBased";
    static final String PLUGIN_ISSUE_CATEGORY_PRESENTATION_NAME = "Plugin Based";
    static final String PLUGIN_ISSUE_NAME = "PluginIssue";
    static final String PLUGIN_ISSUE_PRESENTATION_NAME = "Plugin Issue";

    private static final Logger LOGGER = LoggerFactory.getLogger(SonargraphBase.class);

    static final Set<String> IGNORE_ISSUE_TYPE_CATEGORIES = Collections.unmodifiableSet(Arrays
            .asList("Workspace", "InstallationConfiguration", "SystemConfiguration", "Session" /* deprecated, replaced by ArchitecturalView */,
                    "ArchitecturalView", "ArchitectureDefinition", "ArchitectureConsistency", "ScriptDefinition")
            .stream().collect(Collectors.toSet()));

    private SonargraphBase()
    {
        super();
    }

    static String createMetricKeyFromStandardName(final String metricIdName)
    {
        return METRIC_ID_PREFIX + Utility.convertMixedCaseStringToConstantName(metricIdName).replace(" ", "");
    }

    static String toLowerCase(String input, final boolean firstLower)
    {
        if (input == null)
        {
            return "";
        }
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

    static String trimDescription(final String description)
    {
        if (description != null && !description.isEmpty())
        {
            final String trimmedDescription = description.replaceAll("\r", " ").replaceAll("\n", " ").trim();
            return trimmedDescription.length() > 255 ? trimmedDescription.substring(0, 252) + "..." : trimmedDescription;
        }
        return "";
    }

    static void setMetricDirection(final Double bestValue, final Double worstValue, final Metric.Builder metric)
    {
        if (bestValue > worstValue)
        {
            metric.setDirection(Metric.DIRECTION_BETTER);
        }
        else if (bestValue < worstValue)
        {
            metric.setDirection(Metric.DIRECTION_WORST);
        }
        else
        {
            metric.setDirection(Metric.DIRECTION_NONE);
        }
    }

    static void setWorstValue(final Double worstValue, final Metric.Builder metric)
    {
        if (!worstValue.equals(Double.NaN) && !worstValue.equals(Double.POSITIVE_INFINITY) && !worstValue.equals(Double.NEGATIVE_INFINITY))
        {
            metric.setWorstValue(worstValue);
        }
    }

    static void setBestValue(final Double bestValue, final Metric.Builder metric)
    {
        if (!bestValue.equals(Double.NaN) && !bestValue.equals(Double.POSITIVE_INFINITY) && !bestValue.equals(Double.NEGATIVE_INFINITY))
        {
            metric.setBestValue(bestValue);
        }
    }

    static Metric<Serializable> createMetric(final IMetricId metricId)
    {
        final Metric.Builder builder = new Metric.Builder(createMetricKeyFromStandardName(metricId.getName()), metricId.getPresentationName(),
                metricId.isFloat() ? Metric.ValueType.FLOAT : Metric.ValueType.INT).setDescription(trimDescription(metricId.getDescription()))
                        .setDomain(SONARGRAPH_PLUGIN_PRESENTATION_NAME);

        setBestValue(metricId.getBest(), builder);
        setWorstValue(metricId.getWorst(), builder);
        setMetricDirection(metricId.getBest(), metricId.getWorst(), builder);

        return builder.create();
    }

    static String createRuleKey(final String issueTypeName)
    {
        return Utility.convertMixedCaseStringToConstantName(issueTypeName).replace(" ", "_");
    }

    static String createRuleKeyToCheck(final IIssueType issueType)
    {
        if (isScriptIssue(issueType))
        {
            return SonargraphBase.createRuleKey(SonargraphBase.SCRIPT_ISSUE_NAME);
        }
        if (isPluginIssue(issueType))
        {
            return SonargraphBase.createRuleKey(SonargraphBase.PLUGIN_ISSUE_NAME);
        }
        return SonargraphBase.createRuleKey(issueType.getName());
    }

    static String createRuleName(final String issueTypePresentationName)
    {
        return SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + issueTypePresentationName;
    }

    static String createRuleCategoryTag(final String categoryPresentationName)
    {
        return categoryPresentationName.replace(' ', '-').toLowerCase();
    }

    static boolean ignoreIssueType(final String categoryName)
    {
        return IGNORE_ISSUE_TYPE_CATEGORIES.contains(categoryName);
    }

    static boolean ignoreIssueType(final IIssueType issueType)
    {
        return ignoreIssueType(issueType.getCategory().getName());
    }

    static boolean isIgnoredErrorOrWarningIssue(final IIssueType issueType)
    {
        return ignoreIssueType(issueType.getCategory().getName())
                && (Severity.ERROR.equals(issueType.getSeverity()) || Severity.WARNING.equals(issueType.getSeverity()));
    }

    static boolean isScriptIssue(final IIssueType issueType)
    {
        return SCRIPT_ISSUE_CATEGORY.equals(issueType.getCategory().getName());
    }

    static boolean isPluginIssue(final IIssueType issueType)
    {
        return PLUGIN_ISSUE_CATEGORY.equals(issueType.getCategory().getName());
    }

    private static String getIdentifyingPath(final File file)
    {
        try
        {
            return file.getCanonicalPath().replace('\\', '/');
        }
        catch (final IOException e)
        {
            return file.getAbsolutePath().replace('\\', '/');
        }
    }

    static IModule matchModule(final ISoftwareSystem softwareSystem, final String inputModuleKey, final File baseDirectory, final boolean isProject)
    {
        IModule matched = null;

        final String sqMsgPart = "SonarQube " + (isProject ? "project" : "module") + " '" + inputModuleKey + "'.";
        final List<IModule> moduleCandidates = getSonargraphModuleCandidates(softwareSystem, baseDirectory);
        if (moduleCandidates.isEmpty())
        {
            LOGGER.warn("{}: No Sonargraph module match found for {}", SONARGRAPH_PLUGIN_PRESENTATION_NAME, sqMsgPart);
        }
        else if (moduleCandidates.size() == 1)
        {
            matched = moduleCandidates.get(0);
            LOGGER.info("{}: Matched Sonargraph module '{}' for {}", SONARGRAPH_PLUGIN_PRESENTATION_NAME, matched.getName(), sqMsgPart);
        }
        else
        {
            LOGGER.warn("{}: Skip Sonargraph module processing as {} modules are detected as potential matches for {}",
                    SONARGRAPH_PLUGIN_PRESENTATION_NAME, moduleCandidates.size(), sqMsgPart);
        }

        return matched;
    }

    /**
     * Determines the Sonargraph module(s) with the highest number of root directories that can be located underneath the given baseDirectory.
     *
     * @param softwareSystem
     * @param baseDirectory
     * @return A list of matching Sonargraph modules. Problems are indicated by list size of 0 (no match) or > 1 (several modules found).
     */
    private static List<IModule> getSonargraphModuleCandidates(final ISoftwareSystem softwareSystem, final File baseDirectory)
    {
        final String identifyingBaseDirectoryPath = getIdentifyingPath(baseDirectory);
        final File systemBaseDirectory = new File(softwareSystem.getBaseDir());

        LOGGER.info("{}: Trying to match module using system base directory '{}'", SONARGRAPH_PLUGIN_PRESENTATION_NAME, systemBaseDirectory);

        final TreeMap<Integer, List<IModule>> numberOfMatchedRootDirsToModules = new TreeMap<>();
        for (final IModule nextModule : softwareSystem.getModules().values())
        {
            int matchedRootDirs = 0;

            for (final IRootDirectory nextRootDirectory : nextModule.getRootDirectories())
            {
                final String nextRelPath = nextRootDirectory.getRelativePath();
                final File nextAbsoluteRootDirectory = new File(systemBaseDirectory, nextRelPath);
                if (nextAbsoluteRootDirectory.exists())
                {
                    final String nextIdentifyingPath = getIdentifyingPath(nextAbsoluteRootDirectory);
                    if (nextIdentifyingPath.startsWith(identifyingBaseDirectoryPath))
                    {
                        LOGGER.info("{}: Matched Sonargraph root directory '{}' underneath '{}'", SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                                nextIdentifyingPath, identifyingBaseDirectoryPath);
                        matchedRootDirs++;
                    }
                }
            }

            if (matchedRootDirs > 0)
            {
                final Integer nextMatchedRootDirsAsInteger = Integer.valueOf(matchedRootDirs);
                final List<IModule> nextMatched = numberOfMatchedRootDirsToModules.computeIfAbsent(nextMatchedRootDirsAsInteger,
                        k -> new ArrayList<>(2));
                nextMatched.add(nextModule);
            }
        }

        if (!numberOfMatchedRootDirsToModules.isEmpty())
        {
            return numberOfMatchedRootDirsToModules.lastEntry().getValue();
        }

        return Collections.emptyList();
    }

    static String getNonEmptyString(final Object input)
    {
        if (input instanceof String && !((String) input).isEmpty())
        {
            return (String) input;
        }
        throw new IllegalArgumentException("Empty input");
    }
}