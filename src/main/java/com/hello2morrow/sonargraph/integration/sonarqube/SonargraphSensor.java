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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.fs.InputModule;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.batch.fs.internal.DefaultTextPointer;
import org.sonar.api.batch.fs.internal.DefaultTextRange;
import org.sonar.api.batch.measure.Metric;
import org.sonar.api.batch.measure.MetricFinder;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.config.Settings;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rules.ActiveRule;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import com.hello2morrow.sonargraph.integration.access.controller.ControllerAccess;
import com.hello2morrow.sonargraph.integration.access.controller.IInfoProcessor;
import com.hello2morrow.sonargraph.integration.access.controller.IModuleInfoProcessor;
import com.hello2morrow.sonargraph.integration.access.controller.ISonargraphSystemController;
import com.hello2morrow.sonargraph.integration.access.controller.ISystemInfoProcessor;
import com.hello2morrow.sonargraph.integration.access.foundation.Result;
import com.hello2morrow.sonargraph.integration.access.foundation.Result.ICause;
import com.hello2morrow.sonargraph.integration.access.foundation.ResultCause;
import com.hello2morrow.sonargraph.integration.access.foundation.Utility;
import com.hello2morrow.sonargraph.integration.access.model.IDuplicateCodeBlockIssue;
import com.hello2morrow.sonargraph.integration.access.model.IDuplicateCodeBlockOccurrence;
import com.hello2morrow.sonargraph.integration.access.model.IFeature;
import com.hello2morrow.sonargraph.integration.access.model.IIssue;
import com.hello2morrow.sonargraph.integration.access.model.IIssueCategory;
import com.hello2morrow.sonargraph.integration.access.model.IIssueType;
import com.hello2morrow.sonargraph.integration.access.model.IJavaMetricId;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;
import com.hello2morrow.sonargraph.integration.access.model.IMetricLevel;
import com.hello2morrow.sonargraph.integration.access.model.IMetricValue;
import com.hello2morrow.sonargraph.integration.access.model.IModule;
import com.hello2morrow.sonargraph.integration.access.model.INamedElement;
import com.hello2morrow.sonargraph.integration.access.model.INamedElementContainer;
import com.hello2morrow.sonargraph.integration.access.model.IResolution;
import com.hello2morrow.sonargraph.integration.access.model.ISoftwareSystem;
import com.hello2morrow.sonargraph.integration.access.model.ISourceFile;
import com.hello2morrow.sonargraph.integration.access.model.ResolutionType;

public final class SonargraphSensor implements Sensor
{
    public enum ReportProcessingMessageCause implements ICause
    {
        NO_MODULES;

        @Override
        public String getStandardName()
        {
            return Utility.convertConstantNameToStandardName(name());
        }

        @Override
        public String getPresentationName()
        {
            return Utility.convertConstantNameToPresentationName(name());
        }
    }

    private static final Logger LOGGER = Loggers.get(SonargraphSensor.class);
    private static final String SEPARATOR = "----------------------------------------------------------------";
    private static final String SONARGRAPH_TARGET_DIR = "sonargraph";
    private static final String SONARGRAPH_SONARQUBE_REPORT_FILENAME = "sonargraph-sonarqube-report.xml";

    private final RulesProfile profile;
    private final Settings settings;
    private final FileSystem fileSystem;
    private Result loadReportResult;
    private ISonargraphSystemController controller;
    private Exception sensorExecutionException;
    private final MetricFinder metricFinder;
    private int numberOfWorkspaceWarnings = 0;

    public SonargraphSensor(final MetricFinder metricFinder, final RulesProfile profile, final Settings settings, final FileSystem moduleFileSystem)
    {
        this.metricFinder = metricFinder;
        this.profile = profile;
        this.settings = settings;
        this.fileSystem = moduleFileSystem;
    }

    Exception getSensorExecutionException()
    {
        return sensorExecutionException;
    }

    private static boolean fileExistsAndIsReadable(final File reportFile)
    {
        return reportFile.exists() && reportFile.canRead();
    }

    private static Optional<File> determineReportFile(final FileSystem fileSystem, final Settings settings)
    {
        assert fileSystem != null : "Parameter 'fileSystem' of method 'determineReportFile' must not be null";
        assert settings != null : "Parameter 'settings' of method 'determineReportFile' must not be null";

        final String reportPathOld = settings.getString(SonargraphPluginBase.REPORT_PATH_OLD);
        final String reportPath = settings.getString(SonargraphPluginBase.REPORT_PATH);
        final File reportFile;
        if (reportPathOld != null)
        {
            reportFile = fileSystem.resolvePath(reportPathOld);
        }
        else if (reportPath != null)
        {
            reportFile = fileSystem.resolvePath(reportPath);
        }
        else
        {
            //try Maven path
            final File mavenDefaultLocation = Paths
                    .get(fileSystem.workDir().getParentFile().getAbsolutePath(), SONARGRAPH_TARGET_DIR, SONARGRAPH_SONARQUBE_REPORT_FILENAME)
                    .toFile();
            if (fileExistsAndIsReadable(mavenDefaultLocation))
            {
                reportFile = mavenDefaultLocation;
            }
            else
            {
                //try Gradle path
                reportFile = Paths.get(fileSystem.workDir().getParentFile().getParentFile().getAbsolutePath(), SONARGRAPH_TARGET_DIR,
                        SONARGRAPH_SONARQUBE_REPORT_FILENAME).toFile();
            }
        }

        if (fileExistsAndIsReadable(reportFile))
        {
            LOGGER.debug("Load report from: {}", reportFile.getAbsolutePath());
            return Optional.of(reportFile);
        }

        LOGGER.debug("No report found at: {}", reportFile.getAbsolutePath());
        return Optional.empty();
    }

