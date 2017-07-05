/**
 * SonarQube Sonargraph Integration Plugin
 * Copyright (C) 2016-2017 hello2morrow GmbH
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
package com.hello2morrow.sonargraph.integration.sonarqube.api;

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
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.batch.measure.Metric;
import org.sonar.api.batch.measure.MetricFinder;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issuable;
import org.sonar.api.issue.Issuable.IssueBuilder;
import org.sonar.api.issue.Issue;
import org.sonar.api.measures.Measure;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Project;
import org.sonar.api.rules.ActiveRule;

import com.hello2morrow.sonargraph.integration.access.controller.ControllerFactory;
import com.hello2morrow.sonargraph.integration.access.controller.IInfoProcessor;
import com.hello2morrow.sonargraph.integration.access.controller.IModuleInfoProcessor;
import com.hello2morrow.sonargraph.integration.access.controller.ISonargraphSystemController;
import com.hello2morrow.sonargraph.integration.access.controller.ISystemInfoProcessor;
import com.hello2morrow.sonargraph.integration.access.foundation.IOMessageCause;
import com.hello2morrow.sonargraph.integration.access.foundation.NumberUtility;
import com.hello2morrow.sonargraph.integration.access.foundation.OperationResult;
import com.hello2morrow.sonargraph.integration.access.foundation.OperationResult.IMessageCause;
import com.hello2morrow.sonargraph.integration.access.foundation.StringUtility;
import com.hello2morrow.sonargraph.integration.access.model.IDuplicateCodeBlockIssue;
import com.hello2morrow.sonargraph.integration.access.model.IDuplicateCodeBlockOccurrence;
import com.hello2morrow.sonargraph.integration.access.model.IElementContainer;
import com.hello2morrow.sonargraph.integration.access.model.IFeature;
import com.hello2morrow.sonargraph.integration.access.model.IIssue;
import com.hello2morrow.sonargraph.integration.access.model.IIssueCategory;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;
import com.hello2morrow.sonargraph.integration.access.model.IMetricLevel;
import com.hello2morrow.sonargraph.integration.access.model.IMetricValue;
import com.hello2morrow.sonargraph.integration.access.model.IModule;
import com.hello2morrow.sonargraph.integration.access.model.INamedElement;
import com.hello2morrow.sonargraph.integration.access.model.IResolution;
import com.hello2morrow.sonargraph.integration.access.model.ISoftwareSystem;
import com.hello2morrow.sonargraph.integration.access.model.ISourceFile;
import com.hello2morrow.sonargraph.integration.access.model.ResolutionType;
import com.hello2morrow.sonargraph.integration.access.model.Severity;
import com.hello2morrow.sonargraph.integration.access.model.java.IJavaMetricId;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.DuplicateFixResolutionIssue;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.FixResolutionIssueType;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.PluginVersionReader;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphPluginBase;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.StandardFixResolutionIssue;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.Utilities;

public final class SonargraphSensor implements Sensor
{
    public enum ReportProcessingMessageCause implements IMessageCause
    {
        NO_MODULES;

        @Override
        public String getStandardName()
        {
            return StringUtility.convertConstantNameToStandardName(name());
        }

        @Override
        public String getPresentationName()
        {
            return StringUtility.convertConstantNameToPresentationName(name());
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SonargraphSensor.class);
    private static final String SEPARATOR = "----------------------------------------------------------------";
    private static final String SONARGRAPH_TARGET_DIR = "sonargraph";
    private static final String SONARGRAPH_SONARQUBE_REPORT_FILENAME = "sonargraph-sonarqube-report.xml";

    private final RulesProfile profile;
    private final Settings settings;
    private final FileSystem fileSystem;
    private final ResourcePerspectives perspectives;
    private OperationResult loadReportResult;
    private ISonargraphSystemController controller;
    private Exception sensorExecutionException;
    private final MetricFinder metricFinder;
    private int numberOfWorkspaceWarnings = 0;

    public SonargraphSensor(final MetricFinder metricFinder, final RulesProfile profile, final Settings settings, final FileSystem moduleFileSystem,
            final ResourcePerspectives perspectives)
    {
        this.metricFinder = metricFinder;
        this.profile = profile;
        this.settings = settings;
        this.fileSystem = moduleFileSystem;
        this.perspectives = perspectives;
    }

    Exception getSensorExecutionException()
    {
        return sensorExecutionException;
    }

    /* called from maven */
    @Override
    public boolean shouldExecuteOnProject(final Project project)
    {
        if (!Utilities.areSonargraphRulesActive(this.profile))
        {
            LOGGER.warn(SEPARATOR);
            LOGGER.warn("{}: Skipping project {} [{}], since no Sonargraph rules are activated in current SonarQube quality profile [{}].",
                    SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, project.getName(), project.getKey(), profile.getName());
            LOGGER.warn(SEPARATOR);
            return false;
        }

        if (!determineReportFile(fileSystem, settings).isPresent())
        {
            LOGGER.warn(SEPARATOR);
            LOGGER.warn("{}: Skipping project {} [{}], since no Sonargraph report is found.",
                    SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, project.getName(), project.getKey());
            LOGGER.warn(SEPARATOR);
            return false;
        }
        return true;
    }

    @Override
    public void analyse(final Project proj, final SensorContext context)
    {
        LOGGER.info("{}: Executing for module {} [{}]", SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, proj.getName(), proj.getKey());

        controller = new ControllerFactory().createController();
        numberOfWorkspaceWarnings = 0;
        final Optional<File> reportFileOpt = determineReportFile(fileSystem, settings);
        if (!reportFileOpt.isPresent())
        {
            LOGGER.error("{}: Failed to read Sonargraph report!", SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
            loadReportResult = new OperationResult("Loading Sonargraph report");
            loadReportResult.addError(IOMessageCause.FILE_NOT_FOUND, "No Sonargraph report found!");
            return;
        }

        final File reportFile = reportFileOpt.get();
        LOGGER.info("{}: Reading Sonargraph report from: {}", SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, reportFile.getAbsolutePath());
        loadReportResult = loadReport(proj, reportFile, settings);
        if (loadReportResult.isFailure())
        {
            return;
        }

        final ISoftwareSystem sonargraphSystem = controller.getSoftwareSystem();
        if (sonargraphSystem.getModules().size() == 0)
        {
            final String msg = "No modules defined for Sonargraph system, please check the workspace definition!";
            LOGGER.warn("{}: {}", SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, msg);
            loadReportResult.addWarning(ReportProcessingMessageCause.NO_MODULES, msg);
            return;
        }

        @SuppressWarnings("unchecked")
        final Map<String, Metric<? extends Serializable>> metrics = metricFinder.findAll().stream()
                .filter(m -> m.key().startsWith(SonargraphPluginBase.ABBREVIATION)).collect(Collectors.toMap(Metric::key, m -> m));

        processFeatures(context);
        if (proj.isRoot())
        {
            processSystemMetrics(metrics, context);
        }
        processModule(metrics, proj, context, sonargraphSystem);

        LOGGER.info("{}: Finished processing of {} [{}]", SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, proj.getName(), proj.getKey());
        if (numberOfWorkspaceWarnings > 0)
        {
            LOGGER.warn("{}: Found {} workspace warnings. Sonargraph metrics might not be correct. "
                    + "Please check that all root directories of the Sonargraph workspace are correct "
                    + "and that class files have been generated before executing Sonargraph to create a report.",
                    SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, numberOfWorkspaceWarnings);
        }
    }

    private void processFeatures(final SensorContext sensorContext)
    {
        final ISystemInfoProcessor systemInfoProcessor = controller.createSystemInfoProcessor();
        for (final IFeature feature : systemInfoProcessor.getFeatures())
        {
            if (feature.getName().equals(IFeature.ARCHITECTURE) && feature.isLicensed())
            {
                sensorContext.saveMeasure(new Measure<Boolean>(SonargraphMetrics.ARCHITECTURE_FEATURE_AVAILABLE, 1.0));
            }

            if (feature.getName().equals(IFeature.VIRTUAL_MODELS) && feature.isLicensed())
            {
                sensorContext.saveMeasure(new Measure<Boolean>(SonargraphMetrics.VIRTUAL_MODEL_FEATURE_AVAILABLE, 1.0));
            }
        }
    }

    private OperationResult loadReport(final Project project, final File reportFile, final Settings settings)
    {
        final OperationResult result = new OperationResult("Reading Sonargraph report from: " + reportFile.getAbsolutePath());
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
        if (result.isFailure() && LOGGER.isErrorEnabled())
        {
            LOGGER.error("Failed to execute Sonargraph plugin for {} [{}]", project.getName(), project.getKey());
            LOGGER.error(result.toString());
        }
        return result;
    }

    private void processSystemMetrics(final Map<String, Metric<?>> metrics, final SensorContext sensorContext)
    {
        final ISoftwareSystem softwareSystem = controller.getSoftwareSystem();
        final ISystemInfoProcessor infoProcessor = controller.createSystemInfoProcessor();
        final Optional<IMetricLevel> systemLevel = infoProcessor.getMetricLevel(IMetricLevel.SYSTEM);
        assert systemLevel.isPresent() : "Metric level 'system' not found";

        processProjectMetrics(sensorContext, softwareSystem, infoProcessor, metrics, systemLevel.get());

        final Map<INamedElement, IMetricValue> nccdValues = infoProcessor.getMetricValues(IMetricLevel.MODULE,
                IMetricId.StandardName.CORE_NCCD.getStandardName());
        final OptionalDouble highestNccd = nccdValues.values().stream().mapToDouble(v -> v.getValue().doubleValue()).max();
        if (highestNccd.isPresent())
        {
            sensorContext.saveMeasure(new Measure<Double>(SonargraphMetrics.MAX_MODULE_NCCD, highestNccd.getAsDouble()));
        }
    }

    private void processModule(final Map<String, Metric<?>> metrics, final Project project, final SensorContext sensorContext,
            final ISoftwareSystem system)
    {
        final Optional<IModule> moduleOptional = determineModuleName(project, system);
        if (!moduleOptional.isPresent())
        {
            LOGGER.info("{}: No module found in report for {} [{}]", SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, project.getName(),
                    project.getKey());
            return;
        }

        final IModule module = moduleOptional.get();
        final IModuleInfoProcessor moduleInfoProcessor = controller.createModuleInfoProcessor(module);
        final Optional<IMetricLevel> optionalMetricLevel = moduleInfoProcessor.getMetricLevels().stream()
                .filter(level -> level.getName().equals(IMetricLevel.MODULE)).findAny();

        if (project.isModule() && optionalMetricLevel.isPresent())
        {
            processProjectMetrics(sensorContext, module, moduleInfoProcessor, metrics, optionalMetricLevel.get());
        }

        processIssues(
                moduleInfoProcessor,
                issue -> !issue.hasResolution()
                        && !IIssueCategory.StandardName.WORKSPACE.getStandardName().equals(issue.getIssueType().getCategory().getName()));
        processIssues(
                moduleInfoProcessor,
                issue -> issue.hasResolution()
                        && issue.getIssueType().getCategory().getName().equals(IIssueCategory.StandardName.TODO.getStandardName()));
        processIssues(
                moduleInfoProcessor,
                issue -> issue.hasResolution()
                        && issue.getIssueType().getCategory().getName().equals(IIssueCategory.StandardName.REFACTORING.getStandardName()));

        processFixResolutions(moduleInfoProcessor);
    }

    private void processFixResolutions(final IModuleInfoProcessor infoProcessor)
    {
        final String ruleKey = SonargraphMetrics.createRuleKey(FixResolutionIssueType.FIX_RESOLUTION_RULE);
        final ActiveRule rule = profile.getActiveRule(SonargraphPluginBase.PLUGIN_KEY, ruleKey);
        if (rule == null)
        {
            LOGGER.info("Rule '{}' is not activated, no issues for 'Fix' resolutions are created.", ruleKey);
            return;
        }
        final Map<ISourceFile, Map<IResolution, List<IIssue>>> issueMap = infoProcessor
                .getIssuesForResolutionsForSourceFiles(r -> r.getType() == ResolutionType.FIX);
        final Map<String, ActiveRule> issueTypeToRuleMap = new HashMap<>();
        issueTypeToRuleMap.put(ruleKey, rule);
        for (final Map.Entry<ISourceFile, Map<IResolution, List<IIssue>>> next : issueMap.entrySet())
        {
            final ISourceFile source = next.getKey();
            final List<IIssue> issues = new ArrayList<>();
            final Map<IResolution, List<IIssue>> resolutionToIssueMap = next.getValue();
            for (final Map.Entry<IResolution, List<IIssue>> resolutionToIssue : resolutionToIssueMap.entrySet())
            {
                for (final IIssue issue : resolutionToIssue.getValue())
                {
                    if (issue instanceof IDuplicateCodeBlockIssue)
                    {
                        issues.add(new DuplicateFixResolutionIssue(resolutionToIssue.getKey(), (IDuplicateCodeBlockIssue) issue));
                    }
                    else
                    {
                        issues.add(new StandardFixResolutionIssue(resolutionToIssue.getKey(), issue));
                    }
                }
            }
            addIssuesToSourceFile(issueTypeToRuleMap, infoProcessor.getBaseDirectory(), source, issues);
        }
    }

    private void processProjectMetrics(final SensorContext context, final IElementContainer container, final IInfoProcessor infoProcessor,
            final Map<String, Metric<?>> metrics, final IMetricLevel level)
    {
        final List<String> unconfiguredMetrics = new ArrayList<>();
        for (final IMetricId metricId : infoProcessor.getMetricIdsForLevel(level))
        {
            final String metricKey = SonargraphMetrics.createMetricKeyFromStandardName(metricId.getName());
            if (!isConfiguredMetric(metrics, metricId))
            {
                unconfiguredMetrics.add(metricId.getName());
                continue;
            }

            final Optional<IMetricValue> value = infoProcessor.getMetricValueForElement(metricId, level, container.getFqName());
            if (value.isPresent())
            {
                LOGGER.debug("Processing metric id: {}, metricKey: {}", metricId.getName(), metricKey);
                final Measure<Double> measure = new Measure<>(metricKey);
                measure.setValue(value.get().getValue().doubleValue());
                context.saveMeasure(measure);
            }
            else
            {
                LOGGER.error("No value found for metric '{}'. Please check the meta-data configuration for Sonargraph!",
                        metricId.getPresentationName());
            }
        }
        if (!unconfiguredMetrics.isEmpty())
        {
            final StringJoiner joiner = new StringJoiner(", ");
            unconfiguredMetrics.stream().forEach(joiner::add);
            if (LOGGER.isWarnEnabled())
            {
                LOGGER.warn(
                        "The following Sonargraph metrics have not been configured: \n    "
                                + "{}"
                                + "\n    If you want to persist the values for these metrics in SonarQube, "
                                + "go to the plugin's configuration in the SonarQube web server and specify the directory where the exported report meta-data files can be found.",
                        joiner.toString());
            }
        }

        context.saveMeasure(new Measure<String>(SonargraphMetrics.CURRENT_VIRTUAL_MODEL, controller.getSoftwareSystem().getVirtualModel()));

        calculateStructuralCost(context, infoProcessor);
        calculateMetricsForStructuralDebtWidget(context, infoProcessor);
        calculateMetricsForArchitectureWidget(metrics, context, level, infoProcessor);
        calculateMetricsForStructureWidget(context, level, infoProcessor, container);
    }

    private void calculateStructuralCost(final SensorContext context, final IInfoProcessor infoProcessor)
    {
        final Float indexCost = this.settings.getFloat(SonargraphPluginBase.COST_PER_INDEX_POINT);
        if (indexCost == null)
        {
            return;
        }

        final Optional<IMetricValue> value = infoProcessor.getMetricValue(IJavaMetricId.StandardName.JAVA_STRUCTURAL_DEBT_INDEX_PACKAGES
                .getStandardName());
        if (value.isPresent())
        {
            final double cost = (double) indexCost * value.get().getValue().intValue();
            if (cost >= 0)
            {
                context.saveMeasure(new Measure<Double>(SonargraphMetrics.STRUCTURAL_DEBT_COST, cost));
            }
        }
    }

    private void processIssues(final IModuleInfoProcessor infoProcessor, final Predicate<IIssue> issueFilter)
    {
        final Map<ISourceFile, List<IIssue>> issueMap = infoProcessor.getIssuesForSourceFiles(issueFilter);
        addIssuesForActiveRules(infoProcessor, issueMap);
    }

    private void addIssuesForActiveRules(final IModuleInfoProcessor infoProcessor, final Map<ISourceFile, List<IIssue>> issueMap)
    {
        final Map<String, ActiveRule> issueTypeToRuleMap = new HashMap<>();
        final List<String> types = issueMap.values().stream().flatMap(List<IIssue>::stream).map(issue -> issue.getIssueType().getName()).distinct()
                .collect(Collectors.toList());

        for (final String type : types)
        {
            final ActiveRule rule = profile.getActiveRule(SonargraphPluginBase.PLUGIN_KEY, SonargraphMetrics.createRuleKey(type));
            final String ruleKey = SonargraphMetrics.createRuleKey(type);
            if (rule == null)
            {
                LOGGER.info("Rule '{}' is not activated.", ruleKey);
                continue;
            }
            issueTypeToRuleMap.put(ruleKey, rule);
        }

        for (final Entry<ISourceFile, List<IIssue>> issuesPerSourceFile : issueMap.entrySet())
        {
            addIssuesToSourceFile(issueTypeToRuleMap, infoProcessor.getBaseDirectory(), issuesPerSourceFile.getKey(), issuesPerSourceFile.getValue());
        }
    }

    private void addIssuesToSourceFile(final Map<String, ActiveRule> issueTypeToRuleMap, final String baseDir, final ISourceFile sourceFile,
            final List<IIssue> issues)
    {
        assert issueTypeToRuleMap != null : "Parameter 'issueTypeToRuleMap' of method 'addIssuesToSourceFile' must not be null";
        assert sourceFile != null : "Parameter 'sourceFile' of method 'addIssuesToSourceFile' must not be null";
        final String rootDirectoryRelPath = sourceFile.getRelativeRootDirectoryPath();

        //If relativePath then omit rootDirectoryRelPath
        final String sourceRelPath = sourceFile.getRelativePath() != null ? sourceFile.getRelativePath() : sourceFile.getPresentationName();
        final String sourceFileLocation = Paths.get(baseDir, rootDirectoryRelPath, sourceRelPath).normalize().toString();
        final Optional<InputPath> resource = Utilities.getResource(fileSystem, sourceFileLocation);
        if (!resource.isPresent())
        {
            LOGGER.error("Failed to locate resource '{}' at '{}'", sourceFile.getFqName(), sourceFileLocation);
            return;
        }

        for (final IIssue issue : issues)
        {
            final String issueTypeName = SonargraphMetrics.createRuleKey(issue.getIssueType().getName());
            final ActiveRule rule = issueTypeToRuleMap.get(issueTypeName);
            if (rule == null)
            {
                LOGGER.debug("Ignoring issue type '{}', because corresponding rule is not activated in current quality profile", issue.getIssueType()
                        .getPresentationName());
                continue;
            }

            if (issue instanceof IDuplicateCodeBlockIssue)
            {
                handleDuplicateCodeBlock((IDuplicateCodeBlockIssue) issue, sourceFile, resource.get(), rule);
            }
            else
            {
                handleIssue(issue, resource.get(), rule);
            }
        }
    }

    private void handleIssue(final IIssue issue, final InputPath resource, final ActiveRule rule)
    {
        final Issuable issuable = perspectives.as(Issuable.class, resource);
        if (issuable == null)
        {
            if (LOGGER.isErrorEnabled())
            {
                LOGGER.error("Failed to create Issuable for resource '{}'", resource.absolutePath());
            }
            return;
        }

        final IssueBuilder issueBuilder = issuable.newIssueBuilder();
        issueBuilder.ruleKey(rule.getRule().ruleKey());
        if (rule.getSeverity() != null)
        {
            issueBuilder.severity(rule.getSeverity().toString());
        }

        final String msg = issue.getIssueType().getPresentationName() + ": " + issue.getDescription() + " ["
                + issue.getIssueProvider().getPresentationName() + "]";
        issueBuilder.message(msg);
        final int line = issue.getLineNumber();

        if (line > 0)
        {
            issueBuilder.line(line);
        }

        final Issue sqIssue = issueBuilder.build();
        issuable.addIssue(sqIssue);
    }

    private void handleDuplicateCodeBlock(final IDuplicateCodeBlockIssue issue, final ISourceFile sourceFile, final InputPath resource,
            final ActiveRule rule)
    {
        assert issue != null : "Parameter 'issue' of method 'handleDuplicateCodeBlock' must not be null";
        for (final IDuplicateCodeBlockOccurrence occurrence : issue.getOccurrences())
        {
            if (occurrence.getSourceFile().equals(sourceFile))
            {
                final List<IDuplicateCodeBlockOccurrence> others = new ArrayList<>(issue.getOccurrences());
                others.remove(occurrence);
                handleDuplicateBlockOccurrence(issue, occurrence, others, resource, rule);
            }
        }
    }

    private void handleDuplicateBlockOccurrence(final IDuplicateCodeBlockIssue issue, final IDuplicateCodeBlockOccurrence occurrence,
            final List<IDuplicateCodeBlockOccurrence> others, final InputPath resource, final ActiveRule rule)
    {
        final Issuable issuable = perspectives.as(Issuable.class, resource);
        if (issuable == null)
        {
            if (LOGGER.isErrorEnabled())
            {
                LOGGER.error("Failed to create issuable for resource '{}'", resource.absolutePath());
            }
            return;
        }
        final IssueBuilder builder = issuable.newIssueBuilder();
        builder.ruleKey(rule.getRule().ruleKey());
        if (rule.getSeverity() != null)
        {
            builder.severity(rule.getSeverity().toString());
        }

        final StringBuilder msg = new StringBuilder();

        if (issue instanceof DuplicateFixResolutionIssue)
        {
            msg.append(issue.getIssueType().getCategory().getPresentationName()).append(" [").append(issue.getPresentationName()).append("]")
                    .append(": ").append(issue.getDescription()).append(": ");
        }
        else
        {
            msg.append(issue.getPresentationName()).append(": ").append(issue.getDescription()).append(": ");
        }

        msg.append(issue.getIssueType().getCategory().getPresentationName()).append(" [").append(issue.getPresentationName()).append("]")
                .append(": ").append(issue.getDescription()).append(": ");
        msg.append("\nLine ").append(occurrence.getStartLine()).append(" to ").append(occurrence.getStartLine() + occurrence.getBlockSize() - 1)
                .append(" is a duplicate of");

        for (final IDuplicateCodeBlockOccurrence next : others)
        {
            msg.append("\n");
            msg.append(
                    next.getSourceFile().getRelativePath() != null ? next.getSourceFile().getRelativePath() : next.getSourceFile()
                            .getPresentationName()).append(", line ").append(next.getStartLine()).append(" to ")
                    .append(next.getStartLine() + next.getBlockSize() - 1);
        }

        builder.message(msg.toString());
        builder.line(occurrence.getStartLine());
        final Issue sqIssue = builder.build();
        issuable.addIssue(sqIssue);
    }

    private static void calculateMetricsForStructureWidget(final SensorContext context, final IMetricLevel level, final IInfoProcessor infoProcessor,
            final IElementContainer container)
    {
        final String packagesMetricId = IJavaMetricId.StandardName.JAVA_PACKAGES.getStandardName();
        final Optional<IMetricId> packagesMetric = infoProcessor.getMetricId(level, packagesMetricId);
        final String cyclicPackagesMetricId = IJavaMetricId.StandardName.JAVA_CYCLIC_PACKAGES.getStandardName();
        final Optional<IMetricId> cyclicPackagesMetric = infoProcessor.getMetricId(level, cyclicPackagesMetricId);

        LOGGER.debug("Adding cyclic packages metric");
        if (packagesMetric.isPresent() && cyclicPackagesMetric.isPresent())
        {
            final Optional<IMetricValue> numberOfPackagesOptional = infoProcessor.getMetricValueForElement(packagesMetric.get(), level,
                    container.getFqName());
            assert numberOfPackagesOptional.isPresent() : "If key " + packagesMetricId + " is contained, the value must be present!";
            final double numberOfPackages = numberOfPackagesOptional.get().getValue().doubleValue();

            final Optional<IMetricValue> numberOfCyclicPackagesOptional = infoProcessor.getMetricValueForElement(cyclicPackagesMetric.get(), level,
                    container.getFqName());
            assert numberOfPackagesOptional.isPresent() : "If key " + cyclicPackagesMetricId + " is contained, the value must be present!";
            final double numberOfCyclicPackages = numberOfCyclicPackagesOptional.get().getValue().doubleValue();

            final double cylicPackagesPercent = NumberUtility.round((numberOfCyclicPackages / numberOfPackages) * 100.0, 2);
            context.saveMeasure(new Measure<Integer>(SonargraphMetrics.CYCLIC_PACKAGES_PERCENT, cylicPackagesPercent));
        }
    }

    private void calculateMetricsForArchitectureWidget(final Map<String, Metric<?>> metrics, final SensorContext context, final IMetricLevel level,
            final IInfoProcessor infoProcessor)
    {
        assert metrics != null : "Parameter 'metrics' of method 'calculateMetricsForArchitectureWidget' must not be null";
        assert context != null : "Parameter 'sensorContext' of method 'calculateMetricsForArchitectureWidget' must not be null";
        assert level != null : "Parameter 'level' of method 'calculateMetricsForArchitectureWidget' must not be null";
        assert infoProcessor != null : "Parameter 'infoProcessor' of method 'calculateMetricsForArchitectureWidget' must not be null";

        final double numberOfIssues = infoProcessor.getIssues(null).size();
        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_ISSUES, numberOfIssues));

        final double numberOfUnresolvedCriticalIssues = infoProcessor.getIssues(
                issue -> !issue.hasResolution()
                        && (issue.getIssueType().getSeverity() == Severity.WARNING || issue.getIssueType().getSeverity() == Severity.ERROR)).size();
        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_CRITICAL_ISSUES_WITHOUT_RESOLUTION, numberOfUnresolvedCriticalIssues));

        final double numberOfUnresolvedThresholdViolations = infoProcessor.getIssues(
                issue -> !issue.hasResolution()
                        && IIssueCategory.StandardName.THRESHOLD_VIOLATION.getStandardName().equals(issue.getIssueType().getCategory().getName()))
                .size();
        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_THRESHOLD_VIOLATIONS, numberOfUnresolvedThresholdViolations));

        final double numberOfIgnoredCriticalIssues = infoProcessor.getResolutions(
                resolution -> resolution.getType() == ResolutionType.IGNORE
                        && resolution
                                .getIssues()
                                .stream()
                                .anyMatch(
                                        (final IIssue issue) -> issue.getIssueType().getSeverity() == Severity.WARNING
                                                || issue.getIssueType().getSeverity() == Severity.ERROR)).size();
        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_IGNORED_CRITICAL_ISSUES, numberOfIgnoredCriticalIssues));

        numberOfWorkspaceWarnings = infoProcessor.getIssues(
                issue -> !issue.hasResolution()
                        && IIssueCategory.StandardName.WORKSPACE.getStandardName().equals(issue.getIssueType().getCategory().getName())).size();
        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_WORKSPACE_WARNINGS, Double.valueOf(numberOfWorkspaceWarnings)));

        final Optional<Metric<?>> numberOfComponentsMetric = getSonarQubeMetric(metrics, IMetricId.StandardName.CORE_COMPONENTS.getStandardName());
        if (numberOfComponentsMetric.isPresent())
        {
            calculateArchitecturePercentages(metrics, context, infoProcessor);
        }
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
        final Optional<IMetricValue> unassignedComponentsValue = infoProcessor.getMetricValue(IMetricId.StandardName.CORE_UNASSIGNED_COMPONENTS
                .getStandardName());
        if (numberOfUnassignedComponentsMetric.isPresent() && unassignedComponentsValue.isPresent())
        {
            final double unassignedComponents = unassignedComponentsValue.get().getValue().doubleValue();
            final double unassignedComponentsPercent = NumberUtility.round((unassignedComponents / numberOfComponents) * 100.0, 2);
            context.saveMeasure(new Measure<Integer>(SonargraphMetrics.UNASSIGNED_COMPONENTS_PERCENT, unassignedComponentsPercent));
        }

        final Optional<Metric<?>> numberOfViolatingComponentsMetric = getSonarQubeMetric(metrics,
                IMetricId.StandardName.CORE_VIOLATING_COMPONENTS.getStandardName());
        final Optional<IMetricValue> violatingComponentsValue = infoProcessor.getMetricValue(IMetricId.StandardName.CORE_VIOLATING_COMPONENTS
                .getStandardName());
        if (numberOfViolatingComponentsMetric.isPresent() && violatingComponentsValue.isPresent())
        {
            final double numberOfViolatingComponents = violatingComponentsValue.get().getValue().doubleValue();
            final double violatingComponentsPercent = NumberUtility.round((numberOfViolatingComponents / numberOfComponents) * 100.0, 2);
            context.saveMeasure(new Measure<Integer>(SonargraphMetrics.VIOLATING_COMPONENTS_PERCENT, violatingComponentsPercent));
        }
    }

    private void calculateMetricsForStructuralDebtWidget(final SensorContext context, final IInfoProcessor infoProcessor)
    {
        final double numberOfResolutions = infoProcessor.getResolutions(null).size();
        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_RESOLUTIONS, numberOfResolutions));

        final double numberOfUnapplicableResolutions = infoProcessor.getResolutions(r -> !r.isApplicable()).size();
        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_UNAPPLICABLE_RESOLUTIONS, numberOfUnapplicableResolutions));

        final double numberOfTasks = infoProcessor.getResolutions(IResolution::isTask).size();
        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_TASKS, numberOfTasks));

        final double numberOfUnapplicableTasks = infoProcessor.getResolutions(r -> r.isTask() && !r.isApplicable()).size();
        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_UNAPPLICABLE_TASKS, numberOfUnapplicableTasks));

        final List<IResolution> refactorings = infoProcessor.getResolutions(r -> r.getType() == ResolutionType.REFACTORING);
        final double numberOfRefactorings = refactorings.size();
        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_REFACTORINGS, numberOfRefactorings));
        final List<IResolution> applicableRefactorings = refactorings.stream().filter(IResolution::isApplicable).collect(Collectors.toList());

        final double numberOfUnapplicableRefactorings = numberOfRefactorings - applicableRefactorings.size();
        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_UNAPPLICABLE_REFACTORINGS, numberOfUnapplicableRefactorings));

        final double numberOfAffectedParserDepencencies = applicableRefactorings.stream()
                .mapToInt(IResolution::getNumberOfAffectedParserDependencies).sum();
        LOGGER.debug("Detected {} parser dependencies affected by refactorings", numberOfAffectedParserDepencencies);
        context.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_PARSER_DEPENDENCIES_AFFECTED_BY_REFACTORINGS,
                numberOfAffectedParserDepencencies));
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

    @Override
    public String toString()
    {
        return SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + " [" + PluginVersionReader.getInstance().getVersion() + "]";
    }

    OperationResult getProcessReportResult()
    {
        return loadReportResult;
    }

    private static Optional<File> determineReportFile(final FileSystem fileSystem, final Settings settings)
    {
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
            //try maven path
            final File mavenDefaultLocation = Paths.get(fileSystem.workDir().getParentFile().getAbsolutePath(), SONARGRAPH_TARGET_DIR,
                    SONARGRAPH_SONARQUBE_REPORT_FILENAME).toFile();
            if (fileExistsAndIsReadable(mavenDefaultLocation))
            {
                reportFile = mavenDefaultLocation;
            }
            else
            {
                //try gradle path
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

    private static boolean fileExistsAndIsReadable(final File reportFile)
    {
        return reportFile.exists() && reportFile.canRead();
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

    private static Optional<IModule> determineModuleName(final Project project, final ISoftwareSystem system)
    {
        final Map<String, IModule> modules = system.getModules();

        if (modules.size() == 1)
        {
            return Optional.of(modules.values().iterator().next());
        }

        for (final Entry<String, IModule> next : modules.entrySet())
        {
            final IModule module = next.getValue();
            final String buName = Utilities.getBuildUnitName(module.getFqName());
            if (Utilities.buildUnitMatchesAnalyzedProject(buName, project))
            {
                return Optional.of(module);
            }
        }
        return Optional.empty();
    }

    int getNumberOfWorkspaceWarnings()
    {
        return numberOfWorkspaceWarnings;
    }
}