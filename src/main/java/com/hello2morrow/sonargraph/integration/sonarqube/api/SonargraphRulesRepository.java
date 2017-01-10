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
package com.hello2morrow.sonargraph.integration.sonarqube.api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.config.Settings;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.Metrics;
import org.sonar.api.rule.Severity;
import org.sonar.api.server.rule.RulesDefinition;

import com.hello2morrow.sonargraph.integration.access.controller.ControllerFactory;
import com.hello2morrow.sonargraph.integration.access.controller.IMetaDataController;
import com.hello2morrow.sonargraph.integration.access.foundation.OperationResultWithOutcome;
import com.hello2morrow.sonargraph.integration.access.model.IExportMetaData;
import com.hello2morrow.sonargraph.integration.access.model.IIssueType;
import com.hello2morrow.sonargraph.integration.access.model.IMergedExportMetaData;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;
import com.hello2morrow.sonargraph.integration.access.model.IMetricLevel;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphPluginBase;

public final class SonargraphRulesRepository implements RulesDefinition, Metrics
{
    private static final Logger LOG = LoggerFactory.getLogger(SonargraphRulesRepository.class);
    private static final String DEFAULT_META_DATA_PATH = "/com/hello2morrow/sonargraph/integration/sonarqube/ExportMetaData.xml";
    private static final String RULE_TAG_SONARGRAPH = "sonargraph-integration";

    private List<Metric<? extends Serializable>> metrics;
    private String configuredMetaDataPath;

    private final Settings settings;
    private final String defaultMetaDataPath;

    public SonargraphRulesRepository(final Settings settings)
    {
        this(settings, DEFAULT_META_DATA_PATH);
    }

    SonargraphRulesRepository(final Settings settings, final String defaultMetaDataPath)
    {
        super();
        this.settings = settings;
        this.defaultMetaDataPath = defaultMetaDataPath;
        configuredMetaDataPath = defaultMetaDataPath;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<Metric> getMetrics()
    {
        final String metaDataConfigurationPath = getMetaDataPath(settings);

        if (metrics != null)
        {
            if (!configurationChanged())
            {
                return Collections.unmodifiableList(metrics);
            }
            LOG.info("Configured path for meta-data changed from '{}' to '{}'. Reloading metric configuration.", configuredMetaDataPath,
                    metaDataConfigurationPath);
        }

        final IMetaDataController controller = new ControllerFactory().createMetaDataController();
        final Optional<IExportMetaData> metaDataOptional = loadMetaDataForConfiguration(controller, metaDataConfigurationPath);
        if (!metaDataOptional.isPresent())
        {
            LOG.error("Failed to load configuration for Sonargraph plugin");
            return Collections.emptyList();
        }

        metrics = new ArrayList<>();
        final IExportMetaData metaData = metaDataOptional.get();
        final Map<String, IMetricLevel> metricLevels = metaData.getMetricLevels();

        final Map<String, IMetricId> metricMap = new HashMap<>();

        //We are currently only interested in metrics on System and Module levels
        getMetricsForLevel(metaData, metricLevels.get(IMetricLevel.SYSTEM), metricMap);
        getMetricsForLevel(metaData, metricLevels.get(IMetricLevel.MODULE), metricMap);

        for (final Map.Entry<String, IMetricId> nextEntry : metricMap.entrySet())
        {
            final IMetricId next = nextEntry.getValue();
            final Metric.Builder metric = new Metric.Builder(SonargraphMetrics.createMetricKeyFromStandardName(next.getName()),
                    next.getPresentationName(), next.isFloat() ? Metric.ValueType.FLOAT : Metric.ValueType.INT).setDescription(trimDescription(next))
                    .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH);

            setBestValue(next, metric);
            setWorstValue(next, metric);
            setMetricDirection(next, metric);
            metrics.add(metric.create());
        }
        //Additional metrics for structural debt widget
        metrics.add(SonargraphMetrics.STRUCTURAL_DEBT_COST);

        metrics.add(SonargraphMetrics.CURRENT_VIRTUAL_MODEL);

        metrics.add(SonargraphMetrics.VIRTUAL_MODEL_FEATURE_AVAILABLE);
        metrics.add(SonargraphMetrics.NUMBER_OF_TASKS);
        metrics.add(SonargraphMetrics.NUMBER_OF_UNAPPLICABLE_TASKS);

        metrics.add(SonargraphMetrics.NUMBER_OF_RESOLUTIONS);
        metrics.add(SonargraphMetrics.NUMBER_OF_UNAPPLICABLE_RESOLUTIONS);

        metrics.add(SonargraphMetrics.NUMBER_OF_REFACTORINGS);
        metrics.add(SonargraphMetrics.NUMBER_OF_UNAPPLICABLE_REFACTORINGS);

        metrics.add(SonargraphMetrics.NUMBER_OF_PARSER_DEPENDENCIES_AFFECTED_BY_REFACTORINGS);

        //Additional metrics for structure widget
        metrics.add(SonargraphMetrics.CYCLIC_PACKAGES_PERCENT);
        metrics.add(SonargraphMetrics.MAX_MODULE_NCCD);

        //Additional metrics for architecture widget
        metrics.add(SonargraphMetrics.ARCHITECTURE_FEATURE_AVAILABLE);
        metrics.add(SonargraphMetrics.NUMBER_OF_ISSUES);
        metrics.add(SonargraphMetrics.NUMBER_OF_CRITICAL_ISSUES_WITHOUT_RESOLUTION);

        metrics.add(SonargraphMetrics.VIOLATING_COMPONENTS_PERCENT);
        metrics.add(SonargraphMetrics.UNASSIGNED_COMPONENTS_PERCENT);

        metrics.add(SonargraphMetrics.NUMBER_OF_THRESHOLD_VIOLATIONS);
        metrics.add(SonargraphMetrics.NUMBER_OF_WORKSPACE_WARNINGS);
        metrics.add(SonargraphMetrics.NUMBER_OF_IGNORED_CRITICAL_ISSUES);

        return Collections.unmodifiableList(metrics);
    }

