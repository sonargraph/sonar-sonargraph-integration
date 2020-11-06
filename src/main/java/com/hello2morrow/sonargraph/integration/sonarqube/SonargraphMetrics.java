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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sonar.api.measures.Metric;
import org.sonar.api.measures.Metrics;
import org.sonar.api.server.ServerSide;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

@ServerSide
public final class SonargraphMetrics implements Metrics
{
    private static final Logger LOGGER = Loggers.get(SonargraphMetrics.class);
    private List<Metric<Serializable>> metrics;
    private final SonargraphMetricsProvider metricProvider;

    public SonargraphMetrics()
    {
        this(new SonargraphMetricsProvider());
    }

    /** Test support */
    public SonargraphMetrics(final SonargraphMetricsProvider metricsPropertiesProvider)
    {
        this.metricProvider = metricsPropertiesProvider;
    }

    SonargraphMetricsProvider getMetricsProvider()
    {
        return metricProvider;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<Metric> getMetrics()
    {
        if (metrics == null)
        {
            final Map<String, Metric<Serializable>> standardMetrics = metricProvider.loadStandardMetrics();
            final Map<String, Metric<Serializable>> customMetrics = metricProvider.loadCustomMetrics();

            final Map<String, Metric<Serializable>> result = new HashMap<>(standardMetrics);
            int customMetricCounter = 0;
            int omittedCustomMetricCounter = 0;
            for (final Map.Entry<String, Metric<Serializable>> nextCustom : customMetrics.entrySet())
            {
                if (standardMetrics.containsKey(nextCustom.getKey()))
                {
                    LOGGER.warn("Omitting custom metric with key '{}' because same metric key is used for standard metric!", nextCustom.getKey());
                    omittedCustomMetricCounter++;
                }
                else
                {
                    result.put(nextCustom.getKey(), nextCustom.getValue());
                    customMetricCounter++;
                }
            }

            metrics = new ArrayList<>(result.values());
            if (omittedCustomMetricCounter == 0)
            {
                LOGGER.info("{}: Created {} predefined and {} custom metric(s)", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                        standardMetrics.size(), customMetricCounter);
            }
            else
            {
                LOGGER.info("{}: Created {} predefined and {} custom metric(s). Omitted {} custom metrics.",
                        SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, standardMetrics.size(), customMetricCounter, omittedCustomMetricCounter);
            }
        }

        return Collections.unmodifiableList(metrics);
    }
}