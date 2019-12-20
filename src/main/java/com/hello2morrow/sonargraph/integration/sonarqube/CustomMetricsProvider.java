package com.hello2morrow.sonargraph.integration.sonarqube;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
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

class CustomMetricsProvider
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomMetricsProvider.class);

    private static final String METRICS_PROPERTIES_FILENAME = "metrics.properties";
    private static final char CUSTOM_METRIC_SEPARATOR = '|';

    private static final String CUSTOM_METRIC_INT = "INT";
    private static final String CUSTOM_METRIC_FLOAT = "FLOAT";

    private static final String pluginKey = SonargraphBase.SONARGRAPH_PLUGIN_KEY;
    private static final String metricIdPrefix = SonargraphBase.METRIC_ID_PREFIX;

    public CustomMetricsProvider()
    {
        super();
    }

    public String getDirectory()
    {
        return System.getProperty("user.home") + "/." + pluginKey;
    }

    public String getFilePath()
    {
        return getDirectory() + "/" + METRICS_PROPERTIES_FILENAME;
    }

    void addCustomMetric(final ISoftwareSystem softwareSystem, final IMetricId metricId, final Properties customMetrics)
    {
        final String metricKey = softwareSystem.getName() + CUSTOM_METRIC_SEPARATOR + metricId.getName();
        final String definition = createMetricDefinition(metricId);
        customMetrics.put(metricKey, definition);
    }

    private String createMetricDefinition(final IMetricId metricId)
    {
        final StringJoiner joiner = new StringJoiner(CUSTOM_METRIC_SEPARATOR + "");
        joiner.add(metricId.getPresentationName());
        joiner.add((metricId.isFloat() ? CUSTOM_METRIC_FLOAT : CUSTOM_METRIC_INT));
        joiner.add(metricId.getBestValue().toString());
        joiner.add(metricId.getWorstValue().toString());
        joiner.add(SonargraphBase.trimDescription(metricId.getDescription()));
        return joiner.toString();
    }

    static String createCustomMetricKeyFromStandardName(final String softwareSystemName, final String metricIdName)
    {
        String customMetricKey = metricIdPrefix + softwareSystemName + "."
                + Utility.convertMixedCaseStringToConstantName(metricIdName).replace(" ", "");
        customMetricKey = customMetricKey.replace(CUSTOM_METRIC_SEPARATOR, ' ');
        return customMetricKey;
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
                LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + notCreatedInfo);
            }
        }

        return metrics;
    }

    List<Metric<Serializable>> getCustomMetrics()
    {
        final Properties customMetrics = loadCustomMetrics();
        final List<Metric<Serializable>> metrics = getCustomMetrics(customMetrics);
        return metrics;
    }

    static String getNonEmptyString(final Object input)
    {
        if (input instanceof String && !((String) input).isEmpty())
        {
            return (String) input;
        }
        throw new IllegalArgumentException("Empty input");
    }

    Properties loadCustomMetrics()
    {
        final Properties customMetrics = new Properties();

        final String propertiesFilePath = getFilePath();
        try (FileInputStream fis = new FileInputStream(new File(propertiesFilePath)))
        {
            customMetrics.load(fis);
            LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Loaded custom metrics file '" + propertiesFilePath + "'");
        }
        catch (final FileNotFoundException e)
        {
            LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Custom metrics file '" + propertiesFilePath + "' not found");
        }
        catch (final IOException e)
        {
            LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Unable to load custom metrics file '" + propertiesFilePath + "'", e);
        }

        return customMetrics;
    }

    void save(final Properties customMetrics)
    {
        final File directory = new File(getDirectory());
        directory.mkdirs();
        save(customMetrics, directory);
    }

    void save(final Properties customMetrics, final File targetDirectory)
    {
        final File propertiesFile = new File(targetDirectory, METRICS_PROPERTIES_FILENAME);
        try (FileWriter writer = new FileWriter(propertiesFile))
        {
            customMetrics.store(writer, "Custom Metrics");
            LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Custom metrics file '" + propertiesFile.getAbsolutePath()
                    + "' updated, the SonarQube server needs to be restarted");
        }
        catch (final IOException e)
        {
            LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Unable to save custom metrics file '"
                    + propertiesFile.getAbsolutePath() + "'", e);
        }
    }
}