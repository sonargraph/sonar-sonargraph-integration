/**
 * SonarQube Sonargraph Integration Plugin
 * Copyright (C) 2016-2021 hello2morrow GmbH
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
import java.util.Arrays;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.measure.Metric;
import org.sonar.api.batch.measure.MetricFinder;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.rule.internal.NewActiveRule;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.measure.Measure;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Context;

import com.hello2morrow.sonargraph.integration.sonarqube.SonargraphRulesProvider.RuleDto;

public final class SonargraphSensorTest
{
    private static final String REPORT = "./src/test/report/IntegrationSonarqube_2020-11-06_11-39-26.xml";

    private static final String DUMMY_CONTENT = "bla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla"
            + "\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla"
            + "\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\nbla\n";

    private static final String SONARGRAPH_BASE = "src/main/java/com/hello2morrow/sonargraph/integration/sonarqube/SonargraphBase.java";
    private static final String SONARGRAPH_RULES_PROVIDER = "src/main/java/com/hello2morrow/sonargraph/integration/sonarqube/SonargraphRulesProvider.java";
    private static final String SONARGRAPH_RULES = "src/main/java/com/hello2morrow/sonargraph/integration/sonarqube/SonargraphRules.java";

    private final SensorDescriptor sensorDescriptor = new SensorDescriptor()
    {
        @Override
        public SensorDescriptor onlyWhenConfiguration(final Predicate<Configuration> predicate)
        {
            return this;
        }

        @Override
        public SensorDescriptor processesFilesIndependently()
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

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private MetricFinder metricFinder;
    private ActiveRulesBuilder rulesBuilder;

    private SonargraphMetrics sonargraphMetrics;
    private SonargraphRules sonargraphRules;

    @SuppressWarnings({ "unchecked" })
    @Before
    public void before()
    {
        initRules();

        sonargraphMetrics = new SonargraphMetrics(
                new SonargraphMetricsProvider(tempFolder.getRoot().getAbsolutePath()));
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

    private void initRules()
    {
        sonargraphRules = new SonargraphRules(new SonargraphRulesProvider(tempFolder.getRoot().getAbsolutePath()));
        final Context rulesContext = new Context();
        sonargraphRules.define(rulesContext);
        final List<RulesDefinition.Rule> rules = rulesContext.repository(SonargraphBase.SONARGRAPH_PLUGIN_KEY).rules();

        rulesBuilder = new ActiveRulesBuilder();
        for (final RulesDefinition.Rule nextRule : rules)
        {
            final NewActiveRule.Builder builder = new NewActiveRule.Builder();
            final NewActiveRule rule = builder
                    .setRuleKey(RuleKey.of(SonargraphBase.SONARGRAPH_PLUGIN_KEY, nextRule.key()))
                    .setName(nextRule.name()).setLanguage(SonargraphBase.JAVA).build();
            rulesBuilder.addRule(rule);
        }
    }

    @After
    public void after()
    {
        metricFinder = null;
    }

    @Test
    public void testSonargraphSensorOnReportFile() throws IOException
    {
        final int standardRulesCount = 19;
        final int scriptRulesCount = 2;
        final int pluginRulesCount = 1;
        final int customRulesCount = scriptRulesCount + pluginRulesCount;
        {
            final SensorContextTester context = setupAndExecuteSensor(REPORT,
                    Arrays.asList(SONARGRAPH_BASE, SONARGRAPH_RULES, SONARGRAPH_RULES_PROVIDER));

            //Check for standard metric
            final Measure<Integer> coreComponentsMetric = context.measure(context.module().key(),
                    SonargraphBase.createMetricKeyFromStandardName("CoreComponents"));
            assertNotNull("Missing measure", coreComponentsMetric);
            assertEquals("Wrong value", 21, coreComponentsMetric.value().intValue());

            final List<RuleDto> customRules = sonargraphRules.getRulesProvider().loadCustomRules();
            assertEquals("Wrong number of custom rules ", customRulesCount, customRules.size());
            verifyCustomRule(customRules.get(0),
                    "plugin_com.hello2morrow.sonargraph.plugin.spotbugs_spotbugs-warning_warning",
                    "Sonargraph Integration: Spotbugs warning (com.hello2morrow.sonargraph.plugin.spotbugs)",
                    Arrays.asList("plugin-based", "spotbugs-warning", "com.hello2morrow.sonargraph.plugin.spotbugs"),
                    "MINOR");
            verifyCustomRule(customRules.get(1), "script_core-findfixmeandtodoincomments.xml_fixme_warning",
                    "Sonargraph Integration: FIXME (./Core/FindFixmeAndTodoInComments.xml)",
                    Arrays.asList("script-based", "fixme", "core-findfixmeandtodoincomments.xml"), "MINOR");
            verifyCustomRule(customRules.get(2), "script_test.xml_typeissue_warning",
                    "Sonargraph Integration: TypeIssue (./Test.xml)",
                    Arrays.asList("script-based", "typeissue", "test.xml"), "MINOR");
            assertEquals("Wrong number of standard rules", standardRulesCount,
                    context.activeRules().findByRepository(SonargraphBase.SONARGRAPH_PLUGIN_KEY).size());
        }
        {
            //Do the same thing again, simulating a restart. Now custom issue and additional rules are expected.
            initRules();
            final SensorContextTester context = setupAndExecuteSensor(REPORT,
                    Arrays.asList(SONARGRAPH_BASE, SONARGRAPH_RULES, SONARGRAPH_RULES_PROVIDER));

            //Check for standard metric
            final Measure<Integer> coreComponentsMetric = context.measure(context.module().key(),
                    SonargraphBase.createMetricKeyFromStandardName("CoreComponents"));
            assertNotNull("Missing measure", coreComponentsMetric);
            assertEquals("Wrong value", 21, coreComponentsMetric.value().intValue());

            //There is no support for source file metric values in the SonarQube plugin! We check for metric threshold violation instead.

            final String scriptIssueKey = "script_test.xml_typeissue_warning";
            final String componentKey = "projectKey:src/main/java/com/hello2morrow/sonargraph/integration/sonarqube/SonargraphBase.java";
            final List<Issue> issues = context.allIssues().stream()
                    .filter(issue -> issue.ruleKey().rule().equals(scriptIssueKey)
                            && issue.primaryLocation().inputComponent().key().equals(componentKey))
                    .collect(Collectors.toList());
            assertEquals("Missing script issue", 1, issues.size());

            final String pluginIssueKey = "plugin_com.hello2morrow.sonargraph.plugin.spotbugs_spotbugs-warning_warning";
            final String componentKey2 = "projectKey:" + SONARGRAPH_RULES_PROVIDER;
            final List<Issue> issues2 = context.allIssues().stream()
                    .filter(issue -> issue.ruleKey().rule().equals(pluginIssueKey)
                            && issue.primaryLocation().inputComponent().key().equals(componentKey2))
                    .collect(Collectors.toList());
            assertEquals("Missing plugin issue", 2, issues2.size());

            final List<RuleDto> rules = sonargraphRules.getRulesProvider().loadCustomRules();
            assertEquals("Wrong number of custom rules", customRulesCount, rules.size());
            assertEquals("Wrong number of standard rules", standardRulesCount + customRulesCount,
                    context.activeRules().findByRepository(SonargraphBase.SONARGRAPH_PLUGIN_KEY).size());
        }
    }

    private void verifyCustomRule(final RuleDto ruleDto, final String key, final String name,
            final List<String> categoryTags, final String severity)
    {
        assertEquals("Wrong rule key", key, ruleDto.getKey());
        assertEquals("Wrong rule name", name, ruleDto.getName());
        assertEquals("Wrong category tags", categoryTags, Arrays.asList(ruleDto.getCategoryTags()));
        assertEquals("Wrong severity", severity, ruleDto.getSeverity());
    }

    private SensorContextTester setupAndExecuteSensor(final String reportPath, final List<String> paths)
            throws IOException
    {
        final File moduleBaseDir = new File(".").getCanonicalFile();
        final SensorContextTester context = SensorContextTester.create(moduleBaseDir);
        final DefaultFileSystem fileSystem = context.fileSystem();

        for (final String next : paths)
        {
            createTestFile(moduleBaseDir, fileSystem, next);
        }

        final MapSettings settings = new MapSettings();
        settings.setProperty(SonargraphBase.XML_REPORT_FILE_PATH_KEY, reportPath);
        settings.setProperty(SonargraphBase.SONARGRAPH_BASE_DIR_KEY, ".");
        context.setSettings(settings);

        final ActiveRules activeRules = rulesBuilder.build();
        context.setActiveRules(activeRules);

        final SonargraphSensor sonargraphSensor = new SonargraphSensor(fileSystem, metricFinder, sonargraphMetrics,
                sonargraphRules.getRulesProvider());
        sonargraphSensor.describe(sensorDescriptor);
        sonargraphSensor.execute(context);
        return context;
    }

    @Test
    public void testSonargraphSensorOnInvalidReportFile() throws IOException
    {
        final File moduleBaseDir = new File(".").getCanonicalFile();
        final SensorContextTester context = SensorContextTester.create(moduleBaseDir);
        final DefaultFileSystem fileSystem = context.fileSystem();
        createTestFile(moduleBaseDir, fileSystem, SONARGRAPH_BASE);

        final MapSettings settings = new MapSettings();
        settings.setProperty(SonargraphBase.XML_REPORT_FILE_PATH_KEY,
                "./src/test/report/IntegrationSonarqubeInvalid.xml");
        context.setSettings(settings);
        context.setActiveRules(rulesBuilder.build());

        final SonargraphSensor sonargraphSensor = new SonargraphSensor(fileSystem, metricFinder, sonargraphMetrics,
                sonargraphRules.getRulesProvider());
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
        final SonargraphSensor sonargraphSensor = new SonargraphSensor(fileSystem, metricFinder, sonargraphMetrics,
                sonargraphRules.getRulesProvider());
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
        settings.setProperty(SonargraphBase.XML_REPORT_FILE_PATH_KEY,
                "./src/test/test-project/test-project_from_different_origin.xml");
        settings.setProperty(SonargraphBase.SONARGRAPH_BASE_DIR_KEY, "./src/test/test-project");

        final SensorContextTester context = setupAndExecuteSensorForTestProject(settings, "./src/test/test-project");
        validateContextForTestProject(context);
    }

    @Test
    public void testReportWithUnsupportedLanguage() throws IOException
    {
        final MapSettings settings = new MapSettings();
        settings.setProperty(SonargraphBase.XML_REPORT_FILE_PATH_KEY, "./src/test/NAlarmClock/NAlarmClock_Report.xml");
        settings.setProperty(SonargraphBase.SONARGRAPH_BASE_DIR_KEY, "./src/test/test-project");

        final SensorContextTester context = setupAndExecuteSensorForTestProject(settings, "./src/test/test-project");
        final Measure<Integer> coreTypesMeasure = context.measure(context.module().key(),
                SonargraphBase.createMetricKeyFromStandardName("CoreComponents"));
        assertNull("No measure expected", coreTypesMeasure);
    }

    @Test
    public void testEmtpyReport() throws IOException
    {
        final MapSettings settings = new MapSettings();
        settings.setProperty(SonargraphBase.XML_REPORT_FILE_PATH_KEY,
                "./src/test/NAlarmClock/NAlarmClock_Report_empty_workspace.xml");
        settings.setProperty(SonargraphBase.SONARGRAPH_BASE_DIR_KEY, "./src/test/test-project");

        final SensorContextTester context = setupAndExecuteSensorForTestProject(settings, "./src/test/test-project");
        final Measure<Integer> coreTypesMeasure = context.measure(context.module().key(),
                SonargraphBase.createMetricKeyFromStandardName("CoreComponents"));
        assertNull("No measure expected", coreTypesMeasure);
    }

    @Test
    public void testGetRelativePathForMavenScannerApp() throws IOException
    {
        final MapSettings settings = new MapSettings();
        final String scanner = "ScannerMaven";
        settings.setProperty("sonar.scanner.app", scanner);
        final String outputPath = "target";
        settings.setProperty("sonar.projectBuildDir", outputPath);

        final File baseToUse = new File(".").getCanonicalFile();
        final SensorContextTester context = SensorContextTester.create(baseToUse);
        context.setSettings(settings);

        assertEquals("Wrong Maven default path",
                new File(new File(baseToUse, outputPath), SonargraphBase.XML_REPORT_FILE_PATH_DEFAULT)
                        .getCanonicalPath(),
                SonargraphSensor.getPathForScannerApp(context.config(), scanner));
    }

    @Test
    public void testGetRelativePathForGradleScannerApp() throws IOException
    {
        final MapSettings settings = new MapSettings();
        final String scanner = "ScannerGradle";
        settings.setProperty("sonar.scanner.app", scanner);
        final String outputPath = "build";
        settings.setProperty("sonar.working.directory", outputPath + "/sonar");

        final File baseToUse = new File(".").getCanonicalFile();
        final SensorContextTester context = SensorContextTester.create(baseToUse);
        context.setSettings(settings);

        assertEquals("Wrong Gradle default path",
                new File(new File(baseToUse, outputPath), SonargraphBase.XML_REPORT_FILE_PATH_DEFAULT)
                        .getCanonicalPath(),
                SonargraphSensor.getPathForScannerApp(context.config(), scanner));
    }

    @Test
    public void testGetRelativePathForDefaultScannerApp() throws IOException
    {
        final MapSettings settings = new MapSettings();
        final String scanner = "Scanner";
        settings.setProperty("sonar.scanner.app", scanner);

        final File baseToUse = new File(".").getCanonicalFile();
        final SensorContextTester context = SensorContextTester.create(baseToUse);
        context.setSettings(settings);

        assertNull("Wrong Gradle default path",
                SonargraphSensor.getPathForScannerApp(context.config(), scanner));
    }

    private SensorContextTester setupAndExecuteSensorForTestProject(final MapSettings settings, final String basePath)
            throws IOException
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
        fileSystem.add(TestInputFileBuilder
                .create("projectKey", fileSystem.baseDir(),
                        new File(basePath, "src/com/h2m/C1.java").getCanonicalFile())
                .setLanguage(SonargraphBase.JAVA).setContents(DUMMY_CONTENT).build());
        fileSystem.add(TestInputFileBuilder
                .create("projectKey", fileSystem.baseDir(),
                        new File(basePath, "src/com/h2m/C2.java").getCanonicalFile())
                .setLanguage(SonargraphBase.JAVA).setContents(DUMMY_CONTENT).build());

        final SonargraphSensor sonargraphSensor = new SonargraphSensor(fileSystem, metricFinder, sonargraphMetrics,
                sonargraphRules.getRulesProvider());
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
        final int refactoringCount = 1;
        final Collection<Issue> issues = context.allIssues();
        assertEquals("Wrong number of issues", thresholdViolationErrorCount + thresholdViolationWarningCount
                + duplicatesCount + todoCount + refactoringCount, issues.size());

        checkIssueCount("Wrong number of threshold errors", "ThresholdViolationError", thresholdViolationErrorCount,
                issues);
        checkIssueCount("Wrong number of threshold warnings", "ThresholdViolation", thresholdViolationWarningCount,
                issues);
        checkIssueCount("Wrong number of duplicates", "DuplicateCodeBlock", duplicatesCount, issues);
        checkIssueCount("Wrong number of todos", "Todo", todoCount, issues);
        checkIssueCount("Wrong number of refactorings", "RenameRefactoring", refactoringCount, issues);

        //Check for resolutions
        final String todoRuleKey = SonargraphBase.createRuleKey("Todo");
        final Issue todo = issues.stream().filter(issue -> issue.ruleKey().rule().equals(todoRuleKey)).findFirst()
                .get();
        final String expectedTodoMessage = "[Todo] assignee='Dietmar' priority='Medium' description='Review.'";
        assertTrue("Wrong message: " + todo.primaryLocation().message(),
                todo.primaryLocation().message().startsWith(expectedTodoMessage));

        final String thresholdRuleKey = SonargraphBase.createRuleKey("ThresholdViolation");
        final Issue thresholdWarning = issues.stream().filter(issue -> issue.ruleKey().rule().equals(thresholdRuleKey)
                && issue.primaryLocation().inputComponent().key().contains("C1.java")).findFirst().get();
        final String expectedFixMessage = "[Fix: Threshold Violation] assignee='Dietmar' priority='Medium' description='Do it.'";
        assertTrue("Wrong message: " + thresholdWarning.primaryLocation().message(),
                thresholdWarning.primaryLocation().message().startsWith(expectedFixMessage));
    }

    private static void checkIssueCount(final String message, final String sonargraphIssueKey, final int expectedCount,
            final Collection<Issue> issues)
    {
        final String issueKey = SonargraphBase.createRuleKey(sonargraphIssueKey);
        assertEquals(message, expectedCount,
                issues.stream().filter(issue -> issue.ruleKey().rule().equals(issueKey)).count());
    }

    private static void createTestFile(final File moduleBaseDir, final DefaultFileSystem fileSystem, final String path)
            throws IOException
    {
        final File absoluteSourceFile = new File(moduleBaseDir, path);
        final String content = Files.lines(absoluteSourceFile.toPath(), StandardCharsets.UTF_8)
                .collect(Collectors.joining("\n"));
        fileSystem.add(TestInputFileBuilder.create("projectKey", moduleBaseDir, absoluteSourceFile).setContents(content)
                .setLanguage(SonargraphBase.JAVA).build());
    }
}