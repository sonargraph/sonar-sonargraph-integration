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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;

import org.sonar.api.measures.Metric;
import org.sonar.api.measures.Metric.ValueType;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import com.hello2morrow.sonargraph.integration.access.controller.ControllerAccess;
import com.hello2morrow.sonargraph.integration.access.controller.IMetaDataController;
import com.hello2morrow.sonargraph.integration.access.foundation.ResultWithOutcome;
import com.hello2morrow.sonargraph.integration.access.foundation.Utility;
import com.hello2morrow.sonargraph.integration.access.model.IExportMetaData;
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
    static final String XML_REPORT_FILE_PATH_KEY = CONFIG_PREFIX + ":" + "report.path";
    static final String XML_REPORT_FILE_PATH_DEFAULT = "target/sonargraph/sonargraph-sonarqube-report.xml";

    static final String SONARGRAPH_BASE_DIR_KEY = CONFIG_PREFIX + ":" + "system.basedir";

    static final String SCRIPT_ISSUE_CATEGORY = "ScriptBased";
    static final String SCRIPT_ISSUE_CATEGORY_PRESENTATION_NAME = "Script Based";
    static final String SCRIPT_ISSUE_NAME = "ScriptIssue";
    static final String SCRIPT_ISSUE_PRESENTATION_NAME = "Script Issue";

    static final String PLUGIN_ISSUE_CATEGORY = "PluginBased";
    static final String PLUGIN_ISSUE_CATEGORY_PRESENTATION_NAME = "Plugin Based";
    static final String PLUGIN_ISSUE_NAME = "PluginIssue";
    static final String PLUGIN_ISSUE_PRESENTATION_NAME = "Plugin Issue";

    interface ICustomMetricsPropertiesProvider
    {
        public default String getDirectory()
        {
            return System.getProperty("user.home") + "/." + SONARGRAPH_PLUGIN_KEY;
        }

        public default String getFileName()
        {
            return "metrics.properties";
        }

        default String getFilePath()
        {
            return getDirectory() + "/" + getFileName();
        }
    }

    private static final Logger LOGGER = Loggers.get(SonargraphBase.class);
    private static final char CUSTOM_METRIC_SEPARATOR = '|';
    private static final String CUSTOM_METRIC_INT = "INT";
    private static final String CUSTOM_METRIC_FLOAT = "FLOAT";

    private static final String BUILT_IN_META_DATA_RESOURCE_PATH = "/com/hello2morrow/sonargraph/integration/sonarqube/ExportMetaData.xml";
    static final List<String> IGNORE_ISSUE_TYPE_CATEGORIES = Collections.unmodifiableList(
            Arrays.asList("Workspace", "InstallationConfiguration", "SystemConfiguration", "Session" /* deprecated, replaced by ArchitecturalView */,
                    "ArchitecturalView", "ArchitectureDefinition", "ArchitectureConsistency", "ScriptDefinition"));

    private static ICustomMetricsPropertiesProvider customMetricsPropertiesProvider = new ICustomMetricsPropertiesProvider()
    {
        //Default
    };

    private SonargraphBase()
    {
        super();
    }

    static void setCustomMetricsPropertiesProvider(final ICustomMetricsPropertiesProvider provider)
    {
        customMetricsPropertiesProvider = provider;
    }

    static String createMetricKeyFromStandardName(final String metricIdName)
    {
        return METRIC_ID_PREFIX + Utility.convertMixedCaseStringToConstantName(metricIdName).replace(" ", "");
    }

    static String createCustomMetricKeyFromStandardName(final String softwareSystemName, final String metricIdName)
    {
        String customMetricKey = METRIC_ID_PREFIX + softwareSystemName + "."
                + Utility.convertMixedCaseStringToConstantName(metricIdName).replace(" ", "");
        customMetricKey = customMetricKey.replace(CUSTOM_METRIC_SEPARATOR, ' ');
        return customMetricKey;
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

    private static void setMetricDirection(final Double bestValue, final Double worstValue, final Metric.Builder metric)
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

    private static void setWorstValue(final Double worstValue, final Metric.Builder metric)
    {
        if (!worstValue.equals(Double.NaN) && !worstValue.equals(Double.POSITIVE_INFINITY) && !worstValue.equals(Double.NEGATIVE_INFINITY))
        {
            metric.setWorstValue(worstValue);
        }
    }

    private static void setBestValue(final Double bestValue, final Metric.Builder metric)
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

        setBestValue(metricId.getBestValue(), builder);
        setWorstValue(metricId.getWorstValue(), builder);
        setMetricDirection(metricId.getBestValue(), metricId.getWorstValue(), builder);

        return builder.create();
    }

    static Properties loadCustomMetrics()
    {
        final Properties customMetrics = new Properties();

        try (FileInputStream fis = new FileInputStream(new File(customMetricsPropertiesProvider.getFilePath())))
        {
            customMetrics.load(fis);
            LOGGER.info(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Loaded custom metrics file '" + customMetricsPropertiesProvider.getFilePath() + "'");
        }
        catch (final FileNotFoundException e)
        {
            LOGGER.info(
                    SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Custom metrics file '" + customMetricsPropertiesProvider.getFilePath() + "' not found");
        }
        catch (final IOException e)
        {
            LOGGER.error(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Unable to load custom metrics file '"
                    + customMetricsPropertiesProvider.getFilePath() + "'", e);
        }

        return customMetrics;
    }

    static void addCustomMetric(final ISoftwareSystem softwareSystem, final IMetricId metricId, final Properties customMetrics)
    {
        customMetrics.put(softwareSystem.getName() + CUSTOM_METRIC_SEPARATOR + metricId.getName(),
                metricId.getPresentationName() + CUSTOM_METRIC_SEPARATOR + (metricId.isFloat() ? CUSTOM_METRIC_FLOAT : CUSTOM_METRIC_INT)
                        + CUSTOM_METRIC_SEPARATOR + metricId.getBestValue() + CUSTOM_METRIC_SEPARATOR + metricId.getWorstValue()
                        + CUSTOM_METRIC_SEPARATOR + trimDescription(metricId.getDescription()));
    }

    static void save(final Properties customMetrics)
    {
        try
        {
            final File file = new File(customMetricsPropertiesProvider.getDirectory());
            file.mkdirs();
            customMetrics.store(new FileWriter(new File(file, customMetricsPropertiesProvider.getFileName())), "Custom Metrics");

            LOGGER.warn(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Custom metrics file '" + customMetricsPropertiesProvider.getFilePath()
                    + "' updated, the SonarQube server needs to be restarted");
        }
        catch (final IOException e)
        {
            LOGGER.error(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Unable to save custom metrics file '"
                    + customMetricsPropertiesProvider.getFilePath() + "'", e);
        }
    }

    static String getNonEmptyString(final Object input)
    {
        if (input instanceof String && !((String) input).isEmpty())
        {
            return (String) input;
        }
        throw new IllegalArgumentException("Empty input");
    }

    static List<Metric<Serializable>> getCustomMetrics(final Properties customMetrics)
    {
        if (customMetrics.isEmpty())
        {
            return Collections.emptyList();
        }

        final List<Metric<Serializable>> metrics = new ArrayList<>(customMetrics.size());
        for (final Entry<Object, Object> nextEntry : customMetrics.entrySet())
        {
            String notCreatedInfo = null;
            final String nextKey = getNonEmptyString(nextEntry.getKey());
            final String nextValue = getNonEmptyString(nextEntry.getValue());

            try
            {
                final String[] nextSplitKey = nextKey.split("\\" + CUSTOM_METRIC_SEPARATOR);
                final String[] nextSplitValue = nextValue.split("\\" + CUSTOM_METRIC_SEPARATOR);

                if (nextSplitKey.length == 2 && nextSplitValue.length == 5)
                {
                    final String nextSoftwareSystemName = nextSplitKey[0];
                    final String nextMetricIdName = nextSplitKey[1];

                    final String nextMetricKey = createCustomMetricKeyFromStandardName(nextSoftwareSystemName, nextMetricIdName);
                    final String nextMetricPresentationName = nextSplitValue[0];
                    ValueType nextValueType = null;
                    final String nextTypeInfo = nextSplitValue[1];
                    if (CUSTOM_METRIC_FLOAT.equalsIgnoreCase(nextTypeInfo))
                    {
                        nextValueType = ValueType.FLOAT;
                    }
                    else
                    {
                        nextValueType = ValueType.INT;
                    }
                    final Double nextBestValue = Double.valueOf(nextSplitValue[2]);
                    final Double nextWorstValue = Double.valueOf(nextSplitValue[3]);
                    final String nextDescription = nextSplitValue[4];

                    final Metric.Builder builder = new Metric.Builder(nextMetricKey, nextMetricPresentationName, nextValueType)
                            .setDescription(trimDescription(nextDescription)).setDomain(SONARGRAPH_PLUGIN_PRESENTATION_NAME);
                    setBestValue(nextBestValue, builder);
                    setWorstValue(nextWorstValue, builder);
                    setMetricDirection(nextBestValue, nextWorstValue, builder);

                    metrics.add(builder.create());
                }
                else
                {
                    notCreatedInfo = "Unable to create custom metric from '" + nextKey + "=" + nextValue;
                }
            }
            catch (final Exception e)
            {
                notCreatedInfo = "Unable to create custom metric from '" + nextKey + "=" + nextValue + " - " + e.getLocalizedMessage();
            }

            if (notCreatedInfo != null)
            {
                LOGGER.warn(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + notCreatedInfo);
            }
        }

        return metrics;
    }

    static List<Metric<Serializable>> getCustomMetrics()
    {
        return getCustomMetrics(loadCustomMetrics());
    }

    static IExportMetaData readBuiltInMetaData()
    {
        final String errorMsg = "Failed to load built in meta data from '" + BUILT_IN_META_DATA_RESOURCE_PATH + "'";
        try (InputStream inputStream = SonargraphBase.class.getResourceAsStream(BUILT_IN_META_DATA_RESOURCE_PATH))
        {
            if (inputStream != null)
            {
                final IMetaDataController controller = ControllerAccess.createMetaDataController();
                final ResultWithOutcome<IExportMetaData> result = controller.loadExportMetaData(inputStream, BUILT_IN_META_DATA_RESOURCE_PATH);
                if (result.isFailure())
                {
                    LOGGER.error(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + errorMsg + " - " + result.toString());
                }
                else
                {
                    return result.getOutcome();
                }
            }
            else
            {
                LOGGER.error(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + errorMsg);
            }
        }
        catch (final IOException ex)
        {
            LOGGER.error(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + errorMsg, ex);
        }

        return null;
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

    static boolean ignoreIssueType(final IIssueType issueType)
    {
        final String categoryName = issueType.getCategory().getName();

        for (final String next : IGNORE_ISSUE_TYPE_CATEGORIES)
        {
            if (next.equals(categoryName))
            {
                return true;
            }
        }

        return false;
    }

    static boolean isIgnoredErrorOrWarningIssue(final IIssueType issueType)
    {
        return ignoreIssueType(issueType) && (Severity.ERROR.equals(issueType.getSeverity()) || Severity.WARNING.equals(issueType.getSeverity()));
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

    static IModule matchModule(final ISoftwareSystem softwareSystem, final String inputModuleKey, final File baseDirectory)
    {
        IModule matched = null;

        final List<IModule> moduleCandidates = getModuleCandidates(softwareSystem, baseDirectory);
        if (moduleCandidates.size() == 1)
        {
            matched = moduleCandidates.get(0);
        }

        if (matched == null)
        {
            LOGGER.info(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": No module match found for '" + inputModuleKey + "'");
        }
        else
        {
            LOGGER.info(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Matched module '" + matched.getName() + "'");
        }

        return matched;
    }

    private static List<IModule> getModuleCandidates(final ISoftwareSystem softwareSystem, final File baseDirectory)
    {
        final String identifyingBaseDirectoryPath = getIdentifyingPath(baseDirectory);
        final File systemBaseDirectory = new File(softwareSystem.getBaseDir());

        LOGGER.info(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Trying to match module using system base directory '" + systemBaseDirectory + "'");

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
                        LOGGER.info(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Matched root directory '" + nextIdentifyingPath + "' underneath '"
                                + identifyingBaseDirectoryPath + "'");
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
}