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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sonar.api.measures.Metric;
import org.sonar.api.measures.Metrics;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import com.hello2morrow.sonargraph.integration.access.model.IExportMetaData;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;
import com.hello2morrow.sonargraph.integration.access.model.IMetricLevel;

public final class SonargraphMetrics implements Metrics
{
    private static final Logger LOGGER = Loggers.get(SonargraphMetrics.class);

    //    private static IExportMetaData loadAdditionalMetaData(final IMetaDataController controller, final String directory)
    //    {
    //        assert controller != null : "Parameter 'controller' of method 'loadAdditionalMetaData' must not be null";
    //        assert directory != null && directory.length() > 0 : "Parameter 'directory' of method 'loadAdditionalMetaData' must not be empty";
    //
    //        final File configurationDir = new File(directory);
    //        if (!configurationDir.exists() || !configurationDir.isDirectory())
    //        {
    //            LOGGER.error("Cannot load meta-data from directory '{}'. It does not exist.", directory);
    //            return null;
    //        }
    //
    //        final List<File> files = Arrays.asList(configurationDir.listFiles()).stream().filter(f -> !f.isDirectory() && f.getName().endsWith(".xml"))
    //                .collect(Collectors.toList());
    //        if (!files.isEmpty())
    //        {
    //            try
    //            {
    //                final ResultWithOutcome<IMergedExportMetaData> result = controller.mergeExportMetaDataFiles(files);
    //                if (result.isSuccess())
    //                {
    //                    return result.getOutcome();
    //                }
    //                LOGGER.error("Failed to load configuration from '{}': {}", directory, result.toString());
    //            }
    //            catch (final Exception ex)
    //            {
    //                LOGGER.error("Failed to load configuration from '{}'", directory, ex);
    //            }
    //        }
    //        return null;
    //    }

    public SonargraphMetrics()
    {
        super();
    }

    //    @Override
    //    public void define(final Context context)
    //    {
    //        assert context != null : "Parameter 'context' of method 'define' must not be null";
    //        readBuiltInMetaData();
    //
    //        if (builtInMetaData == null)
    //        {
    //            return;
    //        }
    //
    //        //        IExportMetaData additionalMetaData = null;
    //        //        final Optional<String> configuredMetaDataPathOptional = configuration.get(SonargraphBase.METADATA_PATH);
    //        //        if (configuredMetaDataPathOptional.isPresent())
    //        //        {
    //        //            final String configuredMetaDataPath = configuredMetaDataPathOptional.get().trim();
    //        //            if (!configuredMetaDataPath.isEmpty())
    //        //            {
    //        //                additionalMetaData = loadAdditionalMetaData(controller, configuredMetaDataPath);
    //        //            }
    //        //        }
    //
    //        final NewRepository repository = context.createRepository(SonargraphBase.SONARGRAPH_PLUGIN_KEY, SonargraphBase.JAVA)
    //                .setName(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
    //
    //        final Set<IIssueType> builtInIssueTypes = new HashSet<>(builtInMetaData.getIssueTypes().values());
    //        for (final IIssueType nextIssueType : builtInIssueTypes)
    //        {
    //            if (!SonargraphBase.ignoreIssueType(nextIssueType))
    //            {
    //                createRule(nextIssueType, repository);
    //            }
    //        }
    //
    //        createRule(SonargraphBase.createRuleKey(SonargraphBase.SCRIPT_ISSUE_NAME),
    //                SonargraphBase.createRuleName(SonargraphBase.SCRIPT_ISSUE_PRESENTATION_NAME),
    //                SonargraphBase.createRuleCategoryTag(SonargraphBase.SCRIPT_ISSUE_CATEGORY_PRESENTATION_NAME), Severity.MINOR,
    //                "Description '" + SonargraphBase.SCRIPT_ISSUE_PRESENTATION_NAME + "', category '"
    //                        + SonargraphBase.SCRIPT_ISSUE_CATEGORY_PRESENTATION_NAME + "'",
    //                repository);
    //
    //        //        if (additionalMetaData != null)
    //        //        {
    //        //            for (final IIssueType nextIssueType : additionalMetaData.getIssueTypes().values())
    //        //            {
    //        //                if (!SonargraphBase.ignoreIssueType(nextIssueType) && !builtInIssueTypes.contains(nextIssueType))
    //        //                {
    //        //                    //Only add additional issue types
    //        //                    createRule(nextIssueType, repository);
    //        //                }
    //        //            }
    //        //        }
    //
    //        repository.done();
    //        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Created " + repository.rules().size() + " predefined rule(s)");
    //    }