    //    /* Called from Maven */
    //    @Override
    //    public boolean shouldExecuteOnProject(final InputModule project)
    //    {
    //        assert project != null : "Parameter 'project' of method 'shouldExecuteOnProject' must not be null";
    //
    //        if (profile.getActiveRulesByRepository(SonargraphPluginBase.PLUGIN_KEY).isEmpty())
    //        {
    //            LOGGER.warn(SEPARATOR);
    //            LOGGER.warn("{}: Skipping project {}, since no Sonargraph rules are activated in current SonarQube quality profile [{}].",
    //                    SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, project.key(), profile.getName());
    //            LOGGER.warn(SEPARATOR);
    //            return false;
    //        }
    //
    //        if (!determineReportFile(fileSystem, settings).isPresent())
    //        {
    //            LOGGER.warn(SEPARATOR);
    //            LOGGER.warn("{}: Skipping project {}, since no Sonargraph report is found.", SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
    //                    project.key());
    //            LOGGER.warn(SEPARATOR);
    //            return false;
    //        }
    //        return true;
    //    }

    private void processFeatures(final SensorContext sensorContext, final ISystemInfoProcessor systemInfoProcessor)
    {
        assert sensorContext != null : "Parameter 'sensorContext' of method 'processFeatures' must not be null";
        assert systemInfoProcessor != null : "Parameter 'systemInfoProcessor' of method 'processFeatures' must not be null";

        for (final IFeature feature : systemInfoProcessor.getFeatures())
        {
            //            if (feature.getName().equals(IFeature.ARCHITECTURE) && feature.isLicensed())
            //            {
            //                sensorContext.saveMeasure(new Measure<Boolean>(SonargraphMetrics.ARCHITECTURE_FEATURE_AVAILABLE, 1.0));
            //            }
            //
            //            if (feature.getName().equals(IFeature.VIRTUAL_MODELS) && feature.isLicensed())
            //            {
            //                sensorContext.saveMeasure(new Measure<Boolean>(SonargraphMetrics.VIRTUAL_MODEL_FEATURE_AVAILABLE, 1.0));
            //            }
        }
    }

    private static Optional<File> determineBaseDirectory(final Settings settings)
    {
        final String baseDirectory = settings.getString(SonargraphPluginBase.SYSTEM_BASE_DIRECTORY);
        if (baseDirectory == null || baseDirectory.trim().isEmpty())
        {
            return Optional.empty();
        }
        final File baseDir = Paths.get(baseDirectory).toAbsolutePath().normalize().toFile();
        return Optional.of(baseDir);
    }

    private Result loadReport(final InputModule project, final File reportFile, final Settings settings)
    {
        assert settings != null : "Parameter 'settings' of method 'loadReport' must not be null";

        final Result result = new Result("Reading Sonargraph report from: " + reportFile.getAbsolutePath());
        final Optional<File> baseDirectory = determineBaseDirectory(settings);
        if (baseDirectory.isPresent())
        {
            LOGGER.info("Changing Sonargraph baseDirectory to: {}", baseDirectory.get().getAbsolutePath());
            result.addMessagesFrom(controller.loadSystemReport(reportFile, baseDirectory.get()));
        }
        else
        {
            result.addMessagesFrom(controller.loadSystemReport(reportFile));
        }
        if (result.isFailure())
        {
            LOGGER.error("Failed to execute Sonargraph plugin for {}", project.key());
            LOGGER.error(result.toString());
        }
        return result;
    }

    private void processSystemMetrics(final Map<String, Metric<?>> metrics, final SensorContext sensorContext,
            final ISystemInfoProcessor systemInfoProcessor, final ISoftwareSystem softwareSystem)
    {
        assert metrics != null : "Parameter 'metrics' of method 'processSystemMetrics' must not be null";
        assert sensorContext != null : "Parameter 'sensorContext' of method 'processSystemMetrics' must not be null";
        assert systemInfoProcessor != null : "Parameter 'systemInfoProcessor' of method 'processSystemMetrics' must not be null";
        assert softwareSystem != null : "Parameter 'softwareSystem' of method 'processSystemMetrics' must not be null";

        final Optional<IMetricLevel> systemLevel = systemInfoProcessor.getMetricLevel(IMetricLevel.SYSTEM);
        assert systemLevel.isPresent() : "Metric level 'system' not found";

        processProjectMetrics(sensorContext, softwareSystem, systemInfoProcessor, metrics, systemLevel.get());

        final Map<INamedElement, IMetricValue> nccdValues = systemInfoProcessor.getMetricValues(IMetricLevel.MODULE,
                IMetricId.StandardName.CORE_NCCD.getStandardName());
        final OptionalDouble highestNccd = nccdValues.values().stream().mapToDouble(v -> v.getValue().doubleValue()).max();
        if (highestNccd.isPresent())
        {
            //            final NewMeasure<Serializable> newMeasure = sensorContext.newMeasure();
            //            newMeasure.forMetric(SonargraphMetrics.MAX_MODULE_NCCD);
            //            newMeasure.withValue(highestNccd.getAsDouble());
            //            newMeasure.save();
        }
    }

