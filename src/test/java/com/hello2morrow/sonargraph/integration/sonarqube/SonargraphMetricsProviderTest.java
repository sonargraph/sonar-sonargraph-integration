package com.hello2morrow.sonargraph.integration.sonarqube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
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

public class SonargraphMetricsProviderTest
{
    @Rule
    public TemporaryFolder targetDirectory = new TemporaryFolder();

    @Test
    public void testCustomMetrics() throws IOException
    {
        final ISonargraphSystemController controller = ControllerFactory.createController();
        final Result result = controller.loadSystemReport(new File("./src/test/report/IntegrationSonarqube.xml"));
        assertTrue("Failed to load report", result.isSuccess());
        final SonargraphMetricsProvider metricsProvider = new SonargraphMetricsProvider();
        final Properties customMetrics = new Properties();
        final List<IMetricId> metricIds = controller.createSystemInfoProcessor().getMetricIds();
        for (final IMetricId nextMetricId : metricIds)
        {
            metricsProvider.addCustomMetric(controller.getSoftwareSystem(), nextMetricId, customMetrics);
        }

        assertEquals("Wrong number of custom metrics", customMetrics.size(), metricIds.size());

        final List<Metric<Serializable>> metrics = metricsProvider.getCustomMetrics(customMetrics);
        assertEquals("Wrong number of metrics", metrics.size(), metricIds.size());
        metricsProvider.save(customMetrics, targetDirectory.getRoot(), "Sonargraph Test Metrics");
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
}