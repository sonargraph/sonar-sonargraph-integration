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
import java.util.TreeMap;
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
import com.hello2morrow.sonargraph.integration.access.foundation.Utility;
import com.hello2morrow.sonargraph.integration.access.model.IDuplicateCodeBlockIssue;
import com.hello2morrow.sonargraph.integration.access.model.IDuplicateCodeBlockOccurrence;
import com.hello2morrow.sonargraph.integration.access.model.IIssue;
import com.hello2morrow.sonargraph.integration.access.model.IJavaMetricId;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;
import com.hello2morrow.sonargraph.integration.access.model.IMetricLevel;
import com.hello2morrow.sonargraph.integration.access.model.IMetricValue;
import com.hello2morrow.sonargraph.integration.access.model.IModule;
import com.hello2morrow.sonargraph.integration.access.model.INamedElement;
import com.hello2morrow.sonargraph.integration.access.model.INamedElementContainer;
import com.hello2morrow.sonargraph.integration.access.model.IResolution;
import com.hello2morrow.sonargraph.integration.access.model.IRootDirectory;
import com.hello2morrow.sonargraph.integration.access.model.ISoftwareSystem;
import com.hello2morrow.sonargraph.integration.access.model.ISourceFile;
import com.hello2morrow.sonargraph.integration.access.model.ResolutionType;

public final class SonargraphSensor implements Sensor
{
    private static final Logger LOGGER = Loggers.get(SonargraphSensor.class);
    private static final String SONARGRAPH_TARGET_DIR = "sonargraph";
    private static final String SONARGRAPH_SONARQUBE_REPORT_FILENAME = "sonargraph-sonarqube-report.xml";
    private static final String WORKSPACE_ID = SonargraphBase.WORKSPACE + ":";

    private final RulesProfile qualityProfile;
    private final FileSystem fileSystem;
    private final MetricFinder metricFinder;
    private Map<String, Metric<? extends Serializable>> metrics;

    public SonargraphSensor(final RulesProfile qualityProfile, final FileSystem fileSystem, final MetricFinder metricFinder)
    {
        assert qualityProfile != null : "Parameter 'profile' of method 'SonargraphSensor' must not be null";
        assert fileSystem != null : "Parameter 'fileSystem' of method 'SonargraphSensor' must not be null";
        assert metricFinder != null : "Parameter 'metricFinder' of method 'SonargraphSensor' must not be null";

        this.qualityProfile = qualityProfile;
        this.fileSystem = fileSystem;
        this.metricFinder = metricFinder;
    }

