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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.measure.Metric;
import org.sonar.api.batch.measure.MetricFinder;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.batch.sensor.measure.NewMeasure;
import org.sonar.api.config.Configuration;
import org.sonar.api.scanner.sensor.ProjectSensor;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import com.hello2morrow.sonargraph.integration.access.controller.ControllerFactory;
import com.hello2morrow.sonargraph.integration.access.controller.IInfoProcessor;
import com.hello2morrow.sonargraph.integration.access.controller.IModuleInfoProcessor;
import com.hello2morrow.sonargraph.integration.access.controller.ISonargraphSystemController;
import com.hello2morrow.sonargraph.integration.access.controller.ISystemInfoProcessor;
import com.hello2morrow.sonargraph.integration.access.foundation.Result;
import com.hello2morrow.sonargraph.integration.access.foundation.Utility;
import com.hello2morrow.sonargraph.integration.access.model.IDuplicateCodeBlockIssue;
import com.hello2morrow.sonargraph.integration.access.model.IDuplicateCodeBlockOccurrence;
import com.hello2morrow.sonargraph.integration.access.model.IIssue;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;
import com.hello2morrow.sonargraph.integration.access.model.IMetricLevel;
import com.hello2morrow.sonargraph.integration.access.model.IMetricValue;
import com.hello2morrow.sonargraph.integration.access.model.IModule;
import com.hello2morrow.sonargraph.integration.access.model.INamedElement;
import com.hello2morrow.sonargraph.integration.access.model.IResolution;
import com.hello2morrow.sonargraph.integration.access.model.ISoftwareSystem;
import com.hello2morrow.sonargraph.integration.access.model.ISourceFile;
import com.hello2morrow.sonargraph.integration.access.model.ResolutionType;

public final class SonargraphSensor implements ProjectSensor
{
    private static final Logger LOGGER = Loggers.get(SonargraphSensor.class);
    private static final int ZERO_LINE_OFFSET = 0;

    static final class ActiveRulesAndMetrics
    {
        private final Map<String, Map<String, ActiveRule>> languageToActiveRules;
        private final Map<String, Metric<Serializable>> metrics;

        ActiveRulesAndMetrics(final Map<String, Map<String, ActiveRule>> languageToActiveRules,
                final Map<String, Metric<Serializable>> metrics)
        {
            this.languageToActiveRules = languageToActiveRules;
            this.metrics = metrics;
        }

        Map<String, ActiveRule> getActiveRules(final String language)
        {
            final Map<String, ActiveRule> rules = languageToActiveRules.get(language);
            if (rules != null)
            {
                return Collections.unmodifiableMap(rules);
            }

            return null;
        }

        Map<String, Metric<Serializable>> getMetrics()
        {
            return Collections.unmodifiableMap(metrics);
        }

        Set<String> getLanguages()
        {
            return Collections.unmodifiableSet(languageToActiveRules.keySet());
        }
    }

    private static class ModulesLanguageCounter
    {
        private final int count;
        private final String language;

        public ModulesLanguageCounter(final String language, final int count)
        {
            this.language = language;
            this.count = count;
        }

        public int getCount()
        {
            return count;
        }

        public String getLanguage()
        {
            return language;
        }
    }

    private final FileSystem sqFileSystem;
    private final MetricFinder sqMetricFinder;
    private final SonargraphMetrics sgMetrics;
    private final SonargraphRulesProvider sgRulesProvider;

    private boolean isUpdateOfServerCustomMetricsNeeded = false;
    private boolean isUpdateOfScannerCustomMetricsNeeded = false;

    //[IK] In contrast to metrics, rules are dynamically provided to the client, so there cannot be a situation that the scanner needs updating.
    private boolean isUpdateOfServerCustomRulesNeeded = false;

    public SonargraphSensor(final FileSystem fileSystem, final MetricFinder metricFinder,
            final SonargraphMetrics sgMetrics)
    {
        this(fileSystem, metricFinder, sgMetrics, new SonargraphRulesProvider());
    }

