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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.measure.Metric;
import org.sonar.api.batch.measure.MetricFinder;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Context;

import com.hello2morrow.sonargraph.integration.sonarqube.SonargraphBase.ICustomMetricsPropertiesProvider;

public final class SonargraphSensorTest
{
    private static final String JAVA_FILE_CONTENT = "bla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla"
            + "\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla"
            + "\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\n";

    private final ICustomMetricsPropertiesProvider customMetricsPropertiesProvider = new ICustomMetricsPropertiesProvider()
    {
        @Override
        public String getDirectory()
        {
            return "./." + SonargraphBase.SONARGRAPH_PLUGIN_KEY;
        }
    };

    private final SensorDescriptor sensorDescriptor = new SensorDescriptor()
    {
        @Override
        public SensorDescriptor requireProperty(final String... propertyKey)
        {
            return null;
        }

        @Override
        public SensorDescriptor requireProperties(final String... propertyKeys)
        {
            return null;
        }

        @Override
        public SensorDescriptor onlyWhenConfiguration(final Predicate<Configuration> predicate)
        {
            return null;
        }

        @Override
        public SensorDescriptor onlyOnLanguages(final String... languageKeys)
        {
            return null;
        }

        @Override
        public SensorDescriptor onlyOnLanguage(final String languageKey)
        {
            return null;
        }

        @Override
        public SensorDescriptor onlyOnFileType(final Type type)
        {
            return null;
        }

        @Override
        public SensorDescriptor name(final String sensorName)
        {
            return null;
        }

        @Override
        public SensorDescriptor global()
        {
            return null;
        }

        @Override
        public SensorDescriptor createIssuesForRuleRepository(final String... repositoryKey)
        {
            return null;
        }

        @Override
        public SensorDescriptor createIssuesForRuleRepositories(final String... repositoryKeys)
        {
            return null;
        }
    };

    private MetricFinder metricFinder;
    private ActiveRulesBuilder rulesBuilder;

    @SuppressWarnings({ "unchecked" })
    @Before
    public void before()
    {
        SonargraphBase.setCustomMetricsPropertiesProvider(customMetricsPropertiesProvider);

        final SonargraphRules sonargraphRules = new SonargraphRules();
        final Context rulesContext = SonargraphRulesTest.TestRules.createTestContext();
        sonargraphRules.define(rulesContext);
        final List<RulesDefinition.Rule> rules = rulesContext.repository(SonargraphBase.SONARGRAPH_PLUGIN_KEY).rules();

        rulesBuilder = new ActiveRulesBuilder();
        for (final RulesDefinition.Rule nextRule : rules)
        {
            rulesBuilder.create(RuleKey.of(SonargraphBase.SONARGRAPH_PLUGIN_KEY, nextRule.key())).setName(nextRule.name())
                    .setLanguage(SonargraphBase.JAVA).activate();
        }

        final SonargraphMetrics sonargraphMetrics = new SonargraphMetrics();
        final Map<String, Metric<Serializable>> keyToMetric = new HashMap<>();
        for (final org.sonar.api.measures.Metric<?> nextMetric : sonargraphMetrics.getMetrics())
        {
            keyToMetric.put(nextMetric.getKey(), (Metric<Serializable>) nextMetric);
        }

        metricFinder = new MetricFinder()
        {
            @Override
            public <G extends Serializable> Metric<G> findByKey(final String key)
            {
                return (Metric<G>) keyToMetric.get(key);
            }

            @Override
            public Collection<Metric<Serializable>> findAll(final List<String> metricKeys)
            {
                final Set<Metric<Serializable>> found = new LinkedHashSet<>();
                for (final String next : metricKeys)
                {
                    final Metric<Serializable> foundMetric = keyToMetric.get(next);
                    if (foundMetric != null)
                    {
                        found.add(foundMetric);
                    }
                }
                return found;
            }

            @Override
            public Collection<Metric<Serializable>> findAll()
            {
                return keyToMetric.values();
            }
        };
    }

    @After
    public void after()
    {
        metricFinder = null;
    }

    @Test
    public void testSonargraphSensorOnReportFile()
    {
        final SensorContextTester sensorContextTester = SensorContextTester.create(new File("."));
        final DefaultFileSystem fileSystem = sensorContextTester.fileSystem();

        fileSystem.add(
                TestInputFileBuilder.create("projectKey", "./src/main/java/com/hello2morrow/sonargraph/integration/sonarqube/SonargraphBase.java")
                        .setLanguage(SonargraphBase.JAVA).build());

        final MapSettings settings = new MapSettings();
        settings.setProperty(SonargraphBase.XML_REPORT_FILE_PATH_KEY, "./src/test/report/IntegrationSonarqube.xml");
        sensorContextTester.setSettings(settings);
        sensorContextTester.setActiveRules(rulesBuilder.build());

        final SonargraphSensor sonargraphSensor = new SonargraphSensor(fileSystem, metricFinder);
        sonargraphSensor.describe(sensorDescriptor);
        sonargraphSensor.execute(sensorContextTester);
    }

