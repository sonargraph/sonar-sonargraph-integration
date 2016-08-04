package com.hello2morrow.sonargraph.integration.sonarqube.api;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.measure.MetricFinder;
import org.sonar.api.config.Settings;
import org.sonar.api.measures.Metric;
import org.sonar.api.profiles.RulesProfile;

import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphPluginBase;

@SuppressWarnings("rawtypes")
public abstract class AbstractSonargraphSensorTest implements MetricFinder
{
    protected RulesProfile rulesProfile;
    protected SensorContext sensorContext;
    protected FileSystem moduleFileSystem;
    protected Settings settings;

    protected SonargraphSensor sensor;
    protected SonargraphRulesRepository sonargraphRulesRepository;
    protected List<org.sonar.api.batch.measure.Metric> metrics;

    @Before
    public void initSensor()
    {
        rulesProfile = TestHelper.initRulesProfile();
        sensorContext = TestHelper.initSensorContext();
        moduleFileSystem = TestHelper.initModuleFileSystem(getBasePath());
        settings = TestHelper.initSettings();
        settings.setProperty(SonargraphPluginBase.REPORT_PATH_OLD, getReport());

        sonargraphRulesRepository = new SonargraphRulesRepository(settings);
        metrics = new ArrayList<>();
        for (final Metric metric : sonargraphRulesRepository.getMetrics())
        {
            final org.sonar.api.batch.measure.Metric converted = mock(org.sonar.api.batch.measure.Metric.class);
            when(converted.key()).thenAnswer(new Answer<String>()
            {
                @Override
                public String answer(final InvocationOnMock invocation) throws Throwable
                {
                    return metric.key();
                }
            });
            metrics.add(converted);
        }
        sensor = new SonargraphSensor(this, rulesProfile, settings, moduleFileSystem, TestHelper.initPerspectives());
        assertTrue(sensor.toString().startsWith(SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME));
    }

    protected abstract String getReport();

    protected abstract String getBasePath();

    @Override
    public org.sonar.api.batch.measure.Metric findByKey(final String key)
    {
        return null;
    }

    @Override
    public Collection<org.sonar.api.batch.measure.Metric> findAll(final List<String> metricKeys)
    {
        return Collections.emptyList();
    }

    @Override
    public Collection<org.sonar.api.batch.measure.Metric> findAll()
    {
        return metrics;
    }
}
