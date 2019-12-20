package com.hello2morrow.sonargraph.integration.sonarqube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
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

public class CustomMetricsProviderTest
{
    @Rule
    public TemporaryFolder targetDirectory = new TemporaryFolder();

    @Test
    public void testCustomMetrics()
    {
        final ISonargraphSystemController controller = ControllerFactory.createController();
        final Result result = controller.loadSystemReport(new File("./src/test/report/IntegrationSonarqube.xml"));
        assertTrue("Failed to load report", result.isSuccess());
        final CustomMetricsProvider metricsProvider = new CustomMetricsProvider();
        final Properties customMetrics = new Properties();
        final List<IMetricId> metricIds = controller.createSystemInfoProcessor().getMetricIds();
        for (final IMetricId nextMetricId : metricIds)
        {
            metricsProvider.addCustomMetric(controller.getSoftwareSystem(), nextMetricId, customMetrics);
        }

        assertEquals("Wrong number of custom metrics", customMetrics.size(), metricIds.size());

        final List<Metric<Serializable>> metrics = metricsProvider.getCustomMetrics(customMetrics);
        assertEquals("Wrong number of metrics", metrics.size(), metricIds.size());
        metricsProvider.save(customMetrics, targetDirectory.getRoot());
    }

    @Test
    public void testGetNonEmptyString()
    {
        try
        {
            CustomMetricsProvider.getNonEmptyString(null);
            fail("This line should not be reached");
        }
        catch (final IllegalArgumentException e)
        {
            //Expected
        }

        try
        {
            CustomMetricsProvider.getNonEmptyString("");
            fail("This line should not be reached");
        }
        catch (final IllegalArgumentException e)
        {
            //Expected
        }

        try
        {
            CustomMetricsProvider.getNonEmptyString(Integer.valueOf(42));
            fail("This line should not be reached");
        }
        catch (final IllegalArgumentException e)
        {
            //Expected
        }
    }
}