    private void getMetricsForLevel(final IExportMetaData builtInMetaData, final IMetricLevel level, final Map<String, IMetricId> metricMap)
    {
        assert builtInMetaData != null : "Parameter 'builtInMetaData' of method 'getMetricsForLevel' must not be null";
        assert level != null : "Parameter 'level' of method 'getMetricsForLevel' must not be null";
        assert metricMap != null : "Parameter 'metricMap' of method 'getMetricsForLevel' must not be null";

        for (final IMetricId next : builtInMetaData.getMetricIdsForLevel(level))
        {
            if (!metricMap.containsKey(next.getName()))
            {
                metricMap.put(next.getName(), next);
            }
        }
    }

    private String trimDescription(final IMetricId id)
    {
        assert id != null : "Parameter 'id' of method 'trimDescription' must not be null";
        final String description = id.getDescription();
        return description.length() > 255 ? description.substring(0, 252) + "..." : description;
    }

    private void setMetricDirection(final IMetricId id, final Metric.Builder metric)
    {
        assert id != null : "Parameter 'id' of method 'setMetricDirection' must not be null";
        assert metric != null : "Parameter 'metric' of method 'setMetricDirection' must not be null";

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
        assert id != null : "Parameter 'id' of method 'setWorstValue' must not be null";
        assert metric != null : "Parameter 'metric' of method 'setWorstValue' must not be null";

        if (!id.getWorstValue().equals(Double.NaN) && !id.getWorstValue().equals(Double.POSITIVE_INFINITY)
                && !id.getWorstValue().equals(Double.NEGATIVE_INFINITY))
        {
            metric.setWorstValue(id.getWorstValue());
        }
    }

    private void setBestValue(final IMetricId id, final Metric.Builder metric)
    {
        assert id != null : "Parameter 'id' of method 'setBestValue' must not be null";
        assert metric != null : "Parameter 'metric' of method 'setBestValue' must not be null";

        if (!id.getBestValue().equals(Double.NaN) && !id.getBestValue().equals(Double.POSITIVE_INFINITY)
                && !id.getBestValue().equals(Double.NEGATIVE_INFINITY))
        {
            metric.setBestValue(id.getBestValue());
        }
    }

    private Metric<Serializable> createMetric(final IMetricId metricId)
    {
        assert metricId != null : "Parameter 'metricId' of method 'createMetric' must not be null";

        final Metric.Builder metric = new Metric.Builder(SonargraphBase.createMetricKeyFromStandardName(metricId.getName()),
                metricId.getPresentationName(), metricId.isFloat() ? Metric.ValueType.FLOAT : Metric.ValueType.INT)
                        .setDescription(trimDescription(metricId)).setDomain(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);

        setBestValue(metricId, metric);
        setWorstValue(metricId, metric);
        setMetricDirection(metricId, metric);

        return metric.create();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<Metric> getMetrics()
    {
        final IExportMetaData builtInMetaData = SonargraphBase.readBuiltInMetaData();
        if (builtInMetaData == null)
        {
            return Collections.emptyList();
        }

        final Map<String, IMetricId> metricNameToId = new HashMap<>();

        final IMetricLevel systemMetricLevel = builtInMetaData.getMetricLevels().get(IMetricLevel.SYSTEM);
        assert systemMetricLevel != null : "'systemMetricLevel' of method 'getMetrics' must not be null";
        getMetricsForLevel(builtInMetaData, systemMetricLevel, metricNameToId);

        final IMetricLevel moduleMetricLevel = builtInMetaData.getMetricLevels().get(IMetricLevel.MODULE);
        assert moduleMetricLevel != null : "'moduleMetricLevel' of method 'getMetrics' must not be null";
        getMetricsForLevel(builtInMetaData, moduleMetricLevel, metricNameToId);

        final List<Metric<? extends Serializable>> metrics = new ArrayList<>(metricNameToId.size());
        metricNameToId.values().forEach(i -> metrics.add(createMetric(i)));
        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Created " + metrics.size() + "system/module metric(s)");

        return Collections.unmodifiableList(metrics);
    }
}