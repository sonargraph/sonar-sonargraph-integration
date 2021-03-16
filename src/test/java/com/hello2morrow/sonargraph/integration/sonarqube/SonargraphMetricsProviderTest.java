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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.measures.Metric;

import com.hello2morrow.sonargraph.integration.access.controller.ControllerFactory;
import com.hello2morrow.sonargraph.integration.access.controller.ISonargraphSystemController;
import com.hello2morrow.sonargraph.integration.access.foundation.Result;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;

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
        final SonargraphMetricsProvider metricsProvider = new SonargraphMetricsProvider(
                targetDirectory.getRoot().getAbsolutePath());

        metricsProvider.loadStandardMetrics();
        metricsProvider.loadCustomMetrics();

        final List<IMetricId> metricIds = controller.createSystemInfoProcessor().getMetricIds();
        final int numberOfCustomMetrics = metricsProvider.getCustomMetricProperties().size();
        for (final IMetricId nextMetricId : metricIds)
        {
            metricsProvider.addCustomMetric(nextMetricId);
        }

        final Properties customMetricProperties = metricsProvider.getCustomMetricProperties();
        assertEquals("Wrong number of custom metrics", numberOfCustomMetrics + metricIds.size(),
                customMetricProperties.size());

        final Map<String, Metric<Serializable>> metrics = metricsProvider
                .convertMetricProperties(metricsProvider.getCustomMetricProperties());
        assertEquals("Wrong number of metrics", metrics.size(), metricIds.size());
        final File targetFile = new File(metricsProvider.getFilePath());
        metricsProvider.saveProperties(customMetricProperties, targetFile, "Sonargraph Test Metrics");

        final File customMetricsFile = new File(metricsProvider.getFilePath());
        assertTrue("Missing custom metrics properties file: " + customMetricsFile.getAbsolutePath(),
                customMetricsFile.exists());

        final Map<String, Metric<Serializable>> customMetricsReloaded = metricsProvider.loadCustomMetrics();
        assertNotNull(customMetricsReloaded);
        assertEquals("Wrong number of custom metrics", metrics.size(), customMetricsReloaded.size());
    }

    @Test
    public void testStandardMetrics()
    {
        final SonargraphMetricsProvider metricsProvider = new SonargraphMetricsProvider(
                targetDirectory.getRoot().getAbsolutePath());
        final Map<String, Metric<Serializable>> standardMetrics = metricsProvider.loadStandardMetrics();
        assertTrue("Missing standard metrics, size = " + standardMetrics.size(), standardMetrics.size() > 50);

        final Map<String, Metric<Serializable>> customMetrics = metricsProvider.loadCustomMetrics();
        assertEquals("No custom metrics expected", 0, customMetrics.size());

        assertEquals("Combined metrics and standard metrics are expected to be the same if no custom metrics exist",
                standardMetrics.size() + customMetrics.size(), metricsProvider.getCombinedMetricProperties().size());
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
}