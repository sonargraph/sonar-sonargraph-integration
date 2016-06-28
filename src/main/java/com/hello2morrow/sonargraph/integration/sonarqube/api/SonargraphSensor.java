/**
 * SonarQube Sonargraph Integration Plugin
 * Copyright (C) 2016 hello2morrow GmbH
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
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.PluginVersionReader;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphPluginBase;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.Utilities;

public final class SonargraphSensor implements Sensor
{
    private static final Logger LOG = LoggerFactory.getLogger(SonargraphSensor.class);
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
            LOG.warn(SEPARATOR);
            LOG.warn(SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Skipping project " + project.getName() + " [" + project.getKey()
                    + "], since no Sonargraph rules are activated in current SonarQube quality profile [" + profile.getName() + "].");
            LOG.warn(SEPARATOR);
            return false;
        }

        if (!determineReportFile(fileSystem, settings).isPresent())
        {
            LOG.warn(SEPARATOR);
            LOG.warn(SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Skipping project " + project.getName() + " [" + project.getKey()
                    + "], since no Sonargraph report is found.");
            LOG.warn(SEPARATOR);
            return false;
        }
        return true;
    }

    @Override
    public void analyse(final Project project, final SensorContext sensorContext)
    {
        LOG.info(SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Execute for module " + project.getName() + " [" + project.getKey()
                + "]");

        controller = new ControllerFactory().createController();
        final Optional<File> reportFileOptional = determineReportFile(fileSystem, settings);
        if (!reportFileOptional.isPresent())
        {
            LOG.error(SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Failed to read Sonargraph report!");
            loadReportResult = new OperationResult("Loading Sonargraph report");
            loadReportResult.addError(IOMessageCause.FILE_NOT_FOUND, "No Sonargraph report found!");
            return;
        }

        final File reportFile = reportFileOptional.get();
        LOG.info(SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Reading Sonargraph metrics report from: "
                + reportFile.getAbsolutePath());
        loadReportResult = loadReport(project, reportFile, settings);
        if (loadReportResult.isFailure())
        {
            return;
        }

        final ISoftwareSystem system = controller.getSoftwareSystem();
        if (system.getModules().size() == 0)
        {
            LOG.warn(SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME
                    + ": No modules defined for Sonargraph system, please check the workspace definition!");
            return;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        final Map<String, Metric<? extends Serializable>> metrics = metricFinder.findAll().stream()
                .filter(m -> m.key().startsWith(SonargraphPluginBase.ABBREVIATION))
                .collect(Collectors.toMap((final Metric m) -> m.key(), (final Metric m) -> m));

        processFeatures(sensorContext);
        if (project.isRoot())
        {
            processSystemMetrics(metrics, sensorContext);
        }
        processModule(metrics, project, sensorContext, system);

        LOG.info("{}: Finished processing of {} [{}]", SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, project.getName(), project.getKey());
    }

    private void processFeatures(final SensorContext sensorContext)
    {
        final ISystemInfoProcessor systemInfoProcessor = controller.createSystemInfoProcessor();
        for (final IFeature next : systemInfoProcessor.getFeatures())
        {
            if (next.getName().equals(IFeature.ARCHITECTURE) && next.isLicensed())
            {
                sensorContext.saveMeasure(new Measure<Boolean>(SonargraphMetrics.ARCHITECTURE_FEATURE_AVAILABLE, 1.0));
            }

            if (next.getName().equals(IFeature.VIRTUAL_MODELS) && next.isLicensed())
            {
                sensorContext.saveMeasure(new Measure<Boolean>(SonargraphMetrics.VIRTUAL_MODEL_FEATURE_AVAILABLE, 1.0));
            }
        }
    }

    private OperationResult loadReport(final Project project, final File reportFile, final Settings settings)
    {
        final OperationResult result = new OperationResult("Reading Sonargraph report from: " + reportFile.getAbsolutePath());
        final Optional<File> baseDirectory = determineBaseDirectory(fileSystem, settings);
        if (baseDirectory.isPresent())
        {
            LOG.info("Changing Sonargraph baseDirectory to: " + baseDirectory.get().getAbsolutePath());
            result.addMessagesFrom(controller.loadSystemReport(reportFile, baseDirectory.get()));
        }
        else
        {
            result.addMessagesFrom(controller.loadSystemReport(reportFile));
        }
        if (result.isFailure())
        {
            LOG.error("Failed to execute Sonargraph plugin for " + project.getName() + " [" + project.getKey() + "]");
            LOG.error(result.toString());
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
        final OptionalDouble highestNccd = nccdValues.values().stream().mapToDouble((final IMetricValue v) -> v.getValue().doubleValue()).max();
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
            LOG.info(SonargraphPluginBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": No module found in report for " + project.getName() + " ["
                    + project.getKey() + "]");
            return;
        }

        final IModule module = moduleOptional.get();
        processModuleMetrics(metrics, project, sensorContext, module);
    }

    private void processModuleMetrics(final Map<String, Metric<?>> metrics, final Project project, final SensorContext sensorContext,
            final IModule module)
    {
        final IModuleInfoProcessor moduleInfoProcessor = controller.createModuleInfoProcessor(module);
        final Optional<IMetricLevel> optionalMetricLevel = moduleInfoProcessor.getMetricLevels().stream()
                .filter((final IMetricLevel level) -> level.getName().equals(IMetricLevel.MODULE)).findAny();

        if (project.isModule())
        {
            processProjectMetrics(sensorContext, module, moduleInfoProcessor, metrics, optionalMetricLevel.get());
        }

        processIssues(moduleInfoProcessor, (final IIssue issue) -> !issue.hasResolution());
        processIssues(
                moduleInfoProcessor,
                (final IIssue issue) -> issue.hasResolution()
                        && issue.getIssueType().getCategory().getName().equals(IIssueCategory.StandardName.TODO.getStandardName()));
        processIssues(
                moduleInfoProcessor,
                (final IIssue issue) -> issue.hasResolution()
                        && issue.getIssueType().getCategory().getName().equals(IIssueCategory.StandardName.REFACTORING.getStandardName()));
    }

    private void processProjectMetrics(final SensorContext sensorContext, final IElementContainer container, final IInfoProcessor infoProcessor,
            final Map<String, Metric<?>> metrics, final IMetricLevel level)
    {
        for (final IMetricId next : infoProcessor.getMetricIdsForLevel(level))
        {
            final String metricKey = SonargraphMetrics.createMetricKeyFromStandardName(next.getName());
            if (!isConfiguredMetric(metrics, next))
            {
                LOG.warn("Metric id '" + next.getName() + "' has not been configured");
                continue;
            }

            final Optional<IMetricValue> value = infoProcessor.getMetricValueForElement(next, level, container.getFqName());
            if (value.isPresent())
            {
                LOG.debug("Processing metric id: " + next.getName() + ", metricKey: " + metricKey);
                final Measure<Double> measure = new Measure<>(metricKey);
                measure.setValue(value.get().getValue().doubleValue());
                sensorContext.saveMeasure(measure);
            }
            else
            {
                LOG.error("No value found for metric '" + next.getPresentationName() + "'. Please check the meta-data configuration for Sonargraph!");
            }
        }

        sensorContext.saveMeasure(new Measure<String>(SonargraphMetrics.CURRENT_VIRTUAL_MODEL, controller.getSoftwareSystem().getVirtualModel()));

        calculateStructuralCost(sensorContext, infoProcessor);
        calculateMetricsForStructuralDebtWidget(sensorContext, infoProcessor);
        calculateMetricsForArchitectureWidget(metrics, sensorContext, level, infoProcessor);
        calculateMetricsForStructureWidget(sensorContext, level, infoProcessor, container);
    }

    private void calculateStructuralCost(final SensorContext sensorContext, final IInfoProcessor infoProcessor)
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
                sensorContext.saveMeasure(new Measure<Double>(SonargraphMetrics.STRUCTURAL_DEBT_COST, cost));
            }
        }
    }

    private void processIssues(final IModuleInfoProcessor infoProcessor, final Predicate<IIssue> issueFilter)
    {
        final Map<ISourceFile, List<IIssue>> issueMap = infoProcessor.getIssuesForSourceFiles(issueFilter);
        final Map<String, ActiveRule> categoryToRuleMap = new HashMap<>();
        final List<String> categories = issueMap.values().stream().flatMap((final List<IIssue> issues) -> issues.stream())
                .map((final IIssue issue) -> issue.getIssueType().getCategory().getName()).distinct().collect(Collectors.toList());

        for (final String category : categories)
        {
            final ActiveRule rule = profile.getActiveRule(SonargraphPluginBase.PLUGIN_KEY, SonargraphMetrics.createRuleKey(category));
            if (rule == null)
            {
                LOG.info("Rule '" + SonargraphMetrics.createRuleKey(category) + "' is not activated.");
                continue;
            }
            categoryToRuleMap.put(category, rule);
        }

        for (final Entry<ISourceFile, List<IIssue>> issuesPerSourceFile : issueMap.entrySet())
        {
            addIssuesToSourceFile(categoryToRuleMap, infoProcessor.getBaseDirectory(), issuesPerSourceFile.getKey(), issuesPerSourceFile.getValue());
        }
    }

    private void addIssuesToSourceFile(final Map<String, ActiveRule> categoryToRuleMap, final String baseDir, final ISourceFile sourceFile,
            final List<IIssue> issues)
    {
        assert sourceFile != null : "Parameter 'sourceFile' of method 'addIssuesToSourceFile' must not be null";
        final String rootDirectoryRelPath = sourceFile.getRelativeRootDirectoryPath();
        final String sourceRelPath = sourceFile.getRelativePath();

        final String sourceFileLocation = Paths.get(baseDir, rootDirectoryRelPath, sourceRelPath).normalize().toString();
        final Optional<InputPath> resource = Utilities.getResource(fileSystem, sourceFileLocation);
        if (!resource.isPresent())
        {
            LOG.error("Failed to locate resource '" + sourceFile.getFqName() + "' at '" + sourceFileLocation + "'");
            return;
        }

        for (final IIssue issue : issues)
        {
            final String categoryName = issue.getIssueType().getCategory().getName();
            final ActiveRule rule = categoryToRuleMap.get(categoryName);
            if (rule == null)
            {
                LOG.debug("Ignoring issue type '{}', because corresponding rule is not activated in current quality profile", issue.getIssueType()
                        .getPresentationName());
                continue;
            }

            if (issue instanceof IDuplicateCodeBlockIssue)
            {
                handleDuplicateCodeBlock((IDuplicateCodeBlockIssue) issue, sourceFile, resource.get(), rule);
            }
            else
            {
                final Issuable issuable = perspectives.as(Issuable.class, resource.get());
                if (issuable == null)
                {
                    LOG.error("Failed to create Issuable for resource '" + resource.get().absolutePath());
                    continue;
                }
                final IssueBuilder issueBuilder = issuable.newIssueBuilder();
                issueBuilder.ruleKey(rule.getRule().ruleKey());
                if (rule.getSeverity() != null)
                {
                    issueBuilder.severity(rule.getSeverity().toString());
                }

                final String msg = issue.getIssueType().getCategory().getPresentationName() + ": " + issue.getDescription() + " ["
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
        }
    }

    private void handleDuplicateCodeBlock(final IDuplicateCodeBlockIssue issue, final ISourceFile sourceFile, final InputPath resource,
            final ActiveRule rule)
    {
        for (final IDuplicateCodeBlockOccurrence occurrence : issue.getOccurrences())
        {
            //We need to handle duplicate issues with blocks within the same source file
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
            LOG.error("Failed to create issuable for resource '" + resource.absolutePath() + "'");
            return;
        }
        final IssueBuilder issueBuilder = issuable.newIssueBuilder();
        issueBuilder.ruleKey(rule.getRule().ruleKey());
        if (rule.getSeverity() != null)
        {
            issueBuilder.severity(rule.getSeverity().toString());
        }

        final StringBuilder msg = new StringBuilder();
        msg.append(issue.getIssueType().getCategory().getPresentationName()).append(" [").append(issue.getPresentationName()).append("]")
                .append(": ").append(issue.getDescription()).append(": ");
        msg.append("\nLine ").append(occurrence.getStartLine()).append(" to ").append(occurrence.getStartLine() + occurrence.getBlockSize() - 1)
                .append(" is a duplicate of");
        for (final IDuplicateCodeBlockOccurrence next : others)
        {
            msg.append("\n");
            msg.append(next.getSourceFile().getRelativePath()).append(", line ").append(next.getStartLine()).append(" to ")
                    .append(next.getStartLine() + next.getBlockSize() - 1);
        }

        issueBuilder.message(msg.toString());
        issueBuilder.line(occurrence.getStartLine());
        final Issue sqIssue = issueBuilder.build();
        issuable.addIssue(sqIssue);
    }

    private static void calculateMetricsForStructureWidget(final SensorContext sensorContext, final IMetricLevel level,
            final IInfoProcessor infoProcessor, final IElementContainer container)
    {
        final String packagesMetricId = IJavaMetricId.StandardName.JAVA_PACKAGES.getStandardName();
        final Optional<IMetricId> packagesMetric = infoProcessor.getMetricId(level, packagesMetricId);
        final String cyclicPackagesMetricId = IJavaMetricId.StandardName.JAVA_CYCLIC_PACKAGES.getStandardName();
        final Optional<IMetricId> cyclicPackagesMetric = infoProcessor.getMetricId(level, cyclicPackagesMetricId);

        LOG.debug("Adding cyclic packages metric");
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
            sensorContext.saveMeasure(new Measure<Integer>(SonargraphMetrics.CYCLIC_PACKAGES_PERCENT, cylicPackagesPercent));
        }
    }

    private void calculateMetricsForArchitectureWidget(final Map<String, Metric<?>> metrics, final SensorContext sensorContext,
            final IMetricLevel level, final IInfoProcessor infoProcessor)
    {
        assert metrics != null : "Parameter 'metrics' of method 'calculateMetricsForArchitectureWidget' must not be null";
        assert sensorContext != null : "Parameter 'sensorContext' of method 'calculateMetricsForArchitectureWidget' must not be null";
        assert level != null : "Parameter 'level' of method 'calculateMetricsForArchitectureWidget' must not be null";
        assert infoProcessor != null : "Parameter 'infoProcessor' of method 'calculateMetricsForArchitectureWidget' must not be null";

        final double numberOfIssues = infoProcessor.getIssues(null).size();
        sensorContext.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_ISSUES, numberOfIssues));

        final double numberOfUnresolvedCriticalIssues = infoProcessor.getIssues(
                (final IIssue i) -> !i.hasResolution()
                        && (i.getIssueType().getSeverity() == Severity.WARNING || i.getIssueType().getSeverity() == Severity.ERROR)).size();
        sensorContext.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_CRITICAL_ISSUES_WITHOUT_RESOLUTION,
                numberOfUnresolvedCriticalIssues));

        final double numberOfUnresolvedThresholdViolations = infoProcessor.getIssues(
                (final IIssue issue) -> !issue.hasResolution()
                        && IIssueCategory.StandardName.THRESHOLD_VIOLATION.getStandardName().equals(issue.getIssueType().getCategory().getName()))
                .size();
        sensorContext.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_THRESHOLD_VIOLATIONS, numberOfUnresolvedThresholdViolations));

        final double numberOfIgnoredCriticalIssues = infoProcessor.getResolutions(
                (final IResolution r) -> r.getType() == ResolutionType.IGNORE
                        && r.getIssues()
                                .stream()
                                .anyMatch(
                                        (final IIssue issue) -> issue.getIssueType().getSeverity() == Severity.WARNING
                                                || issue.getIssueType().getSeverity() == Severity.ERROR)).size();
        sensorContext.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_IGNORED_CRITICAL_ISSUES, numberOfIgnoredCriticalIssues));

        if (IMetricLevel.SYSTEM.equals(level.getName()))
        {

            final double numberOfWorkspaceWarnings = infoProcessor.getIssues(
                    (final IIssue issue) -> !issue.hasResolution()
                            && IIssueCategory.StandardName.WORKSPACE.getStandardName().equals(issue.getIssueType().getCategory().getName())).size();
            sensorContext.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_WORKSPACE_WARNINGS, numberOfWorkspaceWarnings));
        }

        final Optional<Metric<?>> numberOfComponentsMetric = getSonarQubeMetric(metrics, IMetricId.StandardName.CORE_COMPONENTS.getStandardName());
        if (numberOfComponentsMetric.isPresent())
        {
            calculateArchitecturePercentages(metrics, sensorContext, infoProcessor);
        }
    }

    private static void calculateArchitecturePercentages(final Map<String, Metric<?>> metrics, final SensorContext sensorContext,
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
            sensorContext.saveMeasure(new Measure<Integer>(SonargraphMetrics.UNASSIGNED_COMPONENTS_PERCENT, unassignedComponentsPercent));
        }

        final Optional<Metric<?>> numberOfViolatingComponentsMetric = getSonarQubeMetric(metrics,
                IMetricId.StandardName.CORE_VIOLATING_COMPONENTS.getStandardName());
        final Optional<IMetricValue> violatingComponentsValue = infoProcessor.getMetricValue(IMetricId.StandardName.CORE_VIOLATING_COMPONENTS
                .getStandardName());
        if (numberOfViolatingComponentsMetric.isPresent() && violatingComponentsValue.isPresent())
        {
            final double numberOfViolatingComponents = violatingComponentsValue.get().getValue().doubleValue();
            final double violatingComponentsPercent = NumberUtility.round((numberOfViolatingComponents / numberOfComponents) * 100.0, 2);
            sensorContext.saveMeasure(new Measure<Integer>(SonargraphMetrics.VIOLATING_COMPONENTS_PERCENT, violatingComponentsPercent));
        }
    }

    private void calculateMetricsForStructuralDebtWidget(final SensorContext sensorContext, final IInfoProcessor infoProcessor)
    {
        final double numberOfResolutions = infoProcessor.getResolutions(null).size();
        sensorContext.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_RESOLUTIONS, numberOfResolutions));

        final double numberOfUnapplicableResolutions = infoProcessor.getResolutions((final IResolution r) -> !r.isApplicable()).size();
        sensorContext.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_UNAPPLICABLE_RESOLUTIONS, numberOfUnapplicableResolutions));

        final double numberOfTasks = infoProcessor.getResolutions((final IResolution r) -> r.isTask()).size();
        sensorContext.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_TASKS, numberOfTasks));

        final double numberOfUnapplicableTasks = infoProcessor.getResolutions((final IResolution r) -> r.isTask() && !r.isApplicable()).size();
        sensorContext.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_UNAPPLICABLE_TASKS, numberOfUnapplicableTasks));

        final List<IResolution> refactorings = infoProcessor.getResolutions((final IResolution r) -> r.getType() == ResolutionType.REFACTORING);
        final double numberOfRefactorings = refactorings.size();
        sensorContext.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_REFACTORINGS, numberOfRefactorings));
        final List<IResolution> applicableRefactorings = refactorings.stream().filter((final IResolution r) -> r.isApplicable())
                .collect(Collectors.toList());

        final double numberOfUnapplicableRefactorings = numberOfRefactorings - applicableRefactorings.size();
        sensorContext.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_UNAPPLICABLE_REFACTORINGS, numberOfUnapplicableRefactorings));

        final double numberOfAffectedParserDepencencies = applicableRefactorings.stream()
                .mapToInt(IResolution::getNumberOfAffectedParserDependencies).sum();
        LOG.debug("Detected " + numberOfAffectedParserDepencencies + " parser dependencies affected by refactorings");
        sensorContext.saveMeasure(new Measure<Integer>(SonargraphMetrics.NUMBER_OF_PARSER_DEPENDENCIES_AFFECTED_BY_REFACTORINGS,
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
            LOG.debug("Load report from " + reportFile.getAbsolutePath());
            return Optional.of(reportFile);
        }
        return Optional.empty();
    }

    private static boolean fileExistsAndIsReadable(final File reportFile)
    {
        return reportFile.exists() && reportFile.canRead();
    }

    private static Optional<File> determineBaseDirectory(final FileSystem fileSystem, final Settings settings)
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
}