    private static Optional<IModule> determineModuleName(final InputModule project, final ISoftwareSystem softwareSystem)
    {
        assert project != null : "Parameter 'project' of method 'determineModuleName' must not be null";
        assert softwareSystem != null : "Parameter 'softwareSystem' of method 'determineModuleName' must not be null";

        final Map<String, IModule> modules = softwareSystem.getModules();

        if (modules.size() == 1)
        {
            return Optional.of(modules.values().iterator().next());
        }

        for (final Entry<String, IModule> next : modules.entrySet())
        {
            final IModule module = next.getValue();
            final String buName = SonargraphPluginBase.getBuildUnitName(module.getFqName());
            if (SonargraphPluginBase.buildUnitMatchesAnalyzedProject(buName, project))
            {
                return Optional.of(module);
            }
        }
        return Optional.empty();
    }

    private static String toLowerCase(String input, final boolean firstLower)
    {
        assert input != null : "Parameter 'input' of method 'toLowerCase' must not be null";

        if (input.isEmpty())
        {
            return input;
        }

        if (input.length() == 1)
        {
            return firstLower ? input.toLowerCase() : input.toUpperCase();
        }

        input = input.toLowerCase();
        return firstLower ? input : Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    private static String create(final IModuleInfoProcessor moduleInfoProcessor, final IIssue issue, final String detail)
    {
        assert moduleInfoProcessor != null : "Parameter 'moduleInfoProcessor' of method 'create' must not be null";
        assert issue != null : "Parameter 'issue' of method 'create' must not be null";
        assert detail != null : "Parameter 'detail' of method 'create' must not be null";

        final StringBuilder builder = new StringBuilder();

        final IResolution resolution = moduleInfoProcessor.getResolution(issue);
        if (resolution != null)
        {
            final ResolutionType type = resolution.getType();
            switch (type)
            {
            case FIX:
                builder.append("[").append(toLowerCase(type.toString(), false)).append(": ").append(issue.getPresentationName()).append("]");
                break;
            case REFACTORING:
            case TODO:
                builder.append("[").append(issue.getPresentationName()).append("]");
                break;
            case IGNORE:
                assert false : "Unexpected resolution type: " + type;
                break;
            default:
                assert false : "Unhandled resolution type: " + type;
                break;
            }

            builder.append(" assignee='").append(resolution.getAssignee()).append("'");
            builder.append(" priority='").append(toLowerCase(resolution.getPriority().toString(), false)).append("'");
            builder.append(" description='").append(resolution.getDescription()).append("'");
            builder.append(" created='").append(resolution.getDate()).append("'");
        }
        else
        {
            //Or issue.getIssueType().getPresentationName()?
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

    private static String create(final IModuleInfoProcessor moduleInfoProcessor, final IDuplicateCodeBlockIssue issue,
            final IDuplicateCodeBlockOccurrence occurrence, final List<IDuplicateCodeBlockOccurrence> others)
    {
        assert moduleInfoProcessor != null : "Parameter 'moduleInfoProcessor' of method 'create' must not be null";
        assert issue != null : "Parameter 'issue' of method 'create' must not be null";
        assert occurrence != null : "Parameter 'occurrence' of method 'create' must not be null";
        assert others != null : "Parameter 'others' of method 'create' must not be null";

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

        return create(moduleInfoProcessor, issue, detail.toString());
    }

    private static String create(final IModuleInfoProcessor moduleInfoProcessor, final IIssue issue)
    {
        assert moduleInfoProcessor != null : "Parameter 'moduleInfoProcessor' of method 'create' must not be null";
        assert issue != null : "Parameter 'issue' of method 'create' must not be null";
        return create(moduleInfoProcessor, issue, "");
    }

    private void addIssuesToSourceFile(final SensorContext context, final IModuleInfoProcessor moduleInfoProcessor,
            final Map<String, ActiveRule> issueTypeToRuleMap, final String baseDir, final ISourceFile sourceFile, final List<IIssue> issues)
    {
        assert context != null : "Parameter 'context' of method 'addIssuesToSourceFile' must not be null";
        assert moduleInfoProcessor != null : "Parameter 'moduleInfoProcessor' of method 'addIssuesToSourceFile' must not be null";
        assert issueTypeToRuleMap != null : "Parameter 'issueTypeToRuleMap' of method 'addIssuesToSourceFile' must not be null";
        assert sourceFile != null : "Parameter 'sourceFile' of method 'addIssuesToSourceFile' must not be null";
        final String rootDirectoryRelPath = sourceFile.getRelativeRootDirectory();

        //If relativePath then omit rootDirectoryRelPath
        final String sourceRelPath = sourceFile.getRelativePath() != null ? sourceFile.getRelativePath() : sourceFile.getPresentationName();
        final String sourceFileLocation = Paths.get(baseDir, rootDirectoryRelPath, sourceRelPath).normalize().toString();

        final InputPath inputPath = fileSystem
                .inputFile(fileSystem.predicates().hasAbsolutePath(Utility.convertPathToUniversalForm(sourceFileLocation)));
        if (inputPath == null)
        {
            LOGGER.error("Failed to locate resource '{}' at '{}'", sourceFile.getFqName(), sourceFileLocation);
            return;
        }

        for (final IIssue nextIssue : issues)
        {
            final ActiveRule nextRule = issueTypeToRuleMap.get(SonargraphMetrics.createRuleKey(nextIssue.getIssueType().getName()));
            if (nextRule == null)
            {
                LOGGER.debug("Ignoring issue type '{}', because corresponding rule is not activated in current quality profile",
                        nextIssue.getIssueType().getPresentationName());
                continue;
            }

            if (nextIssue instanceof IDuplicateCodeBlockIssue)
            {
                final IDuplicateCodeBlockIssue nextDuplicateCodeBlockIssue = (IDuplicateCodeBlockIssue) nextIssue;
                final List<IDuplicateCodeBlockOccurrence> nextOccurrences = nextDuplicateCodeBlockIssue.getOccurrences();

                for (final IDuplicateCodeBlockOccurrence nextOccurrence : nextOccurrences)
                {
                    if (nextOccurrence.getSourceFile().equals(sourceFile))
                    {
                        final List<IDuplicateCodeBlockOccurrence> others = new ArrayList<>(nextOccurrences);
                        others.remove(nextOccurrence);
                        createIssue(context, inputPath, nextRule, nextOccurrence.getStartLine(),
                                create(moduleInfoProcessor, nextDuplicateCodeBlockIssue, nextOccurrence, others));
                    }
                }
            }
            else
            {
                createIssue(context, inputPath, nextRule, nextIssue.getLine(), create(moduleInfoProcessor, nextIssue));
            }
        }
    }

    private void addIssuesToDirectory(final SensorContext context, final IModuleInfoProcessor moduleInfoProcessor,
            final Map<String, ActiveRule> issueTypeToRuleMap, final String baseDir, final String relDirectory, final List<IIssue> issues)
    {
        assert context != null : "Parameter 'context' of method 'addIssuesToDirectory' must not be null";
        assert moduleInfoProcessor != null : "Parameter 'moduleInfoProcessor' of method 'addIssuesToSourceFile' must not be null";
        assert issueTypeToRuleMap != null : "Parameter 'issueTypeToRuleMap' of method 'addIssuesToSourceFile' must not be null";
        assert relDirectory != null && relDirectory.length() > 0 : "Parameter 'relDirectory' of method 'addIssuesToDirectory' must not be empty";

        final String directoryLocation = Paths.get(baseDir, relDirectory).normalize().toString();
        final InputDir inputDir = fileSystem.inputDir(new File(Utility.convertPathToUniversalForm(directoryLocation)));

        if (inputDir == null)
        {
            LOGGER.error("Failed to locate directory resource: '" + directoryLocation + "'\nBaseDir: " + baseDir + "\nrelDirectory:'" + relDirectory);
            return;
        }

        for (final IIssue nextIssue : issues)
        {
            final ActiveRule nextRule = issueTypeToRuleMap.get(SonargraphMetrics.createRuleKey(nextIssue.getIssueType().getName()));
            if (nextRule == null)
            {
                LOGGER.debug("Ignoring issue type '{}', because corresponding rule is not activated in current quality profile",
                        nextIssue.getIssueType().getPresentationName());
                continue;
            }
            createIssue(context, inputDir, nextRule, nextIssue.getLine(), create(moduleInfoProcessor, nextIssue));
        }
    }

    private void processModule(final SensorContext context, final Map<String, Metric<?>> metrics, final InputModule project,
            final SensorContext sensorContext, final ISoftwareSystem softwareSysten, final Map<String, ActiveRule> issueTypeToRuleMap)
    {
        assert context != null : "Parameter 'context' of method 'processModule' must not be null";
        assert metrics != null : "Parameter 'metrics' of method 'processModule' must not be null";
        assert project != null : "Parameter 'project' of method 'processModule' must not be null";
        assert sensorContext != null : "Parameter 'sensorContext' of method 'processModule' must not be null";
        assert softwareSysten != null : "Parameter 'softwareSysten' of method 'processModule' must not be null";
        assert issueTypeToRuleMap != null : "Parameter 'issueTypeToRuleMap' of method 'processModule' must not be null";

        final Optional<IModule> moduleOptional = determineModuleName(project, softwareSysten);
        if (!moduleOptional.isPresent())
        {
            LOGGER.info("{}: No module found in report for {}", SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, project.key());
            return;
        }

        final IModule module = moduleOptional.get();
        final IModuleInfoProcessor moduleInfoProcessor = controller.createModuleInfoProcessor(module);
        final Optional<IMetricLevel> optionalMetricLevel = moduleInfoProcessor.getMetricLevels().stream()
                .filter(level -> level.getName().equals(IMetricLevel.MODULE)).findAny();

        if (optionalMetricLevel.isPresent())
        {
            processProjectMetrics(sensorContext, module, moduleInfoProcessor, metrics, optionalMetricLevel.get());
        }

        final Map<ISourceFile, List<IIssue>> sourceFileIssueMap = moduleInfoProcessor.getIssuesForSourceFiles(issue -> !issue.isIgnored()
                && !IIssueCategory.StandardName.WORKSPACE.getStandardName().equals(issue.getIssueType().getCategory().getName()));
        for (final Entry<ISourceFile, List<IIssue>> issuesPerSourceFile : sourceFileIssueMap.entrySet())
        {
            addIssuesToSourceFile(context, moduleInfoProcessor, issueTypeToRuleMap, moduleInfoProcessor.getBaseDirectory(),
                    issuesPerSourceFile.getKey(), issuesPerSourceFile.getValue());
        }

        final Map<String, List<IIssue>> directoryIssueMap = moduleInfoProcessor.getIssuesForDirectories(issue -> !issue.isIgnored()
                && !IIssueCategory.StandardName.WORKSPACE.getStandardName().equals(issue.getIssueType().getCategory().getName()));
        for (final Entry<String, List<IIssue>> issuesPerDirectory : directoryIssueMap.entrySet())
        {
            addIssuesToDirectory(context, moduleInfoProcessor, issueTypeToRuleMap, moduleInfoProcessor.getBaseDirectory(),
                    issuesPerDirectory.getKey(), issuesPerDirectory.getValue());
        }
    }

    private void processProjectMetrics(final SensorContext context, final INamedElementContainer container, final IInfoProcessor infoProcessor,
            final Map<String, Metric<?>> metrics, final IMetricLevel level)
    {
        //        final List<String> unconfiguredMetrics = new ArrayList<>();
        //        for (final IMetricId metricId : infoProcessor.getMetricIdsForLevel(level))
        //        {
        //            final String metricKey = SonargraphMetrics.createMetricKeyFromStandardName(metricId.getName());
        //            if (!isConfiguredMetric(metrics, metricId))
        //            {
        //                unconfiguredMetrics.add(metricId.getName());
        //                continue;
        //            }
        //
        //            final Optional<IMetricValue> value = infoProcessor.getMetricValueForElement(metricId, level, container.getFqName());
        //            if (value.isPresent())
        //            {
        //                LOGGER.debug("Processing metric id: {}, metricKey: {}", metricId.getName(), metricKey);
        //                final Measure<Double> measure = new Measure<>(metricKey);
        //                measure.setValue(value.get().getValue().doubleValue());
        //                context.saveMeasure(measure);
        //            }
        //            else
        //            {
        //                LOGGER.error("No value found for metric '{}'. Please check the meta-data configuration for Sonargraph!",
        //                        metricId.getPresentationName());
        //            }
        //        }
        //        if (!unconfiguredMetrics.isEmpty())
        //        {
        //            final StringJoiner joiner = new StringJoiner(", ");
        //            unconfiguredMetrics.stream().forEach(joiner::add);
        //            if (LOGGER.isWarnEnabled())
        //            {
        //                LOGGER.warn("The following Sonargraph metrics have not been configured: \n    " + "{}"
        //                        + "\n    If you want to persist the values for these metrics in SonarQube, "
        //                        + "go to the plugin's configuration in the SonarQube web server and specify the directory where the exported report meta-data files can be found.",
        //                        joiner.toString());
        //            }
        //        }
        //
        //        context.saveMeasure(new Measure<String>(SonargraphMetrics.CURRENT_VIRTUAL_MODEL, controller.getSoftwareSystem().getVirtualModel()));
        //
        //        calculateStructuralCost(context, infoProcessor);
        //        calculateMetricsForStructuralDebtWidget(context, infoProcessor);
        //        calculateMetricsForArchitectureWidget(metrics, context, level, infoProcessor);
        //        calculateMetricsForStructureWidget(context, level, infoProcessor, container);
    }

    private void calculateStructuralCost(final SensorContext context, final IInfoProcessor infoProcessor)
    {
        final Float indexCost = this.settings.getFloat(SonargraphPluginBase.COST_PER_INDEX_POINT);
        if (indexCost == null)
        {
            return;
        }

        final Optional<IMetricValue> value = infoProcessor
                .getMetricValue(IJavaMetricId.StandardName.JAVA_STRUCTURAL_DEBT_INDEX_PACKAGES.getStandardName());
        if (value.isPresent())
        {
            final double cost = (double) indexCost * value.get().getValue().intValue();
            if (cost >= 0)
            {
                //                context.saveMeasure(new Measure<Double>(SonargraphMetrics.STRUCTURAL_DEBT_COST, cost));
            }
        }
    }

    private void createIssue(final SensorContext context, final InputPath resource, final ActiveRule rule, final int line, final String msg)
    {
        assert resource != null : "Parameter 'resource' of method 'createIssue' must not be null";
        assert rule != null : "Parameter 'rule' of method 'createIssue' must not be null";
        assert msg != null && msg.length() > 0 : "Parameter 'msg' of method 'createIssue' must not be empty";

        final NewIssue newIssue = context.newIssue();
        final NewIssueLocation newIssueLocation = newIssue.newLocation();
        newIssueLocation.on(resource);

        newIssue.forRule(rule.getRule().ruleKey());
        if (rule.getSeverity() != null)
        {
            //TODO
            //            newIssue.overrideSeverity(rule.getSeverity());
        }

        if (line > 0)
        {
            newIssueLocation.at(new DefaultTextRange(new DefaultTextPointer(line, 0), new DefaultTextPointer(line, 1)));
        }
        newIssueLocation.message(msg);

    }

    private static void calculateMetricsForStructureWidget(final SensorContext context, final IMetricLevel level, final IInfoProcessor infoProcessor,
            final INamedElementContainer container)
    {
        final String packagesMetricId = IJavaMetricId.StandardName.JAVA_PACKAGES.getStandardName();
        final Optional<IMetricId> packagesMetric = infoProcessor.getMetricId(level, packagesMetricId);
        final String cyclicPackagesMetricId = IJavaMetricId.StandardName.JAVA_CYCLIC_PACKAGES.getStandardName();
        final Optional<IMetricId> cyclicPackagesMetric = infoProcessor.getMetricId(level, cyclicPackagesMetricId);

        LOGGER.debug("Adding cyclic packages metric");
        //        if (packagesMetric.isPresent() && cyclicPackagesMetric.isPresent())
        //        {
        //            final Optional<IMetricValue> numberOfPackagesOptional = infoProcessor.getMetricValueForElement(packagesMetric.get(), level,
        //                    container.getFqName());
        //            assert numberOfPackagesOptional.isPresent() : "If key " + packagesMetricId + " is contained, the value must be present!";
        //            final double numberOfPackages = numberOfPackagesOptional.get().getValue().doubleValue();
        //
        //            final Optional<IMetricValue> numberOfCyclicPackagesOptional = infoProcessor.getMetricValueForElement(cyclicPackagesMetric.get(), level,
        //                    container.getFqName());
        //            assert numberOfPackagesOptional.isPresent() : "If key " + cyclicPackagesMetricId + " is contained, the value must be present!";
        //            final double numberOfCyclicPackages = numberOfCyclicPackagesOptional.get().getValue().doubleValue();
        //
        //            final double cylicPackagesPercent = Utility.round((numberOfCyclicPackages / numberOfPackages) * 100.0, 2);
        //            context.saveMeasure(new Measure<Integer>(SonargraphMetrics.CYCLIC_PACKAGES_PERCENT, cylicPackagesPercent));
        //        }
    }

    private void calculateMetricsForArchitectureWidget(final Map<String, Metric<?>> metrics, final SensorContext context, final IMetricLevel level,
            final IInfoProcessor infoProcessor)
    {
        assert metrics != null : "Parameter 'metrics' of method 'calculateMetricsForArchitectureWidget' must not be null";
        assert context != null : "Parameter 'sensorContext' of method 'calculateMetricsForArchitectureWidget' must not be null";
        assert level != null : "Parameter 'level' of method 'calculateMetricsForArchitectureWidget' must not be null";
        assert infoProcessor != null : "Parameter 'infoProcessor' of method 'calculateMetricsForArchitectureWidget' must not be null";

        //        final double numberOfIssues = infoProcessor.getIssues(null).size();
        //        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_ISSUES, numberOfIssues));
        //
        //        final double numberOfUnresolvedCriticalIssues = infoProcessor.getIssues(issue -> !issue.hasResolution()
        //                && (issue.getIssueType().getSeverity() == Severity.WARNING || issue.getIssueType().getSeverity() == Severity.ERROR)).size();
        //        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_CRITICAL_ISSUES_WITHOUT_RESOLUTION, numberOfUnresolvedCriticalIssues));
        //
        //        final double numberOfUnresolvedThresholdViolations = infoProcessor
        //                .getIssues(issue -> !issue.hasResolution()
        //                        && IIssueCategory.StandardName.THRESHOLD_VIOLATION.getStandardName().equals(issue.getIssueType().getCategory().getName()))
        //                .size();
        //        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_THRESHOLD_VIOLATIONS, numberOfUnresolvedThresholdViolations));
        //
        //        final double numberOfIgnoredCriticalIssues = infoProcessor.getResolutions(resolution -> resolution.getType() == ResolutionType.IGNORE
        //                && resolution.getIssues().stream().anyMatch((final IIssue issue) -> issue.getIssueType().getSeverity() == Severity.WARNING
        //                        || issue.getIssueType().getSeverity() == Severity.ERROR))
        //                .size();
        //        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_IGNORED_CRITICAL_ISSUES, numberOfIgnoredCriticalIssues));
        //
        //        numberOfWorkspaceWarnings = infoProcessor.getIssues(issue -> !issue.hasResolution()
        //                && IIssueCategory.StandardName.WORKSPACE.getStandardName().equals(issue.getIssueType().getCategory().getName())).size();
        //        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_WORKSPACE_WARNINGS, Double.valueOf(numberOfWorkspaceWarnings)));
        //
        //        final Optional<Metric<?>> numberOfComponentsMetric = getSonarQubeMetric(metrics, IMetricId.StandardName.CORE_COMPONENTS.getStandardName());
        //        if (numberOfComponentsMetric.isPresent())
        //        {
        //            calculateArchitecturePercentages(metrics, context, infoProcessor);
        //        }
    }

    private static void calculateArchitecturePercentages(final Map<String, Metric<?>> metrics, final SensorContext context,
            final IInfoProcessor infoProcessor)
    {
        final Optional<IMetricValue> coreComponentsValue = infoProcessor.getMetricValue(IMetricId.StandardName.CORE_COMPONENTS.getStandardName());

        if (!coreComponentsValue.isPresent())
        {
            return;
        }

        final double numberOfComponents = coreComponentsValue.get().getValue().doubleValue();
        if (numberOfComponents <= 0)
        {
            return;
        }

        final Optional<Metric<?>> numberOfUnassignedComponentsMetric = getSonarQubeMetric(metrics,
                IMetricId.StandardName.CORE_UNASSIGNED_COMPONENTS.getStandardName());
        final Optional<IMetricValue> unassignedComponentsValue = infoProcessor
                .getMetricValue(IMetricId.StandardName.CORE_UNASSIGNED_COMPONENTS.getStandardName());
        if (numberOfUnassignedComponentsMetric.isPresent() && unassignedComponentsValue.isPresent())
        {
            final double unassignedComponents = unassignedComponentsValue.get().getValue().doubleValue();
            final double unassignedComponentsPercent = Utility.round((unassignedComponents / numberOfComponents) * 100.0, 2);
            //            context.saveMeasure(new Measure<Integer>(SonargraphMetrics.UNASSIGNED_COMPONENTS_PERCENT, unassignedComponentsPercent));
        }

        final Optional<Metric<?>> numberOfViolatingComponentsMetric = getSonarQubeMetric(metrics,
                IMetricId.StandardName.CORE_VIOLATING_COMPONENTS.getStandardName());
        final Optional<IMetricValue> violatingComponentsValue = infoProcessor
                .getMetricValue(IMetricId.StandardName.CORE_VIOLATING_COMPONENTS.getStandardName());
        if (numberOfViolatingComponentsMetric.isPresent() && violatingComponentsValue.isPresent())
        {
            final double numberOfViolatingComponents = violatingComponentsValue.get().getValue().doubleValue();
            final double violatingComponentsPercent = Utility.round((numberOfViolatingComponents / numberOfComponents) * 100.0, 2);
            //            context.saveMeasure(new Measure<Integer>(SonargraphMetrics.VIOLATING_COMPONENTS_PERCENT, violatingComponentsPercent));
        }
    }

    private void calculateMetricsForStructuralDebtWidget(final SensorContext context, final IInfoProcessor infoProcessor)
    {
        //        final double numberOfResolutions = infoProcessor.getResolutions(null).size();
        //        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_RESOLUTIONS, numberOfResolutions));
        //
        //        final double numberOfUnapplicableResolutions = infoProcessor.getResolutions(r -> !r.isApplicable()).size();
        //        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_UNAPPLICABLE_RESOLUTIONS, numberOfUnapplicableResolutions));
        //
        //        final double numberOfTasks = infoProcessor.getResolutions(IResolution::isTask).size();
        //        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_TASKS, numberOfTasks));
        //
        //        final double numberOfUnapplicableTasks = infoProcessor.getResolutions(r -> r.isTask() && !r.isApplicable()).size();
        //        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_UNAPPLICABLE_TASKS, numberOfUnapplicableTasks));
        //
        //        final List<IResolution> refactorings = infoProcessor.getResolutions(r -> r.getType() == ResolutionType.REFACTORING);
        //        final double numberOfRefactorings = refactorings.size();
        //        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_REFACTORINGS, numberOfRefactorings));
        //        final List<IResolution> applicableRefactorings = refactorings.stream().filter(IResolution::isApplicable).collect(Collectors.toList());
        //
        //        final double numberOfUnapplicableRefactorings = numberOfRefactorings - applicableRefactorings.size();
        //        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_UNAPPLICABLE_REFACTORINGS, numberOfUnapplicableRefactorings));
        //
        //        final double numberOfAffectedParserDepencencies = applicableRefactorings.stream().mapToInt(IResolution::getNumberOfAffectedParserDependencies)
        //                .sum();
        //        LOGGER.debug("Detected {} parser dependencies affected by refactorings", numberOfAffectedParserDepencencies);
        //        context.saveMeasure(
        //                new Measure<Integer>(SonargraphMetrics.NUMBER_OF_PARSER_DEPENDENCIES_AFFECTED_BY_REFACTORINGS, numberOfAffectedParserDepencencies));
    }

    private static boolean isConfiguredMetric(final Map<String, Metric<?>> configuredMetrics, final IMetricId metricId)
    {
        final String metricKey = SonargraphMetrics.createMetricKeyFromStandardName(metricId.getName());
        return configuredMetrics.containsKey(metricKey);
    }

    private static Optional<Metric<?>> getSonarQubeMetric(final Map<String, Metric<?>> metrics, final String name)
    {
        final String metricKey = SonargraphMetrics.createMetricKeyFromStandardName(name);
        return Optional.ofNullable(metrics.get(metricKey));
    }

    Result getProcessReportResult()
    {
        return loadReportResult;
    }

    int getNumberOfWorkspaceWarnings()
    {
        return numberOfWorkspaceWarnings;
    }

    @Override
    public void describe(final SensorDescriptor descriptor)
    {
        assert descriptor != null : "Parameter 'descriptor' of method 'describe' must not be null";
        descriptor.name(SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
        //        descriptor.createIssuesForRuleRepository(repositoryKey)
    }

    @Override
    public void execute(final SensorContext context)
    {
        assert context != null : "Parameter 'context' of method 'execute' must not be null";

        final InputModule project = context.module();

        if (profile.getActiveRulesByRepository(SonargraphPluginBase.PLUGIN_KEY).isEmpty())
        {
            LOGGER.warn(SEPARATOR);
            LOGGER.warn("{}: Skipping project {}, since no Sonargraph rules are activated in current SonarQube quality profile [{}].",
                    SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, project.key(), profile.getName());
            LOGGER.warn(SEPARATOR);
            return;
        }

        if (!determineReportFile(fileSystem, settings).isPresent())
        {
            LOGGER.warn(SEPARATOR);
            LOGGER.warn("{}: Skipping project {}, since no Sonargraph report is found.", SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                    project.key());
            LOGGER.warn(SEPARATOR);
            return;
        }

        LOGGER.info("{}: Executing for module {}", SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, project.key());

        controller = ControllerAccess.createController();
        numberOfWorkspaceWarnings = 0;
        final Optional<File> reportFileOpt = determineReportFile(fileSystem, settings);
        if (!reportFileOpt.isPresent())
        {
            LOGGER.error("{}: Failed to read Sonargraph report!", SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
            loadReportResult = new Result("Loading Sonargraph report");
            loadReportResult.addError(ResultCause.FILE_NOT_FOUND, "No Sonargraph report found!");
            return;
        }

        final File reportFile = reportFileOpt.get();
        LOGGER.info("{}: Reading Sonargraph report from: {}", SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, reportFile.getAbsolutePath());
        loadReportResult = loadReport(project, reportFile, settings);
        if (loadReportResult.isFailure())
        {
            return;
        }

        final ISoftwareSystem softwareSystem = controller.getSoftwareSystem();
        if (softwareSystem.getModules().size() == 0)
        {
            final String msg = "No modules defined for Sonargraph system, please check the workspace definition!";
            LOGGER.warn("{}: {}", SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, msg);
            loadReportResult.addWarning(ReportProcessingMessageCause.NO_MODULES, msg);
            return;
        }

        final Map<String, Metric<? extends Serializable>> metrics = metricFinder.findAll().stream()
                .filter(m -> m.key().startsWith(SonargraphPluginBase.ABBREVIATION)).collect(Collectors.toMap(Metric::key, m -> m));

        final ISystemInfoProcessor systemInfoProcessor = controller.createSystemInfoProcessor();
        processFeatures(context, systemInfoProcessor);
        //        if (project.isRoot())
        //        {
        //TODO
        processSystemMetrics(metrics, context, systemInfoProcessor, softwareSystem);
        //        }

        final Map<String, ActiveRule> issueTypeToRuleMap = new HashMap<>();
        for (final IIssueType nextIssueType : systemInfoProcessor.getIssueTypes())
        {
            final String nextIssueTypeName = nextIssueType.getName();
            final ActiveRule rule = profile.getActiveRule(SonargraphPluginBase.PLUGIN_KEY, SonargraphMetrics.createRuleKey(nextIssueTypeName));
            final String ruleKey = SonargraphMetrics.createRuleKey(nextIssueTypeName);
            if (rule == null)
            {
                LOGGER.info("Rule '{}' is not activated.", ruleKey);
                continue;
            }
            issueTypeToRuleMap.put(ruleKey, rule);
        }

        processModule(context, metrics, project, context, softwareSystem, issueTypeToRuleMap);

        LOGGER.info("{}: Finished processing of {}", SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, project.key());
        if (numberOfWorkspaceWarnings > 0)
        {
            LOGGER.warn(
                    "{}: Found {} workspace warnings. Sonargraph metrics might not be correct. "
                            + "Please check that all root directories of the Sonargraph workspace are correct "
                            + "and that class files have been generated before executing Sonargraph to create a report.",
                    SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, numberOfWorkspaceWarnings);
        }
    }
}