    SonargraphSensor(final FileSystem fileSystem, final MetricFinder metricFinder, final SonargraphMetrics sgMetrics,
            final SonargraphRulesProvider sgRulesProvider)
    {
        this.sqFileSystem = fileSystem;
        this.sqMetricFinder = metricFinder;
        this.sgMetrics = sgMetrics;
        this.sgRulesProvider = sgRulesProvider;
    }

    @Override
    public void describe(final SensorDescriptor descriptor)
    {
        descriptor.name(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
    }

    @Override
    public void execute(final SensorContext sensorContext)
    {
        isUpdateOfServerCustomMetricsNeeded = false;
        isUpdateOfScannerCustomMetricsNeeded = false;
        isUpdateOfServerCustomRulesNeeded = false;

        sgRulesProvider.loadStandardRules();
        sgRulesProvider.loadCustomRules();

        final String projectKey = sensorContext.config().get("sonar.projectKey").orElse("<unknown>");
        LOGGER.info("{}: Processing SonarQube project '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                projectKey);

        final ISonargraphSystemController sgController = ControllerFactory.createController();
        final File reportFile = getReportFile(sensorContext.config());
        Result loadReport;
        if (reportFile != null)
        {
            final File systemBaseDir = getSystemBaseDirectory(sensorContext.config());
            if (systemBaseDir == null)
            {
                loadReport = sgController.loadSystemReport(reportFile);
            }
            else
            {
                LOGGER.info("{}: Adjusting baseDirectory of Sonargraph system to '{}'",
                        SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, systemBaseDir);
                loadReport = sgController.loadSystemReport(reportFile, systemBaseDir);
            }
            if (loadReport.isSuccess())
            {
                process(sensorContext, sgController);
            }
            else
            {
                LOGGER.error("{}: {}", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, loadReport.toString());
            }
            LOGGER.info("{}: Finished processing SonarQube project '{}'",
                    SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, projectKey);
        }
    }

    private File resolveReadableFile(String path)
    {
        if (path == null || path.isEmpty())
        {
            return null;
        }

        File result = sqFileSystem.resolvePath(path);

        if (result != null && result.canRead())
        {
            LOGGER.info("{}: Using XML report file '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                    result.getAbsolutePath());
            return result;
        }
        return null;
    }

    private File getReportFile(final Configuration config)
    {
        File report = null;
        String path = null;
        final Optional<String> configuredRelativeReportPathOpt = config.get(SonargraphBase.XML_REPORT_FILE_PATH_KEY);
        if (configuredRelativeReportPathOpt.isPresent())
        {
            final String configuredRelativeReportPath = configuredRelativeReportPathOpt.get();
            if (!configuredRelativeReportPath.isEmpty())
            {
                path = configuredRelativeReportPath;
                report = resolveReadableFile(path);
                if (report != null)
                {
                    return report;
                }
            }
        }

        final Optional<String> scannerApp = config.get("sonar.scanner.app");
        if (scannerApp.isPresent())
        {
            final String scanner = scannerApp.get();
            LOGGER.info("Determine report path from scanner app {}", scanner);
            path = SonargraphSensor.getPathForScannerApp(config, scanner);
            report = resolveReadableFile(path);
            if (report != null)
            {
                return report;
            }
        }

        // Try Maven default target directory
        path = "target/" + SonargraphBase.XML_REPORT_FILE_PATH_DEFAULT;
        report = resolveReadableFile(path);
        if (report != null)
        {
            return report;
        }

        // Try Gradle default target directory
        path = "build/" + SonargraphBase.XML_REPORT_FILE_PATH_DEFAULT;
        report = resolveReadableFile(path);
        if (report != null)
        {
            return report;
        }
        LOGGER.error("{}: XML report file not found", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
        return null;
    }

    static String getPathForScannerApp(final Configuration configuration, final String scannerApp)
    {
        if (scannerApp.equals("ScannerMaven"))
        {
            final Optional<String> buildDir = configuration.get("sonar.projectBuildDir");
            if (buildDir.isPresent())
            {
                return new File(buildDir.get(), SonargraphBase.XML_REPORT_FILE_PATH_DEFAULT).getAbsolutePath();
            }
        }
        else if (scannerApp.equals("ScannerGradle"))
        {
            final Optional<String> workingDirectory = configuration.get("sonar.working.directory");
            if (workingDirectory.isPresent())
            {
                final File buildDir = new File(workingDirectory.get()).getParentFile();
                return new File(buildDir, SonargraphBase.XML_REPORT_FILE_PATH_DEFAULT).getAbsolutePath();
            }
        }

        return null;
    }

    private File getSystemBaseDirectory(final Configuration config)
    {
        final Optional<String> basePathOpt = config.get(SonargraphBase.SONARGRAPH_BASE_DIR_KEY);
        if (!basePathOpt.isPresent())
        {
            return null;
        }

        final String path = basePathOpt.get();
        final File baseDir = sqFileSystem.resolvePath(path);
        if (baseDir.exists())
        {
            return baseDir;
        }

        return null;
    }

    private void process(final SensorContext sensorContext, final ISonargraphSystemController sgController)
    {
        final ISoftwareSystem softwareSystem = sgController.getSoftwareSystem();

        final ActiveRulesAndMetrics rulesAndMetrics = createActiveRulesAndMetrics(sensorContext);
        final ISystemInfoProcessor systemInfoProcessor = sgController.createSystemInfoProcessor();

        final String language = determineLanguage(softwareSystem, rulesAndMetrics);
        if (language == null)
        {
            return;
        }

        processSystem(sensorContext, softwareSystem, systemInfoProcessor, rulesAndMetrics, language);
        processModules(sensorContext, sgController, rulesAndMetrics, systemInfoProcessor, language);
        updateRules();
    }

    private void updateRules()
    {
        if (isUpdateOfServerCustomMetricsNeeded || isUpdateOfScannerCustomMetricsNeeded)
        {
            //New custom metrics have been introduced.
            try
            {
                final File customMetricsFile = sgMetrics.getMetricsProvider()
                        .saveCustomMetricProperties("Custom Sonargraph Metrics");
                if (isUpdateOfServerCustomMetricsNeeded)
                {
                    LOGGER.warn(
                            "{}: Custom metrics have been updated, file {} needs to be copied to the directory <user-home>/.{} of the SonarQube server."
                                    + " After a restart of the server the values for those additional metrics will be saved on the next SonarQube analysis.",
                            SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, customMetricsFile.getAbsolutePath(),
                            SonargraphBase.SONARGRAPH_PLUGIN_KEY);
                }
                else
                {
                    LOGGER.warn(
                            "{}: Local custom metrics configuration file '{}' has been updated. Values for those additional metrics will be saved on the next SonarQube analysis.",
                            SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, customMetricsFile.getAbsolutePath(),
                            SonargraphBase.SONARGRAPH_PLUGIN_KEY);
                }
            }
            catch (final IOException e)
            {
                LOGGER.error(
                        SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Unable to save custom metrics file.",
                        e);
            }
        }

        if (isUpdateOfServerCustomRulesNeeded)
        {
            //New custom rules have been introduced.
            try
            {
                final File customRulesFile = sgRulesProvider.saveCustomRuleProperties("Custom Sonargraph Rules");
                LOGGER.warn(
                        "{}: Custom rules have been updated, file {} needs to be copied to the directory <user-home>/.{} of the SonarQube server."
                                + " After a restart of the server the additional rules can be activated in the quality profile"
                                + " and issues will then be created on the next SonarQube analysis.",
                        SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, customRulesFile.getAbsolutePath(),
                        SonargraphBase.SONARGRAPH_PLUGIN_KEY);
            }
            catch (final IOException e)
            {
                LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Unable to save custom ruels file.",
                        e);
            }
        }
    }

    private void processModules(final SensorContext sensorContext, final ISonargraphSystemController sgController,
            final ActiveRulesAndMetrics rulesAndMetrics, final ISystemInfoProcessor systemInfoProcessor,
            final String language)
    {
        for (final Entry<String, IModule> nextEntry : systemInfoProcessor.getModules().entrySet())
        {
            final IModule module = nextEntry.getValue();
            final IModuleInfoProcessor moduleInfoProcessor = sgController.createModuleInfoProcessor(module);

            final String sqModuleLanguage = SonargraphBase.convertLanguage(module.getLanguage());
            if (sqModuleLanguage != null)
            {
                if (sqModuleLanguage.equals(language))
                {
                    processModule(sensorContext, moduleInfoProcessor, rulesAndMetrics, language);
                }
                else
                {
                    LOGGER.warn("{}: Ignoring module '{}', since language '{}' is not active for project",
                            SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, module.getName(), module.getLanguage());
                }
            }
            else
            {
                LOGGER.warn(
                        "{}: Ignoring module '{}', since language '{}' is not supported by Sonargraph SonarQube Plugin",
                        SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, module.getName(), module.getLanguage());
            }
        }
    }

    private String determineLanguage(final ISoftwareSystem softwareSystem, final ActiveRulesAndMetrics rulesAndMetrics)
    {
        final List<ModulesLanguageCounter> languagesOfModules = determineLanguagesOfSystem(softwareSystem);
        if (languagesOfModules.isEmpty())
        {
            LOGGER.error("{}: No languages could be determined from the modules of the Sonargraph system.",
                    SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
            return null;
        }

        final String language = getMostUsedActiveLanguage(languagesOfModules, rulesAndMetrics);
        if (language == null)
        {
            LOGGER.error("{}: No rules are active that match the languages of the Sonargraph system: {}",
                    SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, languagesOfModules.stream()
                            .map(ModulesLanguageCounter::getLanguage).collect(Collectors.joining(", ")));
            return null;
        }
        if (languagesOfModules.size() > 1)
        {
            LOGGER.warn("{}: Several languages are detected in Sonargraph system. "
                    + "As support for multi-language systems has not been implemented, only information for language {} will be processed",
                    SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, language);
        }
        return language;
    }

    private void processSystem(final SensorContext sensorContext, final ISoftwareSystem softwareSystem,
            final ISystemInfoProcessor systemInfoProcessor, final ActiveRulesAndMetrics rulesAndMetrics,
            final String language)
    {

        processSystemMetrics(sensorContext, sensorContext.project(), softwareSystem, systemInfoProcessor,
                rulesAndMetrics);
        final Map<String, ActiveRule> keyToRule = rulesAndMetrics.getActiveRules(language);
        if (keyToRule == null || keyToRule.isEmpty())
        {
            LOGGER.warn("Failed to find any rules for language {}", language);
            return;
        }

        final List<IIssue> systemIssues = systemInfoProcessor.getIssues(issue -> !issue.isIgnored()
                && (issue.getIssueType().getCategory().getName().equals(SonargraphBase.QUALITY_GATE_ISSUE_CATEGORY))
                || (!SonargraphBase.ignoreIssueType(issue.getIssueType())
                        && issue.getAffectedNamedElements().contains(softwareSystem)));

        for (final IIssue nextIssue : systemIssues)
        {
            final ActiveRule nextRule = keyToRule
                    .get(SonargraphBase.createRuleKeyToCheck(nextIssue.getIssueType(), nextIssue.getSeverity()));
            if (nextRule != null)
            {
                createSqIssue(sensorContext, sensorContext.project(), nextRule,
                        createIssueDescription(systemInfoProcessor, nextIssue), null);
            }
            else
            {
                createCustomRuleForIssue(nextIssue);
            }
        }

        final List<IIssue> ignoredErrorOrWarningIssues = systemInfoProcessor
                .getIssues(issue -> SonargraphBase.isIgnoredErrorOrWarningIssue(issue.getIssueType()));
        if (!ignoredErrorOrWarningIssues.isEmpty())
        {
            LOGGER.warn("{}: Found {} system setup related error/warning issue(s)",
                    SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, ignoredErrorOrWarningIssues.size());
            int i = 1;
            for (final IIssue nextIssue : ignoredErrorOrWarningIssues)
            {
                LOGGER.warn("[{}] {}", i, nextIssue.getPresentationName());
                for (final INamedElement nextAffected : nextIssue.getAffectedNamedElements())
                {
                    LOGGER.warn(" - {} [{}]", nextAffected.getName(), nextAffected.getPresentationKind());
                }
                i++;
            }
        }
    }

    private String getMostUsedActiveLanguage(final List<ModulesLanguageCounter> languagesOfModules,
            final ActiveRulesAndMetrics rulesAndMetrics)
    {
        final Set<String> configuredLanguages = rulesAndMetrics.getLanguages();
        for (final ModulesLanguageCounter next : languagesOfModules)
        {
            if (configuredLanguages.contains(next.getLanguage()))
            {
                return next.getLanguage();
            }
        }
        return null;
    }

    private List<ModulesLanguageCounter> determineLanguagesOfSystem(final ISoftwareSystem softwareSystem)
    {
        final Map<String, Integer> languagesToModuleCount = new HashMap<>();
        for (final Map.Entry<String, IModule> next : softwareSystem.getModules().entrySet())
        {
            final String language = next.getValue().getLanguage();
            final Integer count = languagesToModuleCount.computeIfAbsent(language, k -> Integer.valueOf(0));
            languagesToModuleCount.put(language, count + 1);
        }

        final List<ModulesLanguageCounter> sorted = new ArrayList<>();
        for (final Map.Entry<String, Integer> next : languagesToModuleCount.entrySet())
        {
            final String sqLanguage = SonargraphBase.convertLanguage(next.getKey());
            if (sqLanguage == null)
            {
                LOGGER.warn("Ignoring {} modules with unsupported language '{}'", next.getValue(), next.getKey());
            }
            else
            {
                sorted.add(new ModulesLanguageCounter(sqLanguage, next.getValue()));
            }
        }
        sorted.sort(Comparator.comparing(ModulesLanguageCounter::getCount).reversed());
        return sorted;
    }

    private void createCustomRuleForIssue(final IIssue issue)
    {
        if (issue.getIssueType().getProvider() != null)
        {
            isUpdateOfServerCustomRulesNeeded = true;
            sgRulesProvider.addCustomRuleForIssue(issue);
        }
    }

    private void processModule(final SensorContext sensorContext, final IModuleInfoProcessor moduleInfoProcessor,
            final ActiveRulesAndMetrics rulesAndMetrics, final String language)
    {
        final Map<String, ActiveRule> keyToRule = rulesAndMetrics.getActiveRules(language);
        if (keyToRule == null || keyToRule.isEmpty())
        {
            LOGGER.warn("No rules activated for language {}", language);
            return;
        }

        final Map<ISourceFile, List<IIssue>> sourceFileIssueMap = moduleInfoProcessor
                .getIssuesForSourceFiles(i -> !i.isIgnored() && !SonargraphBase.ignoreIssueType(i.getIssueType()));
        for (final Entry<ISourceFile, List<IIssue>> issuesPerSourceFile : sourceFileIssueMap.entrySet())
        {
            addIssuesToSourceFile(sensorContext, moduleInfoProcessor, keyToRule, moduleInfoProcessor.getBaseDirectory(),
                    issuesPerSourceFile.getKey(), issuesPerSourceFile.getValue());
        }

        final Map<String, List<IIssue>> directoryIssueMap = moduleInfoProcessor
                .getIssuesForDirectories(i -> !i.isIgnored() && !SonargraphBase.ignoreIssueType(i.getIssueType()));
        for (final Entry<String, List<IIssue>> issuesPerDirectory : directoryIssueMap.entrySet())
        {
            addIssuesToDirectory(sensorContext, moduleInfoProcessor, keyToRule, moduleInfoProcessor.getBaseDirectory(),
                    issuesPerDirectory.getKey(), issuesPerDirectory.getValue());
        }
    }

    private String createIssueDescription(final IInfoProcessor infoProcessor, final IIssue issue, final String detail)
    {
        final StringBuilder result = new StringBuilder();

        final IResolution resolution = infoProcessor.getResolution(issue);
        if (resolution != null)
        {
            final ResolutionType type = resolution.getType();
            switch (type)
            {
            case FIX:
                result.append("[").append(SonargraphBase.toLowerCase(type.toString(), false)).append(": ")
                        .append(issue.getPresentationName()).append("]");
                break;
            case REFACTORING:
                //$FALL-THROUGH$
            case TODO:
                result.append("[").append(issue.getPresentationName()).append("]");
                break;
            case IGNORE:
                //$FALL-THROUGH$
            case NONE:
                //$FALL-THROUGH$
            default:
                assert false : "Unhandled resolution type: " + type;
                break;
            }

            result.append(" assignee='").append(resolution.getAssignee()).append("'");
            result.append(" priority='").append(SonargraphBase.toLowerCase(resolution.getPriority().toString(), false))
                    .append("'");
            result.append(" description='").append(resolution.getDescription()).append("'");
            result.append(" created='").append(resolution.getDate()).append("'");
        }
        else
        {
            result.append("[").append(issue.getPresentationName()).append("]");
        }

        result.append(" ").append(issue.getDescription());
        if (!detail.isEmpty())
        {
            result.append(" ").append(detail);
        }
        result.append(" [").append(issue.getIssueProvider().getPresentationName()).append("]");

        return result.toString();
    }

    private String createIssueDescription(final IModuleInfoProcessor moduleInfoProcessor,
            final IDuplicateCodeBlockIssue duplicateCodeBlockIssue, final IDuplicateCodeBlockOccurrence occurrence,
            final List<IDuplicateCodeBlockOccurrence> others)
    {
        final StringBuilder detail = new StringBuilder();
        detail.append("Line(s) ").append(occurrence.getStartLine()).append("-")
                .append(occurrence.getStartLine() + occurrence.getBlockSize() - 1).append(" duplicate of ");

        for (final IDuplicateCodeBlockOccurrence next : others)
        {
            detail.append(next.getSourceFile().getRelativePath() != null ? next.getSourceFile().getRelativePath()
                    : next.getSourceFile().getPresentationName());
            detail.append(" line(s) ").append(next.getStartLine());
            detail.append("-").append(next.getStartLine() + next.getBlockSize() - 1);
        }

        return createIssueDescription(moduleInfoProcessor, duplicateCodeBlockIssue, detail.toString());
    }

    private String createIssueDescription(final IInfoProcessor infoProcessor, final IIssue issue)
    {
        return createIssueDescription(infoProcessor, issue, "");
    }

    private void createSourceFileIssues(final SensorContext sensorContext,
            final IModuleInfoProcessor moduleInfoProcessor, final ISourceFile sourceFile, final InputFile inputFile,
            final IIssue issue, final ActiveRule rule)
    {
        if (issue instanceof IDuplicateCodeBlockIssue)
        {
            final IDuplicateCodeBlockIssue nextDuplicateCodeBlockIssue = (IDuplicateCodeBlockIssue) issue;
            final List<IDuplicateCodeBlockOccurrence> nextOccurrences = nextDuplicateCodeBlockIssue.getOccurrences();

            for (final IDuplicateCodeBlockOccurrence nextOccurrence : nextOccurrences)
            {
                final ISourceFile nextSourceFileOfOccurence = nextOccurrence.getSourceFile();
                final Optional<ISourceFile> nextOriginalLocationOpt = nextSourceFileOfOccurence.getOriginalLocation();
                if (nextSourceFileOfOccurence.equals(sourceFile)
                        || (nextOriginalLocationOpt.isPresent() && nextOriginalLocationOpt.get().equals(sourceFile)))
                {
                    final List<IDuplicateCodeBlockOccurrence> others = new ArrayList<>(nextOccurrences);
                    others.remove(nextOccurrence);
                    final String issueDescription = createIssueDescription(moduleInfoProcessor,
                            nextDuplicateCodeBlockIssue, nextOccurrence, others);
                    createSqIssue(sensorContext, inputFile, rule, issueDescription,
                            location -> location.at(inputFile.newRange(nextOccurrence.getStartLine(), ZERO_LINE_OFFSET, nextOccurrence.getStartLine() + nextOccurrence.getBlockSize(),
                                    ZERO_LINE_OFFSET)));
                }
            }
        }
        else
        {
            final String issueDescription = createIssueDescription(moduleInfoProcessor, issue);
            createSqIssue(sensorContext, inputFile, rule, issueDescription, location ->
            {
                final int line = issue.getLine();
                final int lineToUse = line <= 0 ? 1 : line;
                location.at(inputFile.newRange(lineToUse, ZERO_LINE_OFFSET, lineToUse+1, ZERO_LINE_OFFSET));
            });
        }
    }

    private void addIssuesToSourceFile(final SensorContext sensorContext,
            final IModuleInfoProcessor moduleInfoProcessor, final Map<String, ActiveRule> keyToRule,
            final String baseDir, final ISourceFile sourceFile, final List<IIssue> issues)
    {
        final String rootDirectoryRelPath = sourceFile.getRelativeRootDirectory();
        final String sourceRelPath = sourceFile.getRelativePath();
        final String sourceFileLocation = Paths.get(baseDir, rootDirectoryRelPath, sourceRelPath).toAbsolutePath()
                .normalize().toString();

        final InputFile inputPath = sqFileSystem.inputFile(
                sqFileSystem.predicates().hasAbsolutePath(sourceFileLocation));
        if (inputPath != null)
        {
            for (final IIssue issue : issues)
            {
                final String ruleKey = SonargraphBase.createRuleKeyToCheck(issue.getIssueType(), issue.getSeverity());
                final ActiveRule nextRule = keyToRule.get(ruleKey);
                if (nextRule != null)
                {
                    try
                    {
                        createSourceFileIssues(sensorContext, moduleInfoProcessor, sourceFile, inputPath, issue,
                                nextRule);
                    }
                    catch (final Exception e)
                    {
                        LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Failed to create issue '"
                                + issue + "'. ", e);
                    }
                }
                else
                {
                    createCustomRuleForIssue(issue);
                }
            }
        }
        else
        {
            LOGGER.error("{}: Failed to locate '{}' at '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                    sourceFile.getFqName(), sourceFileLocation);
        }
    }

    private void addIssuesToDirectory(final SensorContext sensorContext, final IModuleInfoProcessor moduleInfoProcessor,
            final Map<String, ActiveRule> keyToRule, final String baseDir, final String relDirectory,
            final List<IIssue> issues)
    {
        final String directoryPath = Paths.get(baseDir, relDirectory).toAbsolutePath().normalize().toString();
        final InputDir inputDir = sqFileSystem.inputDir(new File(Utility.convertPathToUniversalForm(directoryPath)));

        if (inputDir != null)
        {
            for (final IIssue issue : issues)
            {
                final String ruleKey = SonargraphBase.createRuleKeyToCheck(issue.getIssueType(), issue.getSeverity());
                final ActiveRule nextRule = keyToRule.get(ruleKey);
                if (nextRule != null)
                {
                    try
                    {
                        createSqIssue(sensorContext, inputDir, nextRule,
                                createIssueDescription(moduleInfoProcessor, issue), null);
                    }
                    catch (final Exception e)
                    {
                        LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Failed to create issue '"
                                + issue + "'. ", e);
                    }
                }
                else
                {
                    createCustomRuleForIssue(issue);
                }
            }
        }
        else
        {
            LOGGER.error("{}: Failed to locate directory resource: '{}'\nBaseDir: {}\nrelDirectory:'{}'",
                    SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, directoryPath, baseDir, relDirectory);
        }
    }

    private void processSystemMetrics(final SensorContext sensorContext, final InputComponent inputComponent,
            final ISoftwareSystem softwareSystem, final ISystemInfoProcessor systemInfoProcessor,
            final ActiveRulesAndMetrics rulesAndMetrics)
    {
        final Optional<IMetricLevel> systemLevelOpt = systemInfoProcessor.getMetricLevel(IMetricLevel.SYSTEM);
        if (!systemLevelOpt.isPresent())
        {
            LOGGER.error("{}: Sonargraph report is missing system-level metrics!",
                    SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
            return;
        }

        final IMetricLevel systemLevel = systemLevelOpt.get();
        final SonargraphMetricsProvider customMetricsProvider = sgMetrics.getMetricsProvider();

        for (final IMetricId nextMetricId : systemInfoProcessor.getMetricIdsForLevel(systemLevel))
        {
            final String metricKey = SonargraphBase.createMetricKeyFromStandardName(nextMetricId.getName());
            final Metric<Serializable> metric = rulesAndMetrics.getMetrics().get(metricKey);

            if (metric == null)
            {
                customMetricsProvider.addCustomMetric(nextMetricId);
                LOGGER.warn("{}: Custom metric added '{}'.", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                        nextMetricId.getName());
                isUpdateOfServerCustomMetricsNeeded = true;
                /**
                 * Custom metric has now been added and needs to be persisted and loaded at SonarQube server startup. Only then, measures can be
                 * saved. There is nothing left that can be done here and now.
                 */
                continue;
            }

            final Optional<IMetricValue> metricValueOpt = systemInfoProcessor.getMetricValueForElement(nextMetricId,
                    systemLevel, softwareSystem.getFqName());
            if (metricValueOpt.isPresent())
            {
                try
                {
                    /**
                     * Throws UnsupportedOperationException, if custom metric is present on server side, but was not available at scanner start on
                     * scanner side.
                     */
                    createSqMeasure(sensorContext, inputComponent, metric, metricValueOpt.get());
                }
                catch (final UnsupportedOperationException e)
                {
                    customMetricsProvider.addCustomMetric(nextMetricId);
                    LOGGER.warn("{}: Custom metric already existed on server but not on scanner side '{}'. ",
                            SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, nextMetricId.getName());
                    isUpdateOfScannerCustomMetricsNeeded = true;

                    /**
                     * Custom metric has now been added and needs to be persisted and loaded at SonarScanner startup. Only then, measures can be
                     * saved. There is nothing left that can be done here and now.
                     */
                }
            }
            else
            {
                LOGGER.warn("{}: No value found for metric '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                        metricKey);
            }
        }
    }

    private void createSqIssue(final SensorContext sensorContext, final InputComponent inputComponent,
            final ActiveRule rule, final String msg, final Consumer<NewIssueLocation> consumer)
    {
        final NewIssue sonarqubeIssue = sensorContext.newIssue();
        sonarqubeIssue.forRule(rule.ruleKey());

        final NewIssueLocation sqIssueLoc = sonarqubeIssue.newLocation();
        sqIssueLoc.on(inputComponent);
        sqIssueLoc.message(msg);
        sonarqubeIssue.at(sqIssueLoc);

        if (consumer != null)
        {
            consumer.accept(sqIssueLoc);
        }

        sonarqubeIssue.save();
    }

    private ActiveRulesAndMetrics createActiveRulesAndMetrics(final SensorContext sensorContext)
    {
        final Map<String, Map<String, ActiveRule>> languageToActiveRules = new HashMap<>();
        HashMap<String, ActiveRule> activeRules;
        for (final String nextLanguage : SonargraphBase.SUPPORTED_LANGUAGES)
        {
            final Collection<ActiveRule> rules = sensorContext.activeRules()
                    .findByRepository(SonargraphRules.getRepositoryKeyForLanguage(nextLanguage));
            if (rules.isEmpty())
            {
                continue;
            }

            activeRules = new HashMap<>();
            languageToActiveRules.put(nextLanguage, activeRules);
            for (final ActiveRule rule : rules)
            {
                activeRules.put(rule.ruleKey().rule(), rule);
            }
        }

        LOGGER.info("{}: {} rule(s) activated", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                languageToActiveRules.size());

        final Map<String, Metric<Serializable>> metrics = sqMetricFinder.findAll().stream()
                .filter(m -> m.key().startsWith(SonargraphBase.METRIC_ID_PREFIX))
                .collect(Collectors.toMap(Metric::key, m -> m));
        LOGGER.info("{}: {} metric(s) defined", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, metrics.size());

        return new ActiveRulesAndMetrics(languageToActiveRules, metrics);
    }

    @SuppressWarnings("unchecked")
    private void createSqMeasure(final SensorContext sensorContext, final InputComponent inputComponent,
            final Metric<? extends Serializable> metric, final IMetricValue metricValue)
    {
        if (metricValue.getId().isFloat())
        {
            final NewMeasure<Double> sqMeasure = sensorContext.<Double> newMeasure();
            sqMeasure.forMetric((Metric<Double>) metric);
            sqMeasure.on(inputComponent);
            sqMeasure.withValue(Double.valueOf(metricValue.getValue().doubleValue()));
            sqMeasure.save();
        }
        else
        {
            final NewMeasure<Integer> sqMeasure = sensorContext.<Integer> newMeasure();
            sqMeasure.forMetric((Metric<Integer>) metric);
            sqMeasure.on(inputComponent);
            sqMeasure.withValue(Integer.valueOf(metricValue.getValue().intValue()));
            sqMeasure.save();
        }
    }
}