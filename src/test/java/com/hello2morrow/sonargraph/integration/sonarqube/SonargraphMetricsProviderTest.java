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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.measures.Metric;

import com.hello2morrow.sonargraph.integration.access.controller.ControllerFactory;
import com.hello2morrow.sonargraph.integration.access.controller.ISonargraphSystemController;
import com.hello2morrow.sonargraph.integration.access.foundation.Result;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;
import com.hello2morrow.sonargraph.integration.sonarqube.SonargraphMetricsProvider.MetricLogLevel;

public class SonargraphMetricsProviderTest
{
    @Rule
    public TemporaryFolder targetDirectory = new TemporaryFolder();

    @Test
    public void testCreationOfCustomMetrics() throws IOException
    {
        final ISonargraphSystemController controller = ControllerFactory.createController();
        final Result result = controller.loadSystemReport(new File("./src/test/report/IntegrationSonarqube.xml"));
        assertTrue("Failed to load report", result.isSuccess());
        final SonargraphMetricsProvider metricsProvider = new SonargraphMetricsProvider(targetDirectory.getRoot().getAbsolutePath());
        final Properties customMetrics = new Properties();
        final List<IMetricId> metricIds = controller.createSystemInfoProcessor().getMetricIds();
        for (final IMetricId nextMetricId : metricIds)
        {
            metricsProvider.addCustomMetric(controller.getSoftwareSystem(), nextMetricId, customMetrics);
        }

        assertEquals("Wrong number of custom metrics", customMetrics.size(), metricIds.size());

        final List<Metric<Serializable>> metrics = metricsProvider.convertCustomMetricProperties(customMetrics);
        assertEquals("Wrong number of metrics", metrics.size(), metricIds.size());
        final String systemIdFromReport = "ae8982aeeb97a896d5c5f4668d46a1ee";
        final String customPropertiesFileName = systemIdFromReport + ".properties";
        metricsProvider.saveMetricProperties(customMetrics, targetDirectory.getRoot(), customPropertiesFileName, "Sonargraph Test Metrics");

        final File customMetricsFile = new File(targetDirectory.getRoot(), customPropertiesFileName);
        assertTrue("Missing custom metrics properties file: " + customMetricsFile.getAbsolutePath(), customMetricsFile.exists());

        final Properties customMetricsProperties = metricsProvider.loadSonargraphCustomMetrics(MetricLogLevel.DEBUG, systemIdFromReport);
        assertNotNull(customMetricsProperties);
        assertEquals("Wrong number of custom metrics", metrics.size(), customMetricsProperties.size());
    }

    @Test
    public void testStandardMetrics()
    {
        final SonargraphMetricsProvider metricsProvider = new SonargraphMetricsProvider();
        final List<Metric<Serializable>> standardMetrics = metricsProvider.loadStandardMetrics();
        assertTrue("Missing standard metrics, size = " + standardMetrics.size(), standardMetrics.size() > 50);
    }

    @Test
    public void testGetNonEmptyString()
    {
        try
        {
            SonargraphBase.getNonEmptyString(null);
            fail("This line should not be reached");
        }
        catch (final IllegalArgumentException e)
        {
            //Expected
        }

        try
        {
            SonargraphBase.getNonEmptyString("");
            fail("This line should not be reached");
        }
        catch (final IllegalArgumentException e)
        {
            //Expected
        }

        try
        {
            SonargraphBase.getNonEmptyString(Integer.valueOf(42));
            fail("This line should not be reached");
        }
        catch (final IllegalArgumentException e)
        {
            //Expected
        }
    }

    /**
     * Verifies that custom metrics are loaded from different metric properties files
     */
    @Test
    public void testLoadCustomMetricsForMultipleSystems()
    {
        final ISonargraphSystemController controller = ControllerFactory.createController();
        final Result result = controller.loadSystemReport(new File("./src/test/report/IntegrationSonarqube.xml"));
        assertTrue("Failed to load report", result.isSuccess());
        final SonargraphMetricsProvider metricsProvider = new SonargraphMetricsProvider("./src/test/customMetricsUserHome");

        final Properties customMetrics = metricsProvider.loadSonargraphCustomMetrics(MetricLogLevel.INFO);
        assertEquals("Wrong number of custom metrics", 4, customMetrics.size());

        final List<String> expectedMetricKeys = Arrays.asList("IntegrationSonarqube|CoreIgnoredThresholdViolations",
                "IntegrationSonarqube|CoreEmptyArtifactCount", "IntegrationSonarqube|CoreViolatingComponents", "AlarmClock|UsageOfSystemOutPrintln");
        for (final String next : expectedMetricKeys)
        {
            assertNotNull("Missing metric value for " + next, customMetrics.get(next));
        }
    }
}