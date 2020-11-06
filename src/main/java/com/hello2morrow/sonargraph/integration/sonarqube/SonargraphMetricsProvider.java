/**
 * SonarQube Sonargraph Integration Plugin
 * Copyright (C) 2016-2020 hello2morrow GmbH
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
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.Metric.ValueType;

import com.hello2morrow.sonargraph.integration.access.foundation.Utility;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;

class SonargraphMetricsProvider extends AbstractDataProvider
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SonargraphMetricsProvider.class);

    static final String PROPERTIES_FILENAME = "SonargraphMetrics.properties";
    private static final String BUILT_IN_METRICS_RESOURCE_PATH = "/com/hello2morrow/sonargraph/integration/sonarqube/" + PROPERTIES_FILENAME;

    private static final String INT = "INT";
    private static final String FLOAT = "FLOAT";

    private static final int NUBMER_OF_VALUE_PARTS = 5;

    private Properties customMetrics;
    private Properties standardMetrics;
    private Properties combinedMetricProperties;

    SonargraphMetricsProvider()
    {
        this(System.getProperty("user.home") + "/." + SonargraphBase.SONARGRAPH_PLUGIN_KEY);
    }

    SonargraphMetricsProvider(final String customMetricsDirectoryPath)
    {
        super(PROPERTIES_FILENAME, customMetricsDirectoryPath);
    }

    void addMetricToProperties(final IMetricId metricId, final Properties metricProperties)
    {
        final String definition = createMetricDefinition(metricId);
        metricProperties.put(metricId.getName(), definition);
    }

    void addCustomMetric(final IMetricId metricId)
    {
        final String definition = createMetricDefinition(metricId);
        customMetrics.put(metricId.getName(), definition);
    }

    private String createMetricDefinition(final IMetricId metricId)
    {
        final StringJoiner result = new StringJoiner(SEPARATOR + "");
        result.add(metricId.getPresentationName());
        result.add((metricId.isFloat() ? FLOAT : INT));
        result.add(Double.toString(metricId.getBest()));
        result.add(Double.toString(metricId.getWorst()));
        result.add(SonargraphBase.trimDescription(metricId.getDescription()));
        return result.toString();
    }

    static String createSqMetricKeyFromStandardName(final String metricIdName)
    {
        String metricKey = SonargraphBase.METRIC_ID_PREFIX + Utility.convertMixedCaseStringToConstantName(metricIdName).replace(" ", "");
        metricKey = metricKey.replace(SEPARATOR, ' ');
        return metricKey;
    }

    Map<String, Metric<Serializable>> loadStandardMetrics()
    {
        standardMetrics = new SortedProperties();
        try (InputStream inputStream = SonargraphBase.class.getResourceAsStream(BUILT_IN_METRICS_RESOURCE_PATH))
        {
            standardMetrics.load(inputStream);
            LOGGER.debug("{}: Loaded standard metrics file '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, BUILT_IN_METRICS_RESOURCE_PATH);
        }
        catch (final IOException e)
        {
            LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Unable to load standard metrics file '"
                    + BUILT_IN_METRICS_RESOURCE_PATH + "'", e);
        }

        return convertMetricProperties(standardMetrics);
    }

    Map<String, Metric<Serializable>> convertMetricProperties(final Properties metricProperties)
    {
        if (metricProperties.isEmpty())
        {
            return Collections.emptyMap();
        }

        final Map<String, Metric<Serializable>> metrics = new HashMap<>(metricProperties.size());
        for (final Entry<Object, Object> nextEntry : metricProperties.entrySet())
        {
            String notCreatedInfo = null;
            final String nextKey = SonargraphBase.getNonEmptyString(nextEntry.getKey());
            final String nextValue = SonargraphBase.getNonEmptyString(nextEntry.getValue());

            try
            {
                final String[] nextSplitValue = nextValue.split("\\" + SEPARATOR);
                if (nextSplitValue.length == NUBMER_OF_VALUE_PARTS)
                {
                    final String nextMetricIdName = nextKey;
                    final String nextMetricKey = createSqMetricKeyFromStandardName(nextMetricIdName);
                    final String nextMetricPresentationName = nextSplitValue[0];
                    ValueType nextValueType = null;
                    final String nextTypeInfo = nextSplitValue[1];
                    if (FLOAT.equalsIgnoreCase(nextTypeInfo))
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
                            .setDescription(SonargraphBase.trimDescription(nextDescription))
                            .setDomain(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
                    SonargraphBase.setBestValue(nextBestValue, builder);
                    SonargraphBase.setWorstValue(nextWorstValue, builder);
                    SonargraphBase.setMetricDirection(nextBestValue, nextWorstValue, builder);

                    metrics.put(nextMetricKey, builder.create());
                }
                else
                {
                    notCreatedInfo = "Unable to create standard metric from '" + nextKey + "=" + nextValue;
                }
            }
            catch (final Exception e)
            {
                notCreatedInfo = "Unable to create standard metric from '" + nextKey + "=" + nextValue + " - " + e.getLocalizedMessage();
            }

            if (notCreatedInfo != null)
            {
                LOGGER.warn("{}: {}", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, notCreatedInfo);
            }
        }

        return metrics;
    }

    Properties getCustomMetricProperties()
    {
        return customMetrics;
    }

    /**
     * Load all properties files from the user-home containing metric definitions.
     */
    Map<String, Metric<Serializable>> loadCustomMetrics()
    {
        customMetrics = new SortedProperties();
        final String propertiesFilePath = getFilePath();
        final File file = new File(propertiesFilePath);
        if (!file.exists())
        {
            LOGGER.debug("{}: Custom metrics file '{}' does not exist.", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, propertiesFilePath);
        }
        else
        {
            try (FileInputStream fis = new FileInputStream(file))
            {
                customMetrics.load(fis);
                LOGGER.debug("{}: Loaded custom metrics file '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, propertiesFilePath);
            }
            catch (final IOException e)
            {
                final String msg = SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Unable to load custom metrics file '" + propertiesFilePath
                        + "'";
                LOGGER.error(msg, e);
            }
        }
        return convertMetricProperties(customMetrics);
    }

    File saveCustomMetricProperties(final String comment) throws IOException
    {
        return saveProperties(customMetrics, new File(getFilePath()), comment);
    }

    Properties getCombinedMetricProperties()
    {
        if (combinedMetricProperties == null)
        {
            combinedMetricProperties = new SortedProperties();
            combinedMetricProperties.putAll(customMetrics);
            combinedMetricProperties.putAll(standardMetrics);
        }
        return combinedMetricProperties;
    }
}