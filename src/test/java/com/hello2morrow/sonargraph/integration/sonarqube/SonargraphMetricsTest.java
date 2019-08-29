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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.List;

import org.junit.Test;
import org.sonar.api.measures.Metric;

public final class SonargraphMetricsTest
{
    @Test
    public void testMetricsDefinition() throws IOException
    {
        final SonargraphMetrics sonargraphMetrics = new SonargraphMetrics(new TestSupportMetricPropertiesProvider());
        @SuppressWarnings("rawtypes")
        final List<Metric> metrics = sonargraphMetrics.getMetrics();

        //Different value dependending on the number of custom metrics loaded, which in turn depends on the
        //order of test execution...
        assertEquals("Wrong number of metrics (init triggered)", 53, metrics.size());
        assertEquals("Wrong number of metrics (no init necessary)", metrics, sonargraphMetrics.getMetrics());
    }
}