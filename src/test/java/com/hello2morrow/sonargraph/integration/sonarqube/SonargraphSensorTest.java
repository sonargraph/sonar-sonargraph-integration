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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.measure.Measure;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Context;

import com.hello2morrow.sonargraph.integration.sonarqube.SonargraphBase.CustomMetricsPropertiesProvider;

public final class SonargraphSensorTest
{
    private static final String DUMMY_CONTENT = "bla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla"
            + "\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla"
            + "\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\n";

    private final SensorDescriptor sensorDescriptor = new SensorDescriptor()
    {
        @Override
        public SensorDescriptor requireProperty(final String... propertyKey)
        {
            return this;
        }

        @Override
        public SensorDescriptor requireProperties(final String... propertyKeys)
        {
            return this;
        }

        @Override
        public SensorDescriptor onlyWhenConfiguration(final Predicate<Configuration> predicate)
        {
            return this;
        }

        @Override
        public SensorDescriptor onlyOnLanguages(final String... languageKeys)
        {
            return this;
        }

        @Override
        public SensorDescriptor onlyOnLanguage(final String languageKey)
        {
            return this;
        }

        @Override
        public SensorDescriptor onlyOnFileType(final Type type)
        {
            return this;
        }

        @Override
        public SensorDescriptor name(final String sensorName)
        {
            return this;
        }

        @Override
        public SensorDescriptor global()
        {
            return this;
        }

        @Override
        public SensorDescriptor createIssuesForRuleRepository(final String... repositoryKey)
        {
            return this;
        }

        @Override
        public SensorDescriptor createIssuesForRuleRepositories(final String... repositoryKeys)
        {
            return this;
        }
    };

    private MetricFinder metricFinder;
    private ActiveRulesBuilder rulesBuilder;

    private SonargraphMetrics sonargraphMetrics;