    private void setMetricDirection(final IMetricId id, final Metric.Builder metric)
    {
        if (id.getBestValue() > id.getWorstValue())
        {
            metric.setDirection(Metric.DIRECTION_BETTER);
        }
        else if (id.getBestValue() < id.getWorstValue())
        {
            metric.setDirection(Metric.DIRECTION_WORST);
        }
        else
        {
            metric.setDirection(Metric.DIRECTION_NONE);
        }
    }

    private void setWorstValue(final IMetricId id, final Metric.Builder metric)
    {
        if (!id.getWorstValue().equals(Double.NaN) && !id.getWorstValue().equals(Double.POSITIVE_INFINITY)
                && !id.getWorstValue().equals(Double.NEGATIVE_INFINITY))
        {
            metric.setWorstValue(id.getWorstValue());
        }
    }

    private void setBestValue(final IMetricId id, final Metric.Builder metric)
    {
        if (!id.getBestValue().equals(Double.NaN) && !id.getBestValue().equals(Double.POSITIVE_INFINITY)
                && !id.getBestValue().equals(Double.NEGATIVE_INFINITY))
        {
            metric.setBestValue(id.getBestValue());
        }
    }

    private static void getMetricsForLevel(final IExportMetaData metaData, final IMetricLevel level, final Map<String, IMetricId> metricMap)
    {
        for (final IMetricId next : metaData.getMetricIdsForLevel(level))
        {
            if (!metricMap.containsKey(next.getName()))
            {
                metricMap.put(next.getName(), next);
            }
        }
    }

    private boolean configurationChanged()
    {
        final String metaDataConfigurationPath = getMetaDataPath(settings);
        if (configuredMetaDataPath.equals(metaDataConfigurationPath))
        {
            //All good - nothing to be done.
            return false;
        }
        else if ((metaDataConfigurationPath == null || metaDataConfigurationPath.trim().length() == 0)
                && configuredMetaDataPath.equals(DEFAULT_META_DATA_PATH))
        {
            //configuration did not change - still default
            return false;
        }
        LOG.info("Configured path for meta-data changed from '{}' to '{}'. Reloading metric configuration.", configuredMetaDataPath,
                metaDataConfigurationPath);
        return true;
    }

    @Override
    public void define(final Context context)
    {
        final IMetaDataController controller = new ControllerFactory().createMetaDataController();
        final String metaDataConfigurationPath = getMetaDataPath(settings);
        final Optional<IExportMetaData> result = loadMetaDataForConfiguration(controller, metaDataConfigurationPath);

        if (!result.isPresent())
        {
            LOG.error("Failed to load configuration for Sonargraph repository from '{}'", metaDataConfigurationPath);
            return;
        }

        final NewRepository repository = context.createRepository(SonargraphPluginBase.PLUGIN_KEY, org.sonar.plugins.java.Java.KEY).setName(
                SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + " Rules");
        final IExportMetaData metaData = result.get();

        for (final Map.Entry<String, IIssueType> entry : metaData.getIssueTypes().entrySet())
        {
            final IIssueType type = entry.getValue();
            final NewRule rule = repository.createRule(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics
                    .createRuleKey(type.getName()));
            rule.setName(SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + type.getPresentationName());
            final String description = "Description '" + (type.getDescription().length() > 0 ? type.getDescription() : type.getPresentationName())
                    + "', category '" + type.getCategory().getPresentationName() + "'";
            final List<String> tags = new ArrayList<>(Arrays.asList(RULE_TAG_SONARGRAPH, type.getCategory().getName().toLowerCase()));

            //currently, there is no direct link between an issueType and its provider
            rule.setHtmlDescription(description);
            rule.addTags(tags.toArray(new String[] {}));
            rule.setSeverity(convertSeverity(type.getSeverity()));
        }

        repository.done();
    }

