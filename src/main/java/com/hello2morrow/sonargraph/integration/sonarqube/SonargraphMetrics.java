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
import java.util.List;

import org.sonar.api.measures.Metric;
import org.sonar.api.measures.Metrics;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public final class SonargraphMetrics implements Metrics
{
    private static final Logger LOGGER = Loggers.get(SonargraphMetrics.class);
    private List<Metric<Serializable>> metrics;
    private final SonargraphMetricsProvider metricPropertiesProvider;

    public SonargraphMetrics()
    {
        this(new SonargraphMetricsProvider());
    }

    /** Test support */
    SonargraphMetrics(final SonargraphMetricsProvider metricsPropertiesProvider)
    {
        this.metricPropertiesProvider = metricsPropertiesProvider;
    }

    SonargraphMetricsProvider getMetricsProvider()
    {
        return metricPropertiesProvider;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<Metric> getMetrics()
    {
        if (metrics == null)
        {
            final List<Metric<Serializable>> standardMetrics = metricPropertiesProvider.loadStandardMetrics();
            final List<Metric<Serializable>> customMetrics = metricPropertiesProvider.getCustomMetrics();

            metrics = new ArrayList<>(standardMetrics.size() + customMetrics.size());
            metrics.addAll(standardMetrics);
            metrics.addAll(customMetrics);

            LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Created " + standardMetrics.size() + " predefined and "
                    + customMetrics.size() + " custom metric(s)");
        }

        return Collections.unmodifiableList(metrics);
    }
}