    @Test
    public void testSonargraphSensorOnInvalidReportFile()
    {
        final SensorContextTester sensorContextTester = SensorContextTester.create(new File("."));
        final DefaultFileSystem fileSystem = sensorContextTester.fileSystem();

        fileSystem.add(
                TestInputFileBuilder.create("projectKey", "./src/main/java/com/hello2morrow/sonargraph/integration/sonarqube/SonargraphBase.java")
                        .setLanguage(SonargraphBase.JAVA).build());

        final MapSettings settings = new MapSettings();
        settings.setProperty(SonargraphBase.XML_REPORT_FILE_PATH_KEY, "./src/test/report/IntegrationSonarqubeInvalid.xml");
        sensorContextTester.setSettings(settings);
        sensorContextTester.setActiveRules(rulesBuilder.build());

        final SonargraphSensor sonargraphSensor = new SonargraphSensor(fileSystem, metricFinder);
        sonargraphSensor.describe(sensorDescriptor);
        sonargraphSensor.execute(sensorContextTester);
    }

    @Test
    public void testSonargraphSensorOnEmptyReportFile()
    {
        final SensorContextTester sensorContextTester = SensorContextTester.create(new File("."));
        final DefaultFileSystem fileSystem = sensorContextTester.fileSystem();

        fileSystem.add(
                TestInputFileBuilder.create("projectKey", "./src/main/java/com/hello2morrow/sonargraph/integration/sonarqube/SonargraphBase.java")
                        .setLanguage(SonargraphBase.JAVA).build());

        final MapSettings settings = new MapSettings();
        settings.setProperty(SonargraphBase.XML_REPORT_FILE_PATH_KEY, "./src/test/report/IntegrationSonarqubeEmpty.xml");
        sensorContextTester.setSettings(settings);
        sensorContextTester.setActiveRules(rulesBuilder.build());

        final SonargraphSensor sonargraphSensor = new SonargraphSensor(fileSystem, metricFinder);
        sonargraphSensor.describe(sensorDescriptor);
        sonargraphSensor.execute(sensorContextTester);
    }

    @Test
    public void testSonargraphSensorOnTestProject() throws IOException
    {
        final String basePath = "./src/test/test-project";
        final SensorContextTester sensorContextTester = SensorContextTester.create(new File(basePath).getCanonicalFile());
        sensorContextTester.setActiveRules(rulesBuilder.build());

        final DefaultFileSystem fileSystem = sensorContextTester.fileSystem();
        fileSystem.add(TestInputFileBuilder.create("projectKey", fileSystem.baseDir(), new File(basePath, "src/com/h2m/C1.java").getCanonicalFile())
                .setLanguage(SonargraphBase.JAVA).setContents(JAVA_FILE_CONTENT).build());
        fileSystem.add(TestInputFileBuilder.create("projectKey", fileSystem.baseDir(), new File(basePath, "src/com/h2m/C2.java").getCanonicalFile())
                .setLanguage(SonargraphBase.JAVA).setContents(JAVA_FILE_CONTENT).build());

        final SonargraphSensor sonargraphSensor = new SonargraphSensor(fileSystem, metricFinder);
        sonargraphSensor.describe(sensorDescriptor);
        sonargraphSensor.execute(sensorContextTester);
    }

    @Test
    public void testSonargraphSensorOnTestProjectWithReportFromDifferentOrigin() throws IOException
    {
        final String basePath = "./src/test/test-project";
        final SensorContextTester sensorContextTester = SensorContextTester.create(new File(basePath).getCanonicalFile());

        final MapSettings settings = new MapSettings();
        settings.setProperty(SonargraphBase.XML_REPORT_FILE_PATH_KEY, "./src/test/test-project/test-project_from_different_origin.xml");
        settings.setProperty(SonargraphBase.SONARGRAPH_BASE_DIR_KEY, "./src/test/test-project");
        sensorContextTester.setSettings(settings);

        sensorContextTester.setActiveRules(rulesBuilder.build());

        final DefaultFileSystem fileSystem = sensorContextTester.fileSystem();
        fileSystem.add(TestInputFileBuilder.create("projectKey", fileSystem.baseDir(), new File(basePath, "src/com/h2m/C1.java").getCanonicalFile())
                .setLanguage(SonargraphBase.JAVA).setContents(JAVA_FILE_CONTENT).build());
        fileSystem.add(TestInputFileBuilder.create("projectKey", fileSystem.baseDir(), new File(basePath, "src/com/h2m/C2.java").getCanonicalFile())
                .setLanguage(SonargraphBase.JAVA).setContents(JAVA_FILE_CONTENT).build());

        final SonargraphSensor sonargraphSensor = new SonargraphSensor(fileSystem, metricFinder);
        sonargraphSensor.describe(sensorDescriptor);
        sonargraphSensor.execute(sensorContextTester);
    }

    @Test
    public void testSonargraphSensorOnEmptyTestProject()
    {
        final SensorContextTester sensorContextTester = SensorContextTester.create(new File("./src/test/test-project"));
        final DefaultFileSystem fileSystem = sensorContextTester.fileSystem();
        sensorContextTester.setActiveRules(rulesBuilder.build());
        final SonargraphSensor sonargraphSensor = new SonargraphSensor(fileSystem, metricFinder);
        sonargraphSensor.describe(sensorDescriptor);
        sonargraphSensor.execute(sensorContextTester);
    }
}