    //    private void processFeatures(final SensorContext sensorContext, final ISystemInfoProcessor systemInfoProcessor)
    //    {
    //        assert sensorContext != null : "Parameter 'sensorContext' of method 'processFeatures' must not be null";
    //        assert systemInfoProcessor != null : "Parameter 'systemInfoProcessor' of method 'processFeatures' must not be null";
    //
    //        for (final IFeature feature : systemInfoProcessor.getFeatures())
    //        {
    //            //            if (feature.getName().equals(IFeature.ARCHITECTURE) && feature.isLicensed())
    //            //            {
    //            //                sensorContext.saveMeasure(new Measure<Boolean>(SonargraphMetrics.ARCHITECTURE_FEATURE_AVAILABLE, 1.0));
    //            //            }
    //            //
    //            //            if (feature.getName().equals(IFeature.VIRTUAL_MODELS) && feature.isLicensed())
    //            //            {
    //            //                sensorContext.saveMeasure(new Measure<Boolean>(SonargraphMetrics.VIRTUAL_MODEL_FEATURE_AVAILABLE, 1.0));
    //            //            }
    //        }
    //    }

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
                builder.append("[").append(SonargraphBase.toLowerCase(type.toString(), false)).append(": ").append(issue.getPresentationName())
                        .append("]");
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
            builder.append(" priority='").append(SonargraphBase.toLowerCase(resolution.getPriority().toString(), false)).append("'");
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
        final String sourceRelPath = sourceFile.getRelativePath();
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
            final ActiveRule nextRule = issueTypeToRuleMap.get(SonargraphBase.createRuleKey(nextIssue.getIssueType().getName()));
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
            final ActiveRule nextRule = issueTypeToRuleMap.get(SonargraphBase.createRuleKey(nextIssue.getIssueType().getName()));
            if (nextRule == null)
            {
                LOGGER.debug("Ignoring issue type '{}', because corresponding rule is not activated in current quality profile",
                        nextIssue.getIssueType().getPresentationName());
                continue;
            }
            createIssue(context, inputDir, nextRule, nextIssue.getLine(), create(moduleInfoProcessor, nextIssue));
        }
    }

    private void processModule(final IModuleInfoProcessor moduleInfoProcessor, final IModule module, final Map<String, Metric<?>> metrics,
            final Map<String, ActiveRule> ruleKeyToActiveRule, final SensorContext context)
    {
        assert moduleInfoProcessor != null : "Parameter 'moduleInfoProcessor' of method 'processModule' must not be null";
        assert module != null : "Parameter 'module' of method 'processModule' must not be null";
        assert context != null : "Parameter 'context' of method 'processModule' must not be null";
        assert metrics != null : "Parameter 'metrics' of method 'processModule' must not be null";
        assert ruleKeyToActiveRule != null : "Parameter 'ruleKeyToActiveRule' of method 'processModule' must not be null";

        final Optional<IMetricLevel> optionalMetricLevel = moduleInfoProcessor.getMetricLevels().stream()
                .filter(level -> level.getName().equals(IMetricLevel.MODULE)).findAny();

        if (optionalMetricLevel.isPresent())
        {
            processProjectMetrics(context, module, moduleInfoProcessor, metrics, optionalMetricLevel.get());
        }

        final Map<ISourceFile, List<IIssue>> sourceFileIssueMap = moduleInfoProcessor
                .getIssuesForSourceFiles(issue -> !issue.isIgnored() && !SonargraphBase.ignoreIssueType(issue.getIssueType()));
        for (final Entry<ISourceFile, List<IIssue>> issuesPerSourceFile : sourceFileIssueMap.entrySet())
        {
            addIssuesToSourceFile(context, moduleInfoProcessor, ruleKeyToActiveRule, moduleInfoProcessor.getBaseDirectory(),
                    issuesPerSourceFile.getKey(), issuesPerSourceFile.getValue());
        }

        final Map<String, List<IIssue>> directoryIssueMap = moduleInfoProcessor
                .getIssuesForDirectories(issue -> !issue.isIgnored() && !SonargraphBase.ignoreIssueType(issue.getIssueType()));
        for (final Entry<String, List<IIssue>> issuesPerDirectory : directoryIssueMap.entrySet())
        {
            addIssuesToDirectory(context, moduleInfoProcessor, ruleKeyToActiveRule, moduleInfoProcessor.getBaseDirectory(),
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
        assert context != null : "Parameter 'context' of method 'calculateStructuralCost' must not be null";
        final Optional<Float> indexCost = context.config().getFloat(SonargraphBase.COST_PER_INDEX_POINT);
        if (!indexCost.isPresent())
        {
            return;
        }

        final Optional<IMetricValue> value = infoProcessor
                .getMetricValue(IJavaMetricId.StandardName.JAVA_STRUCTURAL_DEBT_INDEX_PACKAGES.getStandardName());
        if (value.isPresent())
        {
            final double cost = (double) indexCost.get() * value.get().getValue().intValue();
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
        final String metricKey = SonargraphBase.createMetricKeyFromStandardName(metricId.getName());
        return configuredMetrics.containsKey(metricKey);
    }

    private static Optional<Metric<?>> getSonarQubeMetric(final Map<String, Metric<?>> metrics, final String name)
    {
        final String metricKey = SonargraphBase.createMetricKeyFromStandardName(name);
        return Optional.ofNullable(metrics.get(metricKey));
    }

    @Override
    public void describe(final SensorDescriptor descriptor)
    {
        assert descriptor != null : "Parameter 'descriptor' of method 'describe' must not be null";
        descriptor.name(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
        descriptor.global();
        //        descriptor.createIssuesForRuleRepository(repositoryKey)
    }

    private IModule matchModule(final InputModule inputModule, final ISoftwareSystem softwareSystem)
    {
        assert inputModule != null : "Parameter 'inputModule' of method 'matchModule' must not be null";
        assert softwareSystem != null : "Parameter 'softwareSystem' of method 'matchModule' must not be null";

        final Map<String, IModule> modules = softwareSystem.getModules();

        final TreeMap<Integer, List<IModule>> matchedRootDirsToModules = new TreeMap<>();
        for (final IModule nextModule : modules.values())
        {
            int matchedRootDirs = 0;
            final List<IRootDirectory> nextRootDirectories = nextModule.getRootDirectories();
            if (nextRootDirectories.isEmpty())
            {
                continue;
            }
            for (final IRootDirectory nextRootDirectory : nextRootDirectories)
            {
                final String nextRelPath = nextRootDirectory.getRelativePath();
                final File nextResolved = fileSystem.resolvePath(nextRelPath);
                if (nextResolved != null && nextResolved.exists())
                {
                    matchedRootDirs++;
                }
            }
            if (matchedRootDirs == 0)
            {
                continue;
            }

            final Integer nextMatchedRootDirsAsInteger = Integer.valueOf(matchedRootDirs);
            List<IModule> nextMatched = matchedRootDirsToModules.get(nextMatchedRootDirsAsInteger);
            if (nextMatched == null)
            {
                nextMatched = new ArrayList<>(2);
                matchedRootDirsToModules.put(nextMatchedRootDirsAsInteger, nextMatched);
            }
            nextMatched.add(nextModule);
        }

        if (!matchedRootDirsToModules.isEmpty())
        {
            final List<IModule> matchedModules = matchedRootDirsToModules.lastEntry().getValue();
            if (matchedModules.size() == 1)
            {
                return matchedModules.get(0);
            }

            IModule matched = null;
            for (final IModule nextMatchedModule : matchedModules)
            {
                final String nextModuleFqName = nextMatchedModule.getFqName();
                if (nextModuleFqName == null || nextModuleFqName.isEmpty() || !nextModuleFqName.startsWith(WORKSPACE_ID))
                {
                    LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Ignoring invalid module fq name coming from report '"
                            + nextModuleFqName + "'");
                    continue;
                }

                final String nextModuleName = nextModuleFqName.substring(WORKSPACE_ID.length(), nextModuleFqName.length());
                if (inputModule.key().indexOf(nextModuleName) != -1)
                {
                    if (matched == null)
                    {
                        matched = nextMatchedModule;
                    }
                    else
                    {
                        return null;
                    }
                }
            }
            return matched;
        }

        return null;
    }

    private File findReportFile(final SensorContext context)
    {
        assert context != null : "Parameter 'context' of method 'findReportFile' must not be null";

        //Try configured path
        final Optional<String> reportPath = context.config().get(SonargraphBase.REPORT_PATH);
        if (reportPath.isPresent())
        {
            final String configuredReportFilePath = reportPath.get();
            LOGGER.info(
                    SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Using configured report file path '" + configuredReportFilePath + "'");
            final File file = fileSystem.resolvePath(configuredReportFilePath);
            if (file.exists())
            {
                return file;
            }

            LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": No report file found at '" + file.getAbsolutePath() + "'");
            return null;
        }

        //Try Maven path
        File file = Paths.get(fileSystem.workDir().getParentFile().getAbsolutePath(), SONARGRAPH_TARGET_DIR, SONARGRAPH_SONARQUBE_REPORT_FILENAME)
                .toFile();
        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Trying Maven report file path '" + file.getAbsolutePath() + "'");
        if (file.exists())
        {
            return file;
        }

        //Try Gradle path
        file = Paths.get(fileSystem.workDir().getParentFile().getParentFile().getAbsolutePath(), SONARGRAPH_TARGET_DIR,
                SONARGRAPH_SONARQUBE_REPORT_FILENAME).toFile();
        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Trying Gradle report file path '" + file.getAbsolutePath() + "'");
        if (file.exists())
        {
            return file;
        }

        LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": No report file found");
        return null;
    }

    @Override
    public void execute(final SensorContext context)
    {
        assert context != null : "Parameter 'context' of method 'execute' must not be null";

        final InputModule inputModule = context.module();
        final String inputModuleKey = inputModule.key();

        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Processing module '" + inputModuleKey + "'");

        final File reportFile = findReportFile(context);
        if (reportFile == null)
        {
            return;
        }

        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Reading report from '" + reportFile.getAbsolutePath() + "'");

        final ISonargraphSystemController controller = ControllerAccess.createController();
        final Result result = controller.loadSystemReport(reportFile);
        if (result.isFailure())
        {
            LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + result.toString());
            return;
        }

        final ISoftwareSystem softwareSystem = controller.getSoftwareSystem();
        if (softwareSystem.getModules().isEmpty())
        {
            LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": No modules defined in Sonargraph system");
            return;
        }

        final IModule module = matchModule(inputModule, softwareSystem);
        if (module == null)
        {
            LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": No module match found in report for '" + inputModuleKey + "'");
            return;
        }

        if (metrics == null)
        {
            metrics = metricFinder.findAll().stream().filter(m -> m.key().startsWith(SonargraphBase.ABBREVIATION))
                    .collect(Collectors.toMap(Metric::key, m -> m));
        }

        final ISystemInfoProcessor systemInfoProcessor = controller.createSystemInfoProcessor();
        //        processFeatures(context, systemInfoProcessor);
        //        if (project.isRoot())
        //        {
        //TODO
        //        processSystemMetrics(metrics, context, systemInfoProcessor, softwareSystem);
        //        }

        final Map<String, ActiveRule> ruleKeyToActiveRule = new HashMap<>();
        for (final ActiveRule nextActiveRule : qualityProfile.getActiveRulesByRepository(SonargraphBase.SONARGRAPH_PLUGIN_KEY))
        {
            ruleKeyToActiveRule.put(nextActiveRule.getRuleKey(), nextActiveRule);
        }

        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + ruleKeyToActiveRule + " rule(s) activated");

        //        processModule(controller.createModuleInfoProcessor(module), module, metrics, ruleKeyToActiveRule, context);

        final List<IIssue> workspaceIssues = systemInfoProcessor
                .getIssues(issue -> !issue.hasResolution() && SonargraphBase.isWorkspoceIssue(issue.getIssueType()));
        if (!workspaceIssues.isEmpty())
        {
            LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Found " + workspaceIssues.size() + " workspace issue(s)");
            int i = 1;
            for (final IIssue nextIssue : workspaceIssues)
            {
                LOGGER.warn("[" + i + "] " + nextIssue.getPresentationName());
                for (final INamedElement nextAffected : nextIssue.getAffectedNamedElements())
                {
                    LOGGER.warn(" - " + nextAffected.getName() + " [" + nextAffected.getPresentationKind() + "]");
                }
                i++;
            }
        }

        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Finished processing module '" + inputModuleKey + "'");
    }
}