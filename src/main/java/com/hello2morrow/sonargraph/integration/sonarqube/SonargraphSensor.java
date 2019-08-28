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
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
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
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.batch.sensor.measure.NewMeasure;
import org.sonar.api.config.Configuration;
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
import com.hello2morrow.sonargraph.integration.access.model.IIssueType;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;
import com.hello2morrow.sonargraph.integration.access.model.IMetricLevel;
import com.hello2morrow.sonargraph.integration.access.model.IMetricValue;
import com.hello2morrow.sonargraph.integration.access.model.IModule;
import com.hello2morrow.sonargraph.integration.access.model.INamedElement;
import com.hello2morrow.sonargraph.integration.access.model.IResolution;
import com.hello2morrow.sonargraph.integration.access.model.ISoftwareSystem;
import com.hello2morrow.sonargraph.integration.access.model.ISourceFile;
import com.hello2morrow.sonargraph.integration.access.model.ResolutionType;

public final class SonargraphSensor implements Sensor
{
    private static final Logger LOGGER = Loggers.get(SonargraphSensor.class);
    private static final int ZERO_LINE_OFFSET = 0;

    static final class ActiveRulesAndMetrics
    {
        private final Map<String, ActiveRule> activeRules;
        private final Map<String, Metric<Serializable>> metrics;

        ActiveRulesAndMetrics(final Map<String, ActiveRule> activeRules, final Map<String, Metric<Serializable>> metrics)
        {
            this.activeRules = activeRules;
            this.metrics = metrics;
        }

        Map<String, ActiveRule> getActiveRules()
        {
            return Collections.unmodifiableMap(activeRules);
        }

        Map<String, Metric<Serializable>> getMetrics()
        {
            return Collections.unmodifiableMap(metrics);
        }
    }

    private final FileSystem fileSystem;
    private final MetricFinder metricFinder;
    private Properties customMetrics;

    public SonargraphSensor(final FileSystem fileSystem, final MetricFinder metricFinder)
    {
        this.fileSystem = fileSystem;
        this.metricFinder = metricFinder;
    }

    @Override
    public void describe(final SensorDescriptor descriptor)
    {
        descriptor.name(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME).global();
    }

    @Override
    public void execute(final SensorContext context)
    {
        final String projectKey = context.config().get("sonar.projectKey").orElse("<unknown>");
        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Processing SonarQube project '" + projectKey + "'");

        final File reportFile = getReportFile(context.config());
        if (reportFile != null)
        {
            final ISonargraphSystemController controller = ControllerFactory.createController();

            final File baseDir = getSystemBaseDirectory(context.config());
            Result result;
            if (baseDir == null)
            {
                result = controller.loadSystemReport(reportFile);
            }
            else
            {
                LOGGER.info(
                        SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Adjusting baseDirectory of Sonargraph system to '" + baseDir + "'");
                result = controller.loadSystemReport(reportFile, baseDir);
            }
            if (result.isSuccess())
            {
                process(context, controller);
            }
            else
            {
                LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + result.toString());
            }
        }

        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Finished processing SonarQube project '" + projectKey + "'");
    }

    private File getReportFile(final Configuration configuration)
    {
        String relativeReportPath = null;
        final Optional<String> configuredRelativeReportPathOptional = configuration.get(SonargraphBase.XML_REPORT_FILE_PATH_KEY);
        if (configuredRelativeReportPathOptional.isPresent())
        {
            final String configuredRelativeReportPath = configuredRelativeReportPathOptional.get();
            if (!configuredRelativeReportPath.isEmpty())
            {
                relativeReportPath = configuredRelativeReportPath;
            }
        }

        if (relativeReportPath == null)
        {
            relativeReportPath = SonargraphBase.XML_REPORT_FILE_PATH_DEFAULT;
            LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": XML report file path not configured - using default '"
                    + SonargraphBase.XML_REPORT_FILE_PATH_DEFAULT + "'");
        }

        final File reportFile = fileSystem.resolvePath(relativeReportPath);
        if (reportFile.exists())
        {
            LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Using XML report file '" + reportFile.getAbsolutePath() + "'");
            return reportFile;
        }

        LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": XML report file '" + reportFile.getAbsolutePath() + "' not found");
        return null;
    }

    private File getSystemBaseDirectory(final Configuration configuration)
    {
        final Optional<String> basePathOptional = configuration.get(SonargraphBase.SONARGRAPH_BASE_DIR_KEY);
        if (!basePathOptional.isPresent())
        {
            return null;
        }

        final String path = basePathOptional.get();
        final File baseDir = fileSystem.resolvePath(path);
        if (baseDir.exists())
        {
            return baseDir;
        }

        return null;
    }

    private void process(final SensorContext context, final ISonargraphSystemController controller)
    {
        final ISoftwareSystem softwareSystem = controller.getSoftwareSystem();

        final ActiveRulesAndMetrics rulesAndMetrics = createActiveRulesAndMetrics(context);
        final ISystemInfoProcessor systemInfoProcessor = controller.createSystemInfoProcessor();
        processSystem(context, softwareSystem, systemInfoProcessor, rulesAndMetrics);

        for (final Entry<String, IModule> nextEntry : systemInfoProcessor.getModules().entrySet())
        {
            final IModule module = nextEntry.getValue();
            final IModuleInfoProcessor moduleInfoProcessor = controller.createModuleInfoProcessor(module);

            processModule(context, moduleInfoProcessor, rulesAndMetrics);
        }

        if (customMetrics != null)
        {
            //New custom metrics have been introduced.
            SonargraphBase.save(customMetrics);
            customMetrics = null;
        }
    }

    private void processSystem(final SensorContext context, final ISoftwareSystem softwareSystem, final ISystemInfoProcessor systemInfoProcessor,
            final ActiveRulesAndMetrics rulesAndMetrics)
    {
        processSystemMetrics(context, context.module(), softwareSystem, systemInfoProcessor, rulesAndMetrics);

        final List<IIssue> systemIssues = systemInfoProcessor.getIssues(issue -> !issue.isIgnored()
                && !SonargraphBase.ignoreIssueType(issue.getIssueType()) && issue.getAffectedNamedElements().contains(softwareSystem));
        final Map<String, ActiveRule> keyToRule = rulesAndMetrics.getActiveRules();

        for (final IIssue nextIssue : systemIssues)
        {
            final IIssueType nextIssueType = nextIssue.getIssueType();
            final ActiveRule nextRule = keyToRule.get(SonargraphBase.createRuleKeyToCheck(nextIssueType));
            if (nextRule != null)
            {
                createIssue(context, context.module(), nextRule, createIssueDescription(systemInfoProcessor, nextIssue), null);
            }
        }

        final List<IIssue> ignoredErrorOrWarningIssues = systemInfoProcessor
                .getIssues(issue -> SonargraphBase.isIgnoredErrorOrWarningIssue(issue.getIssueType()));
        if (!ignoredErrorOrWarningIssues.isEmpty())
        {
            LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Found " + ignoredErrorOrWarningIssues.size()
                    + " system setup related error/warning issue(s)");
            int i = 1;
            for (final IIssue nextIssue : ignoredErrorOrWarningIssues)
            {
                LOGGER.warn("[" + i + "] " + nextIssue.getPresentationName());
                for (final INamedElement nextAffected : nextIssue.getAffectedNamedElements())
                {
                    LOGGER.warn(" - " + nextAffected.getName() + " [" + nextAffected.getPresentationKind() + "]");
                }
                i++;
            }
        }
    }

    private final void processModule(final SensorContext context, final IModuleInfoProcessor moduleInfoProcessor,
            final ActiveRulesAndMetrics rulesAndMetrics)
    {
        final Map<String, ActiveRule> keyToRule = rulesAndMetrics.getActiveRules();
        final Map<ISourceFile, List<IIssue>> sourceFileIssueMap = moduleInfoProcessor
                .getIssuesForSourceFiles(issue -> !issue.isIgnored() && !SonargraphBase.ignoreIssueType(issue.getIssueType()));
        for (final Entry<ISourceFile, List<IIssue>> issuesPerSourceFile : sourceFileIssueMap.entrySet())
        {
            addIssuesToSourceFile(context, moduleInfoProcessor, keyToRule, moduleInfoProcessor.getBaseDirectory(), issuesPerSourceFile.getKey(),
                    issuesPerSourceFile.getValue());
        }

        final Map<String, List<IIssue>> directoryIssueMap = moduleInfoProcessor
                .getIssuesForDirectories(issue -> !issue.isIgnored() && !SonargraphBase.ignoreIssueType(issue.getIssueType()));
        for (final Entry<String, List<IIssue>> issuesPerDirectory : directoryIssueMap.entrySet())
        {
            addIssuesToDirectory(context, moduleInfoProcessor, keyToRule, moduleInfoProcessor.getBaseDirectory(), issuesPerDirectory.getKey(),
                    issuesPerDirectory.getValue());
        }
    }

    private String createIssueDescription(final IInfoProcessor infoProcessor, final IIssue issue, final String detail)
    {
        final StringBuilder builder = new StringBuilder();

        final IResolution resolution = infoProcessor.getResolution(issue);
        if (resolution != null)
        {
            final ResolutionType type = resolution.getType();
            switch (type)
            {
            case FIX:
                builder.append("[").append(SonargraphBase.toLowerCase(type.toString(), false)).append(": ").append(issue.getPresentationName())
                        .append("]");
                break;
            case REFACTORING:
            case TODO:
                builder.append("[").append(issue.getPresentationName()).append("]");
                break;
            case IGNORE:
                //$FALL-THROUGH$
            default:
                assert false : "Unhandled resolution type: " + type;
                break;
            }

            builder.append(" assignee='").append(resolution.getAssignee()).append("'");
            builder.append(" priority='").append(SonargraphBase.toLowerCase(resolution.getPriority().toString(), false)).append("'");
            builder.append(" description='").append(resolution.getDescription()).append("'");
            builder.append(" created='").append(resolution.getDate()).append("'");
        }
        else
        {
            builder.append("[").append(issue.getPresentationName()).append("]");
        }

        builder.append(" ").append(issue.getDescription());
        if (!detail.isEmpty())
        {
            builder.append(" ").append(detail);
        }
        builder.append(" [").append(issue.getIssueProvider().getPresentationName()).append("]");

        return builder.toString();
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

    private String createIssueDescription(final IInfoProcessor infoProcessor, final IIssue forIssue)
    {
        return createIssueDescription(infoProcessor, forIssue, "");
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
                final Optional<ISourceFile> nextOptionalOriginalLocation = nextSourceFileOfOccurence.getOriginalLocation();
                if (nextSourceFileOfOccurence.equals(sourceFile)
                        || (nextOptionalOriginalLocation.isPresent() && nextOptionalOriginalLocation.get().equals(sourceFile)))
                {
                    final List<IDuplicateCodeBlockOccurrence> others = new ArrayList<>(nextOccurrences);
                    others.remove(nextOccurrence);
                    final String issueDescription = createIssueDescription(moduleInfoProcessor, nextDuplicateCodeBlockIssue, nextOccurrence, others);
                    createIssue(context, inputPath, rule, issueDescription,
                            location -> location.at(new DefaultTextRange(new DefaultTextPointer(nextOccurrence.getStartLine(), ZERO_LINE_OFFSET),
                                    new DefaultTextPointer(nextOccurrence.getStartLine() + nextOccurrence.getBlockSize(), ZERO_LINE_OFFSET))));
                }
            }
        }
        else
        {
            final String issueDescription = createIssueDescription(moduleInfoProcessor, issue);
            createIssue(context, inputPath, rule, issueDescription, location ->
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

        final InputPath inputPath = fileSystem
                .inputFile(fileSystem.predicates().hasAbsolutePath(Utility.convertPathToUniversalForm(sourceFileLocation)));
        if (inputPath != null)
        {
            for (final IIssue nextIssue : issues)
            {
                final ActiveRule nextRule = keyToRule.get(SonargraphBase.createRuleKeyToCheck(nextIssue.getIssueType()));
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
            }
        }
        else
        {
            LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Failed to locate '" + sourceFile.getFqName() + "' at '"
                    + sourceFileLocation + "'");
        }
    }

    private void addIssuesToDirectory(final SensorContext context, final IModuleInfoProcessor moduleInfoProcessor,
            final Map<String, ActiveRule> keyToRule, final String baseDir, final String relDirectory, final List<IIssue> issues)
    {
        final String directoryLocation = Paths.get(baseDir, relDirectory).toAbsolutePath().normalize().toString();
        final InputDir inputDir = fileSystem.inputDir(new File(Utility.convertPathToUniversalForm(directoryLocation)));

        if (inputDir != null)
        {
            for (final IIssue nextIssue : issues)
            {
                final ActiveRule nextRule = keyToRule.get(SonargraphBase.createRuleKeyToCheck(nextIssue.getIssueType()));
                if (nextRule != null)
                {
                    try
                    {
                        createIssue(context, inputDir, nextRule, createIssueDescription(moduleInfoProcessor, nextIssue), null);
                    }
                    catch (final Exception e)
                    {
                        LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Failed to create issue '" + nextIssue + "'. ", e);
                    }
                }
            }
        }
        else
        {
            LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Failed to locate directory resource: '" + directoryLocation
                    + "'\nBaseDir: " + baseDir + "\nrelDirectory:'" + relDirectory);
        }
    }

    private void processSystemMetrics(final SensorContext context, final InputComponent inputComponent, final ISoftwareSystem softwareSystem,
            final ISystemInfoProcessor systemInfoProcessor, final ActiveRulesAndMetrics rulesAndMetrics)
    {
        final Optional<IMetricLevel> systemLevelOptional = systemInfoProcessor.getMetricLevel(IMetricLevel.SYSTEM);
        if (!systemLevelOptional.isPresent())
        {
            LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Sonargraph report is missing system-level metrics!");
            return;
        }

        final IMetricLevel systemLevel = systemLevelOptional.get();
        for (final IMetricId nextMetricId : systemInfoProcessor.getMetricIdsForLevel(systemLevel))
        {
            String nextMetricKey = SonargraphBase.createMetricKeyFromStandardName(nextMetricId.getName());
            Metric<Serializable> metric = rulesAndMetrics.getMetrics().get(nextMetricKey);
            if (metric == null)
            {
                //Try custom metrics
                nextMetricKey = SonargraphBase.createCustomMetricKeyFromStandardName(softwareSystem.getName(), nextMetricId.getName());
                metric = rulesAndMetrics.getMetrics().get(nextMetricKey);
            }
            if (metric == null)
            {
                if (customMetrics == null)
                {
                    customMetrics = SonargraphBase.loadCustomMetrics();
                }

                SonargraphBase.addCustomMetric(softwareSystem, nextMetricId, customMetrics);
                LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Custom metric added '" + softwareSystem.getName() + "/"
                        + nextMetricId.getName() + "'.");
                continue;
            }

            final Optional<IMetricValue> metricValueOptional = systemInfoProcessor.getMetricValueForElement(nextMetricId, systemLevel,
                    softwareSystem.getFqName());
            if (metricValueOptional.isPresent())
            {
                createMeasure(context, inputComponent, metric, metricValueOptional.get());
            }
            else
            {
                LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": No value found for metric '" + nextMetricKey + "'");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void createMeasure(final SensorContext context, final InputComponent inputComponent, final Metric<? extends Serializable> metric,
            final IMetricValue metricValue)
    {
        if (metricValue.getId().isFloat())
        {
            final NewMeasure<Double> newMeasure = context.<Double> newMeasure();
            newMeasure.forMetric((Metric<Double>) metric);
            newMeasure.on(inputComponent);
            newMeasure.withValue(Double.valueOf(metricValue.getValue().doubleValue()));
            newMeasure.save();
        }
        else
        {
            final NewMeasure<Integer> newMeasure = context.<Integer> newMeasure();
            newMeasure.forMetric((Metric<Integer>) metric);
            newMeasure.on(inputComponent);
            newMeasure.withValue(Integer.valueOf(metricValue.getValue().intValue()));
            newMeasure.save();
        }
    }

    private void createIssue(final SensorContext context, final InputComponent inputComponent, final ActiveRule rule, final String msg,
            final Consumer<NewIssueLocation> consumer)
    {
        final NewIssue newIssue = context.newIssue();
        newIssue.forRule(rule.ruleKey());

        final NewIssueLocation newIssueLocation = newIssue.newLocation();
        newIssueLocation.on(inputComponent);
        newIssueLocation.message(msg);
        newIssue.at(newIssueLocation);

        if (consumer != null)
        {
            consumer.accept(newIssueLocation);
        }

        newIssue.save();
    }

    private ActiveRulesAndMetrics createActiveRulesAndMetrics(final SensorContext context)
    {
        final Map<String, ActiveRule> activeRules = new HashMap<>();
        context.activeRules().findByRepository(SonargraphBase.SONARGRAPH_PLUGIN_KEY).forEach(a -> activeRules.put(a.ruleKey().rule(), a));
        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + activeRules.size() + " rule(s) activated");

        final Map<String, Metric<Serializable>> metrics = metricFinder.findAll().stream()
                .filter(m -> m.key().startsWith(SonargraphBase.METRIC_ID_PREFIX)).collect(Collectors.toMap(Metric::key, m -> m));
        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + metrics.size() + " metric(s) defined");

        return new ActiveRulesAndMetrics(activeRules, metrics);
    }
}