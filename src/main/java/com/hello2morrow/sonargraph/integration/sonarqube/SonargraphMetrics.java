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
    private List<Metric<Serializable>> metrics;
    private final CustomMetricsProvider customMetricsPropertiesProvider;

    public SonargraphMetrics()
    {
        this(new CustomMetricsProvider());
    }

    /** Test support */
    SonargraphMetrics(final CustomMetricsProvider customMetricsPropertiesProvider)
    {
        this.customMetricsPropertiesProvider = customMetricsPropertiesProvider;
    }

    private void getMetricsForLevel(final IExportMetaData builtInMetaData, final IMetricLevel level, final Map<String, IMetricId> metricMap)
    {
        for (final IMetricId next : builtInMetaData.getMetricIdsForLevel(level))
        {
            if (!metricMap.containsKey(next.getName()))
            {
                metricMap.put(next.getName(), next);
            }
        }
    }

    CustomMetricsProvider getCustomMetricsProvider()
    {
        return customMetricsPropertiesProvider;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<Metric> getMetrics()
    {
        if (metrics == null)
        {
            final IExportMetaData builtInMetaData = SonargraphBase.readBuiltInMetaData();
            final Map<String, IMetricId> predefinedMetrics = new HashMap<>();
            getMetricsForLevel(builtInMetaData, builtInMetaData.getMetricLevels().get(IMetricLevel.SYSTEM), predefinedMetrics);
            getMetricsForLevel(builtInMetaData, builtInMetaData.getMetricLevels().get(IMetricLevel.MODULE), predefinedMetrics);
            final List<Metric<Serializable>> customMetrics = customMetricsPropertiesProvider.getCustomMetrics();
            metrics = new ArrayList<>(predefinedMetrics.size() + customMetrics.size());
            predefinedMetrics.values().forEach(metricId -> metrics.add(SonargraphBase.createMetric(metricId)));
            customMetrics.forEach(c -> metrics.add(c));

            LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Created " + predefinedMetrics.size() + " predefined and "
                    + customMetrics.size() + " custom metric(s)");
        }

        return Collections.unmodifiableList(metrics);
    }
}