    @SuppressWarnings({ "unchecked" })
    @Before
    public void before() throws IOException
    {
        final SonargraphRules sonargraphRules = new SonargraphRules();
        final Context rulesContext = SonargraphRulesTest.TestRules.createTestContext();
        sonargraphRules.define(rulesContext);
        final List<RulesDefinition.Rule> rules = rulesContext.repository(SonargraphBase.SONARGRAPH_PLUGIN_KEY).rules();

        rulesBuilder = new ActiveRulesBuilder();
        for (final RulesDefinition.Rule nextRule : rules)
        {
            //When migrating to SonarQbue Plugin API > 7.9, this must be used.
            //            final NewActiveRule.Builder builder = new NewActiveRule.Builder();
            //            final NewActiveRule rule = builder.setRuleKey(RuleKey.of(SonargraphBase.SONARGRAPH_PLUGIN_KEY, nextRule.key())).setName(nextRule.name())
            //                    .setLanguage(SonargraphBase.JAVA).build();
            //            rulesBuilder.addRule(rule);

            rulesBuilder.create(RuleKey.of(SonargraphBase.SONARGRAPH_PLUGIN_KEY, nextRule.key())).setName(nextRule.name())
                    .setLanguage(SonargraphBase.JAVA).activate();
        }

        final CustomMetricsPropertiesProvider customMetricsPropertiesProvider = new TestSupportMetricPropertiesProvider();
        sonargraphMetrics = new SonargraphMetrics(customMetricsPropertiesProvider);
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
    public void testSonargraphSensorOnReportFile() throws IOException
    {
        final File moduleBaseDir = new File(".").getCanonicalFile();
        final SensorContextTester context = SensorContextTester.create(moduleBaseDir);
        final DefaultFileSystem fileSystem = context.fileSystem();

        createTestFile(moduleBaseDir, fileSystem);

        final MapSettings settings = new MapSettings();
        settings.setProperty(SonargraphBase.XML_REPORT_FILE_PATH_KEY, "./src/test/report/IntegrationSonarqube_9-11-2.xml");
        settings.setProperty(SonargraphBase.SONARGRAPH_BASE_DIR_KEY, ".");
        context.setSettings(settings);

        context.setActiveRules(rulesBuilder.build());

        final SonargraphSensor sonargraphSensor = new SonargraphSensor(fileSystem, metricFinder, sonargraphMetrics);
        sonargraphSensor.describe(sensorDescriptor);
        sonargraphSensor.execute(context);

        //Check for standard metric
        final Measure<Integer> coreTypesMeasure = context.measure(context.module().key(),
                SonargraphBase.createMetricKeyFromStandardName("CoreComponents"));
        assertNotNull("Missing measure", coreTypesMeasure);
        assertEquals("Wrong value", 12, coreTypesMeasure.value().intValue());

        //There is no support for source file metric values in the SonarQube plugin! We check for metric threshold violation instead.

        final String scriptIssueKey = "SCRIPT_ISSUE";
        final String componentKey = "projectKey:src/main/java/com/hello2morrow/sonargraph/integration/sonarqube/SonargraphBase.java";
        final List<Issue> issues = context.allIssues().stream()
                .filter(issue -> issue.ruleKey().rule().equals(scriptIssueKey) && issue.primaryLocation().inputComponent().key().equals(componentKey))
                .collect(Collectors.toList());
        assertEquals("Missing issue", 1, issues.size());
    }

    @Test
    public void testSonargraphSensorOnInvalidReportFile() throws IOException
    {
        final File moduleBaseDir = new File(".").getCanonicalFile();
        final SensorContextTester context = SensorContextTester.create(moduleBaseDir);
        final DefaultFileSystem fileSystem = context.fileSystem();
        createTestFile(moduleBaseDir, fileSystem);

        final MapSettings settings = new MapSettings();
        settings.setProperty(SonargraphBase.XML_REPORT_FILE_PATH_KEY, "./src/test/report/IntegrationSonarqubeInvalid.xml");
        context.setSettings(settings);
        context.setActiveRules(rulesBuilder.build());

        final SonargraphSensor sonargraphSensor = new SonargraphSensor(fileSystem, metricFinder, sonargraphMetrics);
        sonargraphSensor.describe(sensorDescriptor);
        sonargraphSensor.execute(context);

        //Check for standard metric
        final Measure<Integer> coreTypesMeasure = context.measure(context.module().key(),
                SonargraphBase.createMetricKeyFromStandardName("CoreComponents"));
        assertNull("Measure not expected, because processing of report failed", coreTypesMeasure);
    }

    @Test
    public void testSonargraphSensorOnEmptyTestProject()
    {
        final SensorContextTester context = SensorContextTester.create(new File("./src/test/test-project"));
        final DefaultFileSystem fileSystem = context.fileSystem();
        context.setActiveRules(rulesBuilder.build());
        final SonargraphSensor sonargraphSensor = new SonargraphSensor(fileSystem, metricFinder, sonargraphMetrics);
        sonargraphSensor.describe(sensorDescriptor);
        sonargraphSensor.execute(context);

        assertTrue("No issues expected, since no files are provided", context.allIssues().isEmpty());
    }

    @Test
    public void testSonargraphSensorOnTestProject() throws IOException
    {
        final SensorContextTester context = setupAndExecuteSensorForTestProject(null, "./src/test/test-project");
        validateContextForTestProject(context);
    }

    @Test
    public void testSonargraphSensorOnTestProjectWithReportFromDifferentOrigin() throws IOException
    {
        final MapSettings settings = new MapSettings();
        settings.setProperty(SonargraphBase.XML_REPORT_FILE_PATH_KEY, "./src/test/test-project/test-project_from_different_origin.xml");
        settings.setProperty(SonargraphBase.SONARGRAPH_BASE_DIR_KEY, "./src/test/test-project");

        final SensorContextTester context = setupAndExecuteSensorForTestProject(settings, "./src/test/test-project");
        validateContextForTestProject(context);
    }

    private SensorContextTester setupAndExecuteSensorForTestProject(final MapSettings settings, final String basePath) throws IOException
    {
        File baseToUse;
        final SensorContextTester context;
        if (settings == null)
        {
            baseToUse = new File(basePath).getCanonicalFile();
            context = SensorContextTester.create(baseToUse);
        }
        else
        {
            baseToUse = new File(".").getCanonicalFile();
            context = SensorContextTester.create(baseToUse);
            context.setSettings(settings);
        }
        context.setActiveRules(rulesBuilder.build());

        final DefaultFileSystem fileSystem = context.fileSystem();
        fileSystem.add(TestInputFileBuilder.create("projectKey", fileSystem.baseDir(), new File(basePath, "src/com/h2m/C1.java").getCanonicalFile())
                .setLanguage(SonargraphBase.JAVA).setContents(DUMMY_CONTENT).build());
        fileSystem.add(TestInputFileBuilder.create("projectKey", fileSystem.baseDir(), new File(basePath, "src/com/h2m/C2.java").getCanonicalFile())
                .setLanguage(SonargraphBase.JAVA).setContents(DUMMY_CONTENT).build());

        final SonargraphSensor sonargraphSensor = new SonargraphSensor(fileSystem, metricFinder, sonargraphMetrics);
        sonargraphSensor.describe(sensorDescriptor);
        sonargraphSensor.execute(context);
        return context;
    }

    private void validateContextForTestProject(final SensorContextTester context)
    {
        //Check for core metric
        final Measure<Integer> coreTypesMeasure = context.measure(context.module().key(),
                SonargraphBase.createMetricKeyFromStandardName("CoreComponents"));
        assertNotNull("Missing measure", coreTypesMeasure);
        assertEquals("Wrong value", 2, coreTypesMeasure.value().intValue());

        final int thresholdViolationErrorCount = 1;
        final int thresholdViolationWarningCount = 2;
        final int duplicatesCount = 2;
        final int todoCount = 1;
        final Collection<Issue> issues = context.allIssues();
        assertEquals("Wrong number of issues", thresholdViolationErrorCount + thresholdViolationWarningCount + duplicatesCount + todoCount,
                issues.size());

        checkIssueCount("Wrong number of threshold errors", "ThresholdViolationError", thresholdViolationErrorCount, issues);
        checkIssueCount("Wrong number of threshold warnings", "ThresholdViolation", thresholdViolationWarningCount, issues);
        checkIssueCount("Wrong number of duplicates", "DuplicateCodeBlock", duplicatesCount, issues);
        checkIssueCount("Wrong number of todos", "Todo", todoCount, issues);

        //Check for resolutions
        final String todoRuleKey = SonargraphBase.createRuleKey("Todo");
        final Issue todo = issues.stream().filter(issue -> issue.ruleKey().rule().equals(todoRuleKey)).findFirst().get();
        final String expectedTodoMessage = "[Todo] assignee='Dietmar' priority='Medium' description='Review.' created='2018-05-15T15:27:56.031-05:00' Review. [Core]";
        assertEquals("Wrong message", expectedTodoMessage, todo.primaryLocation().message());

        final String thresholdRuleKey = SonargraphBase.createRuleKey("ThresholdViolation");
        final Issue thresholdWarning = issues.stream().filter(
                issue -> issue.ruleKey().rule().equals(thresholdRuleKey) && issue.primaryLocation().inputComponent().key().contains("C1.java"))
                .findFirst().get();
        final String expectedFixMessage = "[Fix: Threshold Violation] assignee='Dietmar' priority='Medium' description='Do it.' created='2018-05-18T17:42:08.056-05:00' Comment Lines = 0 (allowed range: 10 to 100) [Core]";
        assertEquals("Wrong message", expectedFixMessage, thresholdWarning.primaryLocation().message());
    }

    private static void checkIssueCount(final String message, final String sonargraphIssueKey, final int expectedCount,
            final Collection<Issue> issues)
    {
        final String thresholdErrorKey = SonargraphBase.createRuleKey(sonargraphIssueKey);
        assertEquals(message, expectedCount, issues.stream().filter(issue -> issue.ruleKey().rule().equals(thresholdErrorKey)).count());
    }

    private static void createTestFile(final File moduleBaseDir, final DefaultFileSystem fileSystem) throws IOException
    {
        final File absoluteSourceFile = new File(moduleBaseDir,
                "src/main/java/com/hello2morrow/sonargraph/integration/sonarqube/SonargraphBase.java");
        final String content = Files.lines(absoluteSourceFile.toPath(), StandardCharsets.UTF_8).collect(Collectors.joining("\n"));
        fileSystem.add(TestInputFileBuilder.create("projectKey", moduleBaseDir, absoluteSourceFile).setContents(content)
                .setLanguage(SonargraphBase.JAVA).build());
    }
}