    private Optional<IExportMetaData> loadMetaDataForConfiguration(final IMetaDataController controller, final String metaDataConfigurationPath)
    {
        if (metaDataConfigurationPath != null && metaDataConfigurationPath.trim().length() > 0)
        {
            final Optional<IExportMetaData> result = loadConfigurationDataFromPath(controller, metaDataConfigurationPath);
            if (result.isPresent())
            {
                configuredMetaDataPath = metaDataConfigurationPath;
                return result;
            }
            LOG.warn("Failed to load configuration for Sonargraph plugin. Continue with default configuration.");
        }

        return loadDefaultConfigurationDataFromPlugin(controller);
    }

    private static String getMetaDataPath(final Settings settings)
    {
        return settings.getString(SonargraphPluginBase.METADATA_PATH);
    }

    private Optional<IExportMetaData> loadDefaultConfigurationDataFromPlugin(final IMetaDataController controller)
    {
        final String errorMsg = "Failed to load default configuration for Sonargraph Plugin from '" + defaultMetaDataPath + "'";
        try (InputStream inputStream = SonargraphRulesRepository.class.getResourceAsStream(defaultMetaDataPath))
        {
            if (inputStream == null)
            {
                LOG.error(errorMsg);
                return Optional.empty();
            }

            final OperationResultWithOutcome<IExportMetaData> result = controller.loadExportMetaData(inputStream, defaultMetaDataPath);
            if (result.isFailure())
            {
                if (LOG.isErrorEnabled())
                {
                    LOG.error("{}: {}", errorMsg, result.toString());
                }
                return Optional.empty();
            }
            configuredMetaDataPath = defaultMetaDataPath;
            return Optional.of(result.getOutcome());
        }
        catch (final IOException ex)
        {
            LOG.error(errorMsg, ex);
        }
        return Optional.empty();
    }

    private static Optional<IExportMetaData> loadConfigurationDataFromPath(final IMetaDataController controller,
            final String metaDataConfigurationPath)
    {
        final File configurationDir = new File(metaDataConfigurationPath);
        if (!configurationDir.exists() || !configurationDir.isDirectory())
        {
            LOG.error("Cannot load meta-data from directory '{}'. It does not exist.", metaDataConfigurationPath);
            return Optional.empty();
        }

        final List<File> files = Arrays.asList(configurationDir.listFiles()).stream().filter(f -> !f.isDirectory()).collect(Collectors.toList());
        if (!files.isEmpty())
        {
            try
            {
                final OperationResultWithOutcome<IMergedExportMetaData> result = controller.mergeExportMetaDataFiles(files);
                if (result.isSuccess())
                {
                    return Optional.ofNullable(result.getOutcome());
                }
                if (LOG.isErrorEnabled())
                {
                    LOG.error("Failed to load configuration from '{}': {}", metaDataConfigurationPath, result.toString());
                }
            }
            catch (final Exception ex)
            {
                LOG.error("Failed to load configuration from '{}'", metaDataConfigurationPath, ex);
            }
        }
        return Optional.empty();
    }

    void clearLoadedMetrics()
    {
        metrics = null;
        configuredMetaDataPath = null;
    }

    @SuppressWarnings("rawtypes")
    public Map<String, Metric> getLoadedMetrics()
    {
        if (metrics == null)
        {
            LOG.error("No metric definitions have been loaded yet");
            return Collections.emptyMap();
        }
        final Map<String, Metric> copy = new HashMap<>(metrics.size());
        for (final Metric next : metrics)
        {
            copy.put(next.getKey(), next);
        }

        return Collections.unmodifiableMap(copy);
    }

    private static String trimDescription(final IMetricId id)
    {
        final String description = id.getDescription();
        if (description.length() > 255)
        {
            return description.substring(0, 252) + "...";
        }
        return description;
    }

    private String convertSeverity(final com.hello2morrow.sonargraph.integration.access.model.Severity severity)
    {
        assert severity != null : "Parameter 'severity' of method 'convertSeverity' must not be null";

        String sonarQubeSeverity;

        switch (severity)
        {
        case ERROR:
            sonarQubeSeverity = Severity.MAJOR;
            break;
        case WARNING:
            sonarQubeSeverity = Severity.MINOR;
            break;
        case NONE:
            //$FALL-THROUGH$
        case INFO:
            sonarQubeSeverity = Severity.INFO;
            break;
        default:
            sonarQubeSeverity = Severity.MINOR;
        }

        return sonarQubeSeverity;
    }
}