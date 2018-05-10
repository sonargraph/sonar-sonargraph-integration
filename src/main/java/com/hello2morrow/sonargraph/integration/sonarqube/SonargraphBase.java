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
    static final String RELATIVE_REPORT_PATH = SonargraphBase.CONFIG_PREFIX + ":" + "relative.report.path";
    static final String RELATIVE_REPORT_PATH_DEFAULT = "target/sonargraph/sonargraph-sonarqube-report.xml";

    static final String WORKSPACE = "Workspace";
    static final String SCRIPT_ISSUE_CATEGORY = "ScriptBased";
    static final String SCRIPT_ISSUE_CATEGORY_PRESENTATION_NAME = "Script Based";
    static final String SCRIPT_ISSUE_NAME = "ScriptIssue";
    static final String SCRIPT_ISSUE_PRESENTATION_NAME = "Script Issue";

    private static final Logger LOGGER = Loggers.get(SonargraphBase.class);
    private static final String CUSTOM_METRICS_DIRECTORY = System.getProperty("user.home") + "/." + SONARGRAPH_PLUGIN_KEY;
    private static final String CUSTOM_METRICS_FILE_NAME = "metrics.properties";
    private static final String CUSTOM_METRICS_PATH = CUSTOM_METRICS_DIRECTORY + "/" + CUSTOM_METRICS_FILE_NAME;
    private static final char CUSTOM_METRIC_SEPARATOR = '|';
    private static final String CUSTOM_METRIC_INT = "INT";
    private static final String CUSTOM_METRIC_FLOAT = "FLOAT";

    private static final String BUILT_IN_META_DATA_RESOURCE_PATH = "/com/hello2morrow/sonargraph/integration/sonarqube/ExportMetaData.xml";
    private static final List<String> IGNORE_ISSUE_TYPE_CATEGORIES = Arrays.asList(WORKSPACE, "InstallationConfiguration");

    private SonargraphBase()
    {
        super();
    }

    static String createMetricKeyFromStandardName(final String metricIdName)
    {
        assert metricIdName != null
                && metricIdName.length() > 0 : "Parameter 'metricIdName' of method 'createMetricKeyFromStandardName' must not be empty";
        return SonargraphBase.METRIC_ID_PREFIX + Utility.convertMixedCaseStringToConstantName(metricIdName).replace(" ", "");
    }

    static String createCustomMetricKeyFromStandardName(final String softwareSystemName, final String metricIdName)
    {
        assert softwareSystemName != null && softwareSystemName
                .length() > 0 : "Parameter 'softwareSystemName' of method 'createCustomMetricKeyFromStandardName' must not be empty";
        assert metricIdName != null
                && metricIdName.length() > 0 : "Parameter 'metricIdName' of method 'createCustomMetricKeyFromStandardName' must not be empty";
        String customMetricKey = SonargraphBase.METRIC_ID_PREFIX + softwareSystemName + "."
                + Utility.convertMixedCaseStringToConstantName(metricIdName).replace(" ", "");
        customMetricKey = customMetricKey.replace(CUSTOM_METRIC_SEPARATOR, ' ');
        return customMetricKey;
    }

    private static String trimDescription(final String description)
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
        assert bestValue != null : "Parameter 'bestValue' of method 'setMetricDirection' must not be null";
        assert worstValue != null : "Parameter 'worstValue' of method 'setMetricDirection' must not be null";
        assert metric != null : "Parameter 'metric' of method 'setMetricDirection' must not be null";

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
        assert worstValue != null : "Parameter 'worstValue' of method 'setWorstValue' must not be null";
        assert metric != null : "Parameter 'metric' of method 'setWorstValue' must not be null";

        if (!worstValue.equals(Double.NaN) && !worstValue.equals(Double.POSITIVE_INFINITY) && !worstValue.equals(Double.NEGATIVE_INFINITY))
        {
            metric.setWorstValue(worstValue);
        }
    }

    private static void setBestValue(final Double bestValue, final Metric.Builder metric)
    {
        assert bestValue != null : "Parameter 'bestValue' of method 'setBestValue' must not be null";
        assert metric != null : "Parameter 'metric' of method 'setBestValue' must not be null";

        if (!bestValue.equals(Double.NaN) && !bestValue.equals(Double.POSITIVE_INFINITY) && !bestValue.equals(Double.NEGATIVE_INFINITY))
        {
            metric.setBestValue(bestValue);
        }
    }

    static Metric<Serializable> createMetric(final IMetricId metricId)
    {
        assert metricId != null : "Parameter 'metricId' of method 'createMetric' must not be null";

        final Metric.Builder builder = new Metric.Builder(SonargraphBase.createMetricKeyFromStandardName(metricId.getName()),
                metricId.getPresentationName(), metricId.isFloat() ? Metric.ValueType.FLOAT : Metric.ValueType.INT)
                        .setDescription(trimDescription(metricId.getDescription())).setDomain(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);

        setBestValue(metricId.getBestValue(), builder);
        setWorstValue(metricId.getWorstValue(), builder);
        setMetricDirection(metricId.getBestValue(), metricId.getWorstValue(), builder);

        return builder.create();
    }

    static Properties loadCustomMetrics()
    {
        final Properties customMetrics = new Properties();

        try (FileInputStream fis = new FileInputStream(new File(CUSTOM_METRICS_PATH)))
        {
            customMetrics.load(fis);
            LOGGER.info(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Loaded custom metrics file '" + CUSTOM_METRICS_PATH + "'");
        }
        catch (final FileNotFoundException e)
        {
            LOGGER.info(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Custom metrics file '" + CUSTOM_METRICS_PATH + "' not found");
        }
        catch (final IOException e)
        {
            LOGGER.error(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Unable to load custom metrics file '" + CUSTOM_METRICS_PATH + "'", e);
        }

        return customMetrics;
    }

    static void addCustomMetric(final ISoftwareSystem softwareSystem, final IMetricId metricId, final Properties customMetrics)
    {
        assert softwareSystem != null : "Parameter 'softwareSystem' of method 'addCustomMetric' must not be null";
        assert metricId != null : "Parameter 'metricId' of method 'addCustomMetric' must not be null";
        assert customMetrics != null : "Parameter 'customMetrics' of method 'addCustomMetric' must not be null";

        customMetrics.put(softwareSystem.getName() + CUSTOM_METRIC_SEPARATOR + metricId.getName(),
                metricId.getPresentationName() + CUSTOM_METRIC_SEPARATOR + (metricId.isFloat() ? CUSTOM_METRIC_FLOAT : CUSTOM_METRIC_INT)
                        + CUSTOM_METRIC_SEPARATOR + metricId.getBestValue() + CUSTOM_METRIC_SEPARATOR + metricId.getWorstValue()
                        + CUSTOM_METRIC_SEPARATOR + trimDescription(metricId.getDescription()));
    }

    static void save(final Properties customMetrics)
    {
        assert customMetrics != null : "Parameter 'customMetrics' of method 'save' must not be null";
        try
        {
            final File file = new File(CUSTOM_METRICS_DIRECTORY);
            file.mkdirs();
            customMetrics.store(new FileWriter(new File(file, CUSTOM_METRICS_FILE_NAME)), "Custom Metrics");

            LOGGER.warn(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Custom metrics file '" + CUSTOM_METRICS_PATH
                    + "' updated, the SonarQube server needs to be restarted");

        }
        catch (final IOException e)
        {
            LOGGER.error(SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Unable to save custom metrics file '" + CUSTOM_METRICS_PATH + "'", e);
        }
    }

    private static String getNonEmptyString(final Object input)
    {
        if (input instanceof String && !((String) input).isEmpty())
        {
            return (String) input;
        }
        throw new IllegalArgumentException("Empty input");
    }

    static List<Metric<Serializable>> getCustomMetrics()
    {
        final Properties customMetrics = loadCustomMetrics();
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
                            .setDescription(trimDescription(nextDescription)).setDomain(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
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
                    LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + errorMsg + " - " + result.toString());
                }
                else
                {
                    return result.getOutcome();
                }
            }
            else
            {
                LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + errorMsg);
            }
        }
        catch (final IOException ex)
        {
            LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + errorMsg, ex);
        }

        return null;
    }

    static String createRuleKey(final String issueTypeName)
    {
        assert issueTypeName != null && issueTypeName.length() > 0 : "Parameter 'issueTypeName' of method 'createRuleKey' must not be empty";
        return Utility.convertMixedCaseStringToConstantName(issueTypeName).replace(" ", "_");
    }

    static String createRuleName(final String issueTypePresentationName)
    {
        assert issueTypePresentationName != null
                && issueTypePresentationName.length() > 0 : "Parameter 'issueTypePresentationName' of method 'createRuleName' must not be empty";
        return SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + issueTypePresentationName;
    }

    static String createRuleCategoryTag(final String categoryPresentationName)
    {
        assert categoryPresentationName != null
                && categoryPresentationName.length() > 0 : "Parameter 'categoryPresentationName' of method 'createRuleCategoryTag' must not be empty";
        return categoryPresentationName.replace(' ', '-').toLowerCase();
    }

    static boolean ignoreIssueType(final IIssueType issueType)
    {
        assert issueType != null : "Parameter 'issueType' of method 'ignoreIssueType' must not be null";
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

    static boolean isErrorOrWarningWorkspoceIssue(final IIssueType issueType)
    {
        assert issueType != null : "Parameter 'issueType' of method 'isErrorOrWarningWorkspoceIssue' must not be null";
        return WORKSPACE.equals(issueType.getCategory().getName())
                && (Severity.ERROR.equals(issueType.getSeverity()) || Severity.WARNING.equals(issueType.getSeverity()));
    }

    static boolean isScriptIssue(final IIssueType issueType)
    {
        assert issueType != null : "Parameter 'issueType' of method 'isScriptIssue' must not be null";
        return SCRIPT_ISSUE_CATEGORY.equals(issueType.getCategory().getName());
    }
}