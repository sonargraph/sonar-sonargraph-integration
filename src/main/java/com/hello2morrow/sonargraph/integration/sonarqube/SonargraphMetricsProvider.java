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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.Metric.ValueType;

import com.hello2morrow.sonargraph.integration.access.foundation.Utility;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;
import com.hello2morrow.sonargraph.integration.access.model.ISoftwareSystem;

class SonargraphMetricsProvider
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SonargraphMetricsProvider.class);

    static final String PROPERTIES_FILENAME = "SonargraphMetrics.properties";
    private static final String BUILT_IN_METRICS_RESOURCE_PATH = "/com/hello2morrow/sonargraph/integration/sonarqube/" + PROPERTIES_FILENAME;

    private static final char SEPARATOR = '|';
    private static final String INT = "INT";
    private static final String FLOAT = "FLOAT";

    private static final int NUBMER_OF_VALUE_PARTS = 5;
    private static final int NUMBER_OF_KEY_PARTS = 2;

    private final String m_customMetricsDirectoryPath;

    SonargraphMetricsProvider()
    {
        this(System.getProperty("user.home") + "/." + SonargraphBase.SONARGRAPH_PLUGIN_KEY);
    }

    SonargraphMetricsProvider(final String customMetricsDirectoryPath)
    {
        m_customMetricsDirectoryPath = customMetricsDirectoryPath;
    }

    String getDirectory()
    {
        return m_customMetricsDirectoryPath;
    }

    String getFilePath()
    {
        return getDirectory() + "/" + PROPERTIES_FILENAME;
    }

    void addMetric(final IMetricId metricId, final Properties metrics)
    {
        final String definition = createMetricDefinition(metricId);
        metrics.put(metricId.getName(), definition);
    }

    void addCustomMetric(final ISoftwareSystem softwareSystem, final IMetricId metricId, final Properties customMetrics)
    {
        final String metricKey = softwareSystem.getName() + SEPARATOR + metricId.getName();
        final String definition = createMetricDefinition(metricId);
        customMetrics.put(metricKey, definition);
    }

    private String createMetricDefinition(final IMetricId metricId)
    {
        final StringJoiner joiner = new StringJoiner(SEPARATOR + "");
        joiner.add(metricId.getPresentationName());
        joiner.add((metricId.isFloat() ? FLOAT : INT));
        joiner.add(Double.toString(metricId.getBest()));
        joiner.add(Double.toString(metricId.getWorst()));
        joiner.add(SonargraphBase.trimDescription(metricId.getDescription()));
        return joiner.toString();
    }

    static String createCustomMetricKeyFromStandardName(final String softwareSystemIdentifier, final String metricIdName)
    {
        String customMetricKey = SonargraphBase.METRIC_ID_PREFIX + softwareSystemIdentifier + "."
                + Utility.convertMixedCaseStringToConstantName(metricIdName).replace(" ", "");
        customMetricKey = customMetricKey.replace(SEPARATOR, ' ');
        return customMetricKey;
    }

    static String createMetricKeyFromStandardName(final String metricIdName)
    {
        String metricKey = SonargraphBase.METRIC_ID_PREFIX + Utility.convertMixedCaseStringToConstantName(metricIdName).replace(" ", "");
        metricKey = metricKey.replace(SEPARATOR, ' ');
        return metricKey;
    }

    List<Metric<Serializable>> loadStandardMetrics()
    {
        final Properties standardMetrics = loadStandardMetricProperties();
        return getStandardMetrics(standardMetrics);
    }

    private Properties loadStandardMetricProperties()
    {
        final Properties standardMetrics = new Properties();
        try (InputStream inputStream = SonargraphBase.class.getResourceAsStream(BUILT_IN_METRICS_RESOURCE_PATH))
        {
            standardMetrics.load(inputStream);
            LOGGER.info("{}: Loaded standard metrics file '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, BUILT_IN_METRICS_RESOURCE_PATH);
        }
        catch (final IOException e)
        {
            LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Unable to load standard metrics file '"
                    + BUILT_IN_METRICS_RESOURCE_PATH + "'", e);
        }

        return standardMetrics;
    }

    List<Metric<Serializable>> getStandardMetrics(final Properties metricProperties)
    {
        if (metricProperties.isEmpty())
        {
            return Collections.emptyList();
        }

        final List<Metric<Serializable>> metrics = new ArrayList<>(metricProperties.size());
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
                    final String nextMetricKey = createMetricKeyFromStandardName(nextMetricIdName);
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

                    metrics.add(builder.create());
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

    List<Metric<Serializable>> getCustomMetrics()
    {
        final Properties customMetrics = loadCustomMetrics();
        return getCustomMetrics(customMetrics);
    }

    Properties loadCustomMetrics()
    {
        final Properties customMetrics = new Properties();
        final String propertiesFilePath = getFilePath();
        final File file = new File(propertiesFilePath);
        if (!file.exists())
        {
            LOGGER.debug("{}: Deprecated custom metrics file '{}' does not exist.", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                    propertiesFilePath);
        }
        else
        {

            try (FileInputStream fis = new FileInputStream(file))
            {
                customMetrics.load(fis);
                LOGGER.info("{}: Loaded custom metrics file '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, propertiesFilePath);
            }
            catch (final IOException e)
            {
                LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Unable to load custom metrics file '" + propertiesFilePath + "'",
                        e);
            }
        }

        final File propertiesDirectory = new File(getDirectory());
        if (!propertiesDirectory.exists())
        {
            return customMetrics;
        }

        final File[] filesList = propertiesDirectory.listFiles();

        int counter = 0;
        if (filesList != null)
        {
            for (final File nextFile : filesList)
            {
                if (nextFile.isDirectory() || !nextFile.getName().endsWith(".properties"))
                {
                    continue;
                }

                try (FileInputStream fis = new FileInputStream(nextFile))
                {
                    customMetrics.load(fis);
                    LOGGER.info("{}: Loaded custom metrics file '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                            nextFile.getAbsolutePath());
                    counter++;
                }
                catch (final IOException e)
                {
                    LOGGER.error(
                            SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Unable to load custom metrics file '" + propertiesFilePath + "'",
                            e);
                }
            }
        }
        if (counter == 0)
        {
            LOGGER.info("No custom metric properties files found in directory {}", propertiesDirectory.getAbsolutePath());
        }
        else
        {
            LOGGER.info("Loaded {} custom metric properties files from directory {}", counter, propertiesDirectory.getAbsolutePath());
        }

        return customMetrics;
    }

    List<Metric<Serializable>> getCustomMetrics(final Properties customMetrics)
    {
        if (customMetrics.isEmpty())
        {
            return Collections.emptyList();
        }

        final List<Metric<Serializable>> metrics = new ArrayList<>(customMetrics.size());
        for (final Entry<Object, Object> nextEntry : customMetrics.entrySet())
        {
            String notCreatedInfo = null;
            final String nextKey = SonargraphBase.getNonEmptyString(nextEntry.getKey());
            final String nextValue = SonargraphBase.getNonEmptyString(nextEntry.getValue());

            try
            {
                final String[] nextSplitKey = nextKey.split("\\" + SEPARATOR);
                final String[] nextSplitValue = nextValue.split("\\" + SEPARATOR);

                if (nextSplitKey.length == NUMBER_OF_KEY_PARTS && nextSplitValue.length == NUBMER_OF_VALUE_PARTS)
                {
                    final String nextSoftwareSystemName = nextSplitKey[0];
                    final String nextMetricIdName = nextSplitKey[1];

                    final String nextMetricKey = createCustomMetricKeyFromStandardName(nextSoftwareSystemName, nextMetricIdName);
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
                LOGGER.warn("{}: {}", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, notCreatedInfo);
            }
        }

        return metrics;
    }

    File saveCustomMetrics(final Properties customMetrics, final String systemId, final String systemName) throws IOException
    {
        final File targetDirectory = new File(getDirectory());
        targetDirectory.mkdirs();
        return saveMetricProperties(customMetrics, targetDirectory, systemId + ".properties", "Custom metrics file for system " + systemName);
    }

    File saveMetricProperties(final Properties metrics, final File targetDirectory, final String fileName, final String comment) throws IOException
    {
        final File propertiesFile = new File(targetDirectory, fileName);
        try (FileWriter writer = new FileWriter(propertiesFile))
        {
            metrics.store(writer, comment);
        }
        return propertiesFile;
    }
}