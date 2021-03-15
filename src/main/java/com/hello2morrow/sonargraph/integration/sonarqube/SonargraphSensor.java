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
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.batch.fs.internal.DefaultTextPointer;
import org.sonar.api.batch.fs.internal.DefaultTextRange;
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

        ActiveRulesAndMetrics(final Map<String, Map<String, ActiveRule>> languageToActiveRules, final Map<String, Metric<Serializable>> metrics)
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

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + count;
            result = prime * result + ((language == null) ? 0 : language.hashCode());
            return result;
        }

        @Override
        public boolean equals(final Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (getClass() != obj.getClass())
            {
                return false;
            }
            final ModulesLanguageCounter other = (ModulesLanguageCounter) obj;
            if (count != other.count)
            {
                return false;
            }
            if (language == null)
            {
                if (other.language != null)
                {
                    return false;
                }
            }
            else if (!language.equals(other.language))
            {
                return false;
            }
            return true;
        }

    }

    private final FileSystem sonarqubeFileSystem;
    private final MetricFinder sonarqubeMetricFinder;
    private final SonargraphMetrics sonargraphMetrics;
    private final SonargraphRulesProvider sonargraphRulesProvider;

    private boolean isUpdateOfServerCustomMetricsNeeded = false;
    private boolean isUpdateOfScannerCustomMetricsNeeded = false;

    //[IK] In contrast to metrics, rules are dynamically provided to the client, so there cannot be a situation that the scanner needs updating.
    private boolean isUpdateOfServerCustomRulesNeeded = false;
    private String m_language;

    public SonargraphSensor(final FileSystem fileSystem, final MetricFinder metricFinder, final SonargraphMetrics sonargraphMetrics)
    {
        this(fileSystem, metricFinder, sonargraphMetrics, new SonargraphRulesProvider());
    }

    SonargraphSensor(final FileSystem fileSystem, final MetricFinder metricFinder, final SonargraphMetrics sonargraphMetrics,
            final SonargraphRulesProvider sonargraphRulesProvider)
    {
        this.sonarqubeFileSystem = fileSystem;
        this.sonarqubeMetricFinder = metricFinder;
        this.sonargraphMetrics = sonargraphMetrics;
        this.sonargraphRulesProvider = sonargraphRulesProvider;
    }

    @Override
    public void describe(final SensorDescriptor descriptor)
    {
        descriptor.name(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
    }

    @Override
    public void execute(final SensorContext context)
    {
        isUpdateOfServerCustomMetricsNeeded = false;
        isUpdateOfScannerCustomMetricsNeeded = false;
        isUpdateOfServerCustomRulesNeeded = false;

        sonargraphRulesProvider.loadStandardRules();
        sonargraphRulesProvider.loadCustomRules();

        final String projectKey = context.config().get("sonar.projectKey").orElse("<unknown>");
        LOGGER.info("{}: Processing SonarQube project '{}", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, projectKey);

        final File reportFile = getReportFile(context.config());
        if (reportFile != null)
        {
            final ISonargraphSystemController sgController = ControllerFactory.createController();

            final File systemBaseDir = getSystemBaseDirectory(context.config());
            Result loadReportResult;
            if (systemBaseDir == null)
            {
                loadReportResult = sgController.loadSystemReport(reportFile);
            }
            else
            {
                LOGGER.info("{}: Adjusting baseDirectory of Sonargraph system to '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                        systemBaseDir);
                loadReportResult = sgController.loadSystemReport(reportFile, systemBaseDir);
            }
            if (loadReportResult.isSuccess())
            {
                process(context, sgController);
            }
            else
            {
                LOGGER.error("{}: {}", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, loadReportResult.toString());
            }
        }

        LOGGER.info("{}: Finished processing SonarQube project '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, projectKey);
    }

    private File getReportFile(final Configuration configuration)
    {
        String relativePath = null;
        final Optional<String> configuredRelativeReportPathOpt = configuration.get(SonargraphBase.XML_REPORT_FILE_PATH_KEY);
        if (configuredRelativeReportPathOpt.isPresent())
        {
            final String configuredRelativeReportPath = configuredRelativeReportPathOpt.get();
            if (!configuredRelativeReportPath.isEmpty())
            {
                relativePath = configuredRelativeReportPath;
            }
        }

        if (relativePath == null)
        {
            final Optional<String> scannerApp = configuration.get("sonar.scanner.app");
            if (scannerApp.isPresent())
            {
                relativePath = SonargraphSensor.getRelativePathForScannerApp(configuration, scannerApp.get());
            }
        }

        if (relativePath == null)
        {
            relativePath = "target/" + SonargraphBase.XML_REPORT_FILE_PATH_DEFAULT;
            LOGGER.info("{}: XML report file path not configured - using default '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                    relativePath);
        }

        final File reportFile = sonarqubeFileSystem.resolvePath(relativePath);
        if (reportFile.exists())
        {
            LOGGER.info("{}: Using XML report file '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, reportFile.getAbsolutePath());
            return reportFile;
        }

        LOGGER.warn("{}: XML report file '{}' not found", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, reportFile.getAbsolutePath());
        return null;
    }

    static String getRelativePathForScannerApp(final Configuration configuration, final String scannerApp)
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

    private File getSystemBaseDirectory(final Configuration configuration)
    {
        final Optional<String> basePathOpt = configuration.get(SonargraphBase.SONARGRAPH_BASE_DIR_KEY);
        if (!basePathOpt.isPresent())
        {
            return null;
        }

        final String path = basePathOpt.get();
        final File baseDir = sonarqubeFileSystem.resolvePath(path);
        if (baseDir.exists())
        {
            return baseDir;
        }

        return null;
    }

    private void process(final SensorContext context, final ISonargraphSystemController sonargraphController)
    {
        final ISoftwareSystem softwareSystem = sonargraphController.getSoftwareSystem();

        final ActiveRulesAndMetrics rulesAndMetrics = createActiveRulesAndMetrics(context);
        final ISystemInfoProcessor systemInfoProcessor = sonargraphController.createSystemInfoProcessor();
        if (!processSystem(context, softwareSystem, systemInfoProcessor, rulesAndMetrics))
        {
            return;
        }

        for (final Entry<String, IModule> nextEntry : systemInfoProcessor.getModules().entrySet())
        {
            final IModule module = nextEntry.getValue();
            final IModuleInfoProcessor moduleInfoProcessor = sonargraphController.createModuleInfoProcessor(module);

            final String sqModuleLanguage = SonargraphBase.convertLanguage(module.getLanguage());
            if (sqModuleLanguage != null)
            {
                if (sqModuleLanguage.equals(m_language))
                {
                    processModule(context, moduleInfoProcessor, rulesAndMetrics, m_language);
                }
                else
                {
                    LOGGER.warn("{}: Ignoring module '{}', since language '{}' is not active for project",
                            SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, module.getName(), module.getLanguage());
                }
            }
            else
            {
                LOGGER.warn("{}: Ignoring module '{}', since language '{}' is not supported by Sonargraph SonarQube Plugin",
                        SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, module.getName(), module.getLanguage());
            }
        }

        if (isUpdateOfServerCustomMetricsNeeded || isUpdateOfScannerCustomMetricsNeeded)
        {
            //New custom metrics have been introduced.
            try
            {
                final File customMetricsFile = sonargraphMetrics.getMetricsProvider().saveCustomMetricProperties("Custom Sonargraph Metrics");
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
                LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Unable to save custom metrics file.", e);
            }
        }

        if (isUpdateOfServerCustomRulesNeeded)
        {
            //New custom rules have been introduced.
            try
            {
                final File customRulesFile = sonargraphRulesProvider.saveCustomRuleProperties("Custom Sonargraph Rules");
                LOGGER.warn(
                        "{}: Custom rules have been updated, file {} needs to be copied to the directory <user-home>/.{} of the SonarQube server."
                                + " After a restart of the server the additional rules can be activated in the quality profile"
                                + " and issues will then be created on the next SonarQube analysis.",
                        SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, customRulesFile.getAbsolutePath(), SonargraphBase.SONARGRAPH_PLUGIN_KEY);
            }
            catch (final IOException e)
            {
                LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Unable to save custom ruels file.", e);
            }
        }
    }

    private boolean processSystem(final SensorContext context, final ISoftwareSystem softwareSystem, final ISystemInfoProcessor systemInfoProcessor,
            final ActiveRulesAndMetrics rulesAndMetrics)
    {
        processSystemMetrics(context, context.project(), softwareSystem, systemInfoProcessor, rulesAndMetrics);

        final List<IIssue> systemIssues = systemInfoProcessor.getIssues(
                issue -> !issue.isIgnored() && (issue.getIssueType().getCategory().getName().equals(SonargraphBase.QUALITY_GATE_ISSUE_CATEGORY))
                        || (!SonargraphBase.ignoreIssueType(issue.getIssueType()) && issue.getAffectedNamedElements().contains(softwareSystem)));

        final List<ModulesLanguageCounter> languagesOfModules = determineLanguagesOfSystem(softwareSystem);
        if (languagesOfModules.isEmpty())
        {
            LOGGER.error("{}: No languages could be determined from the modules of the Sonargraph system.",
                    SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
            return false;
        }

        m_language = getMostUsedActiveLanguage(languagesOfModules, rulesAndMetrics);
        if (m_language == null)
        {
            LOGGER.error("{}: No rules are active that match the languages or the Sonargraph system: {}",
                    SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                    languagesOfModules.stream().map(m -> m.getLanguage()).collect(Collectors.joining(", ")));
            return false;
        }
        if (languagesOfModules.size() > 1)
        {
            LOGGER.warn(
                    "{}: Several languages are detected in Sonargraph system. "
                            + "As support for multi-language systems has not been implemented, only information for language {} will be processed",
                    SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, m_language);
        }

        final Map<String, ActiveRule> keyToRule = rulesAndMetrics.getActiveRules(m_language);
        for (final IIssue nextIssue : systemIssues)
        {
            final ActiveRule nextRule = keyToRule.get(SonargraphBase.createRuleKeyToCheck(nextIssue.getIssueType(), nextIssue.getSeverity()));
            if (nextRule != null)
            {
                createSonarqubeIssue(context, context.project(), nextRule, createIssueDescription(systemInfoProcessor, nextIssue), null);
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
            LOGGER.warn("{}: Found {} system setup related error/warning issue(s)", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                    ignoredErrorOrWarningIssues.size());
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

        return true;
    }

    private String getMostUsedActiveLanguage(final List<ModulesLanguageCounter> languagesOfModules, final ActiveRulesAndMetrics rulesAndMetrics)
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
            final Integer count = languagesToModuleCount.computeIfAbsent(language, k -> new Integer(0));
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
            sonargraphRulesProvider.addCustomRuleForIssue(issue);
        }
    }

    private void processModule(final SensorContext context, final IModuleInfoProcessor moduleInfoProcessor,
            final ActiveRulesAndMetrics rulesAndMetrics, final String language)
    {
        final Map<String, ActiveRule> keyToRule = rulesAndMetrics.getActiveRules(language);
        final Map<ISourceFile, List<IIssue>> sourceFileIssueMap = moduleInfoProcessor
                .getIssuesForSourceFiles(i -> !i.isIgnored() && !SonargraphBase.ignoreIssueType(i.getIssueType()));
        for (final Entry<ISourceFile, List<IIssue>> issuesPerSourceFile : sourceFileIssueMap.entrySet())
        {
            addIssuesToSourceFile(context, moduleInfoProcessor, keyToRule, moduleInfoProcessor.getBaseDirectory(), issuesPerSourceFile.getKey(),
                    issuesPerSourceFile.getValue());
        }

        final Map<String, List<IIssue>> directoryIssueMap = moduleInfoProcessor
                .getIssuesForDirectories(i -> !i.isIgnored() && !SonargraphBase.ignoreIssueType(i.getIssueType()));
        for (final Entry<String, List<IIssue>> issuesPerDirectory : directoryIssueMap.entrySet())
        {
            addIssuesToDirectory(context, moduleInfoProcessor, keyToRule, moduleInfoProcessor.getBaseDirectory(), issuesPerDirectory.getKey(),
                    issuesPerDirectory.getValue());
        }
    }

    private String createIssueDescription(final IInfoProcessor infoProcessor, final IIssue issue, final String detail)
    {
        final StringBuilder description = new StringBuilder();

        final IResolution resolution = infoProcessor.getResolution(issue);
        if (resolution != null)
        {
            final ResolutionType type = resolution.getType();
            switch (type)
            {
            case FIX:
                description.append("[").append(SonargraphBase.toLowerCase(type.toString(), false)).append(": ").append(issue.getPresentationName())
                        .append("]");
                break;
            case REFACTORING:
                //$FALL-THROUGH$
            case TODO:
                description.append("[").append(issue.getPresentationName()).append("]");
                break;
            case IGNORE:
                //$FALL-THROUGH$
            case NONE:
                //$FALL-THROUGH$
            default:
                assert false : "Unhandled resolution type: " + type;
                break;
            }

            description.append(" assignee='").append(resolution.getAssignee()).append("'");
            description.append(" priority='").append(SonargraphBase.toLowerCase(resolution.getPriority().toString(), false)).append("'");
            description.append(" description='").append(resolution.getDescription()).append("'");
            description.append(" created='").append(resolution.getDate()).append("'");
        }
        else
        {
            description.append("[").append(issue.getPresentationName()).append("]");
        }

        description.append(" ").append(issue.getDescription());
        if (!detail.isEmpty())
        {
            description.append(" ").append(detail);
        }
        description.append(" [").append(issue.getIssueProvider().getPresentationName()).append("]");

        return description.toString();
    }

    private String createIssueDescription(final IModuleInfoProcessor moduleInfoProcessor, final IDuplicateCodeBlockIssue duplicateCodeBlockIssue,
            final IDuplicateCodeBlockOccurrence occurrence, final List<IDuplicateCodeBlockOccurrence> others)
    {
        final StringBuilder detail = new StringBuilder();
        detail.append("Line(s) ").append(occurrence.getStartLine()).append("-").append(occurrence.getStartLine() + occurrence.getBlockSize() - 1)
                .append(" duplicate of ");

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

    private void createSourceFileIssues(final SensorContext context, final IModuleInfoProcessor moduleInfoProcessor, final ISourceFile sourceFile,
            final InputPath inputPath, final IIssue issue, final ActiveRule rule)
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
                    final String issueDescription = createIssueDescription(moduleInfoProcessor, nextDuplicateCodeBlockIssue, nextOccurrence, others);
                    createSonarqubeIssue(context, inputPath, rule, issueDescription,
                            location -> location.at(new DefaultTextRange(new DefaultTextPointer(nextOccurrence.getStartLine(), ZERO_LINE_OFFSET),
                                    new DefaultTextPointer(nextOccurrence.getStartLine() + nextOccurrence.getBlockSize(), ZERO_LINE_OFFSET))));
                }
            }
        }
        else
        {
            final String issueDescription = createIssueDescription(moduleInfoProcessor, issue);
            createSonarqubeIssue(context, inputPath, rule, issueDescription, location ->
            {
                final int line = issue.getLine();
                final int lineToUse = line <= 0 ? 1 : line;
                location.at(new DefaultTextRange(new DefaultTextPointer(lineToUse, ZERO_LINE_OFFSET),
                        new DefaultTextPointer(lineToUse, ZERO_LINE_OFFSET)));
            });
        }
    }

    private void addIssuesToSourceFile(final SensorContext context, final IModuleInfoProcessor moduleInfoProcessor,
            final Map<String, ActiveRule> keyToRule, final String baseDir, final ISourceFile sourceFile, final List<IIssue> issues)
    {
        final String rootDirectoryRelPath = sourceFile.getRelativeRootDirectory();
        final String sourceRelPath = sourceFile.getRelativePath();
        final String sourceFileLocation = Paths.get(baseDir, rootDirectoryRelPath, sourceRelPath).toAbsolutePath().normalize().toString();

        final InputPath inputPath = sonarqubeFileSystem
                .inputFile(sonarqubeFileSystem.predicates().hasAbsolutePath(Utility.convertPathToUniversalForm(sourceFileLocation)));
        if (inputPath != null)
        {
            for (final IIssue nextIssue : issues)
            {
                final String ruleKey = SonargraphBase.createRuleKeyToCheck(nextIssue.getIssueType(), nextIssue.getSeverity());
                final ActiveRule nextRule = keyToRule.get(ruleKey);
                if (nextRule != null)
                {
                    try
                    {
                        createSourceFileIssues(context, moduleInfoProcessor, sourceFile, inputPath, nextIssue, nextRule);
                    }
                    catch (final Exception e)
                    {
                        LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Failed to create issue '" + nextIssue + "'. ", e);
                    }
                }
                else
                {
                    createCustomRuleForIssue(nextIssue);
                }
            }
        }
        else
        {
            LOGGER.error("{}: Failed to locate '{}' at '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, sourceFile.getFqName(),
                    sourceFileLocation);
        }
    }

    private void addIssuesToDirectory(final SensorContext context, final IModuleInfoProcessor moduleInfoProcessor,
            final Map<String, ActiveRule> keyToRule, final String baseDir, final String relDirectory, final List<IIssue> issues)
    {
        final String directoryPath = Paths.get(baseDir, relDirectory).toAbsolutePath().normalize().toString();
        final InputDir inputDir = sonarqubeFileSystem.inputDir(new File(Utility.convertPathToUniversalForm(directoryPath)));

        if (inputDir != null)
        {
            for (final IIssue nextIssue : issues)
            {
                final String ruleKey = SonargraphBase.createRuleKeyToCheck(nextIssue.getIssueType(), nextIssue.getSeverity());
                final ActiveRule nextRule = keyToRule.get(ruleKey);
                if (nextRule != null)
                {
                    try
                    {
                        createSonarqubeIssue(context, inputDir, nextRule, createIssueDescription(moduleInfoProcessor, nextIssue), null);
                    }
                    catch (final Exception e)
                    {
                        LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Failed to create issue '" + nextIssue + "'. ", e);
                    }
                }
                else
                {
                    createCustomRuleForIssue(nextIssue);
                }
            }
        }
        else
        {
            LOGGER.error("{}: Failed to locate directory resource: '{}'\nBaseDir: {}\nrelDirectory:'{}'",
                    SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, directoryPath, baseDir, relDirectory);
        }
    }

    private void processSystemMetrics(final SensorContext context, final InputComponent inputComponent, final ISoftwareSystem softwareSystem,
            final ISystemInfoProcessor systemInfoProcessor, final ActiveRulesAndMetrics rulesAndMetrics)
    {
        final Optional<IMetricLevel> systemLevelOpt = systemInfoProcessor.getMetricLevel(IMetricLevel.SYSTEM);
        if (!systemLevelOpt.isPresent())
        {
            LOGGER.error("{}: Sonargraph report is missing system-level metrics!", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
            return;
        }

        final IMetricLevel systemLevel = systemLevelOpt.get();
        final SonargraphMetricsProvider customMetricsProvider = sonargraphMetrics.getMetricsProvider();

        for (final IMetricId nextMetricId : systemInfoProcessor.getMetricIdsForLevel(systemLevel))
        {
            final String metricKey = SonargraphBase.createMetricKeyFromStandardName(nextMetricId.getName());
            final Metric<Serializable> metric = rulesAndMetrics.getMetrics().get(metricKey);

            if (metric == null)
            {
                customMetricsProvider.addCustomMetric(nextMetricId);
                LOGGER.warn("{}: Custom metric added '{}'.", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, nextMetricId.getName());
                isUpdateOfServerCustomMetricsNeeded = true;
                /**
                 * Custom metric has now been added and needs to be persisted and loaded at SonarQube server startup. Only then, measures can be
                 * saved. There is nothing left that can be done here and now.
                 */
                continue;
            }

            final Optional<IMetricValue> metricValueOpt = systemInfoProcessor.getMetricValueForElement(nextMetricId, systemLevel,
                    softwareSystem.getFqName());
            if (metricValueOpt.isPresent())
            {
                try
                {
                    /**
                     * Throws UnsupportedOperationException, if custom metric is present on server side, but was not available at scanner start on
                     * scanner side.
                     */
                    createSonarqubeMeasure(context, inputComponent, metric, metricValueOpt.get());
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
                LOGGER.warn("{}: No value found for metric '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, metricKey);
            }
        }
    }

    private void createSonarqubeIssue(final SensorContext context, final InputComponent inputComponent, final ActiveRule rule, final String msg,
            final Consumer<NewIssueLocation> consumer)
    {
        final NewIssue sqIssue = context.newIssue();
        sqIssue.forRule(rule.ruleKey());

        final NewIssueLocation sqIssueLoc = sqIssue.newLocation();
        sqIssueLoc.on(inputComponent);
        sqIssueLoc.message(msg);
        sqIssue.at(sqIssueLoc);

        if (consumer != null)
        {
            consumer.accept(sqIssueLoc);
        }

        sqIssue.save();
    }

    private ActiveRulesAndMetrics createActiveRulesAndMetrics(final SensorContext context)
    {
        final Map<String, Map<String, ActiveRule>> languageToActiveRules = new HashMap<>();
        HashMap<String, ActiveRule> activeRules;
        for (final String nextLanguage : SonargraphBase.SUPPORTED_LANGUAGES)
        {
            final Collection<ActiveRule> rules = context.activeRules().findByRepository(SonargraphRules.getRepositoryKeyForLanguage(nextLanguage));
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

        LOGGER.info("{}: {} rule(s) activated", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, languageToActiveRules.size());

        final Map<String, Metric<Serializable>> metrics = sonarqubeMetricFinder.findAll().stream()
                .filter(m -> m.key().startsWith(SonargraphBase.METRIC_ID_PREFIX)).collect(Collectors.toMap(Metric::key, m -> m));
        LOGGER.info("{}: {} metric(s) defined", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, metrics.size());

        return new ActiveRulesAndMetrics(languageToActiveRules, metrics);
    }

    @SuppressWarnings("unchecked")
    private void createSonarqubeMeasure(final SensorContext context, final InputComponent inputComponent, final Metric<? extends Serializable> metric,
            final IMetricValue metricValue)
    {
        if (metricValue.getId().isFloat())
        {
            final NewMeasure<Double> sqMeasure = context.<Double> newMeasure();
            sqMeasure.forMetric((Metric<Double>) metric);
            sqMeasure.on(inputComponent);
            sqMeasure.withValue(Double.valueOf(metricValue.getValue().doubleValue()));
            sqMeasure.save();
        }
        else
        {
            final NewMeasure<Integer> sqMeasure = context.<Integer> newMeasure();
            sqMeasure.forMetric((Metric<Integer>) metric);
            sqMeasure.on(inputComponent);
            sqMeasure.withValue(Integer.valueOf(metricValue.getValue().intValue()));
            sqMeasure.save();
        }
    }
}