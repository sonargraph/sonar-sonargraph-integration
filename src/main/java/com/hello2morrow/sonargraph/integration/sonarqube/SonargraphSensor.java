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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputComponent;
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
import org.sonar.api.batch.sensor.measure.NewMeasure;
import org.sonar.api.config.Configuration;
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
import com.hello2morrow.sonargraph.integration.access.model.IIssueType;
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
    private static final String WORKSPACE_ID = SonargraphBase.WORKSPACE + ":";

    static final class ProcessingData
    {
        private final Set<String> createdMeasures = new LinkedHashSet<>();
        private final Set<String> createdIssues = new LinkedHashSet<>();
        private final Map<String, ActiveRule> activeRules;
        private final Map<String, Metric<Serializable>> metrics;

        ProcessingData(final Map<String, ActiveRule> activeRules, final Map<String, Metric<Serializable>> metrics)
        {
            assert activeRules != null : "Parameter 'activeRules' of method 'ProcessingData' must not be null";
            assert metrics != null : "Parameter 'metrics' of method 'ProcessingData' must not be null";
            this.activeRules = activeRules;
            this.metrics = metrics;
        }

        boolean issueAlreadyCreated(final String ruleKey)
        {
            assert ruleKey != null && ruleKey.length() > 0 : "Parameter 'ruleKey' of method 'issueAlreadyCreated' must not be empty";
            return createdIssues.contains(ruleKey);
        }

        void addCreatedIssue(final String ruleKey)
        {
            assert ruleKey != null && ruleKey.length() > 0 : "Parameter 'ruleKey' of method 'addCreatedIssue' must not be empty";
            createdIssues.add(ruleKey);
        }

        boolean measureAlreadyCreated(final String metricKey)
        {
            assert metricKey != null && metricKey.length() > 0 : "Parameter 'metricKey' of method 'measureAlreadyCreated' must not be empty";
            return createdMeasures.contains(metricKey);
        }

        void addCreatedMeasure(final String metricKey)
        {
            assert metricKey != null && metricKey.length() > 0 : "Parameter 'metricKey' of method 'addCreatedMeasure' must not be empty";
            createdMeasures.add(metricKey);
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

    private final RulesProfile qualityProfile;
    private final FileSystem fileSystem;
    private final MetricFinder metricFinder;
    private Properties customMetrics;

    public SonargraphSensor(final FileSystem fileSystem, final RulesProfile qualityProfile, final MetricFinder metricFinder)
    {
        assert fileSystem != null : "Parameter 'fileSystem' of method 'SonargraphSensor' must not be null";
        assert qualityProfile != null : "Parameter 'profile' of method 'SonargraphSensor' must not be null";
        assert metricFinder != null : "Parameter 'metricFinder' of method 'SonargraphSensor' must not be null";

        this.fileSystem = fileSystem;
        this.qualityProfile = qualityProfile;
        this.metricFinder = metricFinder;

        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Sensor created");
    }

    private String toLowerCase(String input, final boolean firstLower)
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

    private String createIssueDescription(final IInfoProcessor infoProcessor, final IIssue issue, final String detail)
    {
        assert infoProcessor != null : "Parameter 'infoProcessor' of method 'createIssueDescription' must not be null";
        assert issue != null : "Parameter 'issue' of method 'createIssueDescription' must not be null";
        assert detail != null : "Parameter 'detail' of method 'createIssueDescription' must not be null";

        final StringBuilder builder = new StringBuilder();

        final IResolution resolution = infoProcessor.getResolution(issue);
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

    private String createIssueDescription(final IModuleInfoProcessor moduleInfoProcessor, final IDuplicateCodeBlockIssue issue,
            final IDuplicateCodeBlockOccurrence occurrence, final List<IDuplicateCodeBlockOccurrence> others)
    {
        assert moduleInfoProcessor != null : "Parameter 'moduleInfoProcessor' of method 'createIssueDescription' must not be null";
        assert issue != null : "Parameter 'issue' of method 'createIssueDescription' must not be null";
        assert occurrence != null : "Parameter 'occurrence' of method 'createIssueDescription' must not be null";
        assert others != null : "Parameter 'others' of method 'createIssueDescription' must not be null";

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

        return createIssueDescription(moduleInfoProcessor, issue, detail.toString());
    }

    private String createIssueDescription(final IInfoProcessor infoProcessor, final IIssue issue)
    {
        assert infoProcessor != null : "Parameter 'infoProcessor' of method 'createIssueDescription' must not be null";
        assert issue != null : "Parameter 'issue' of method 'createIssueDescription' must not be null";
        return createIssueDescription(infoProcessor, issue, "");
    }

    private void createSourceFileIssues(final SensorContext context, final IModuleInfoProcessor moduleInfoProcessor, final ISourceFile sourceFile,
            final InputPath inputPath, final IIssue issue, final ActiveRule rule)
    {
        assert context != null : "Parameter 'context' of method 'createSourceFileIssues' must not be null";
        assert moduleInfoProcessor != null : "Parameter 'moduleInfoProcessor' of method 'createSourceFileIssues' must not be null";
        assert sourceFile != null : "Parameter 'sourceFile' of method 'createSourceFileIssues' must not be null";
        assert inputPath != null : "Parameter 'inputPath' of method 'createSourceFileIssues' must not be null";
        assert issue != null : "Parameter 'issue' of method 'createSourceFileIssues' must not be null";
        assert rule != null : "Parameter 'rule' of method 'createSourceFileIssues' must not be null";

        if (issue instanceof IDuplicateCodeBlockIssue)
        {
            final IDuplicateCodeBlockIssue nextDuplicateCodeBlockIssue = (IDuplicateCodeBlockIssue) issue;
            final List<IDuplicateCodeBlockOccurrence> nextOccurrences = nextDuplicateCodeBlockIssue.getOccurrences();

            for (final IDuplicateCodeBlockOccurrence nextOccurrence : nextOccurrences)
            {
                if (nextOccurrence.getSourceFile().equals(sourceFile))
                {
                    final List<IDuplicateCodeBlockOccurrence> others = new ArrayList<>(nextOccurrences);
                    others.remove(nextOccurrence);
                    createIssue(context, inputPath, rule,
                            createIssueDescription(moduleInfoProcessor, nextDuplicateCodeBlockIssue, nextOccurrence, others),
                            l -> l.at(new DefaultTextRange(new DefaultTextPointer(nextOccurrence.getStartLine(), 0),
                                    new DefaultTextPointer(nextOccurrence.getStartLine() + nextOccurrence.getBlockSize(), 1))));
                }
            }
        }
        else
        {
            createIssue(context, inputPath, rule, createIssueDescription(moduleInfoProcessor, issue), l ->
            {
                final int line = issue.getLine();
                final int lineToUse = line <= 0 ? 1 : line;
                l.at(new DefaultTextRange(new DefaultTextPointer(lineToUse, 0), new DefaultTextPointer(lineToUse, 1)));
            });
        }
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
        if (inputPath != null)
        {
            for (final IIssue nextIssue : issues)
            {
                final ActiveRule nextRule = issueTypeToRuleMap.get(SonargraphBase.createRuleKey(nextIssue.getIssueType().getName()));
                if (nextRule != null)
                {
                    createSourceFileIssues(context, moduleInfoProcessor, sourceFile, inputPath, nextIssue, nextRule);
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
            final Map<String, ActiveRule> issueTypeToRuleMap, final String baseDir, final String relDirectory, final List<IIssue> issues)
    {
        assert context != null : "Parameter 'context' of method 'addIssuesToDirectory' must not be null";
        assert moduleInfoProcessor != null : "Parameter 'moduleInfoProcessor' of method 'addIssuesToSourceFile' must not be null";
        assert issueTypeToRuleMap != null : "Parameter 'issueTypeToRuleMap' of method 'addIssuesToSourceFile' must not be null";
        assert relDirectory != null && relDirectory.length() > 0 : "Parameter 'relDirectory' of method 'addIssuesToDirectory' must not be empty";

        final String directoryLocation = Paths.get(baseDir, relDirectory).normalize().toString();
        final InputDir inputDir = fileSystem.inputDir(new File(Utility.convertPathToUniversalForm(directoryLocation)));

        if (inputDir != null)
        {
            for (final IIssue nextIssue : issues)
            {
                final ActiveRule nextRule = issueTypeToRuleMap.get(SonargraphBase.createRuleKey(nextIssue.getIssueType().getName()));
                if (nextRule != null)
                {
                    createIssue(context, inputDir, nextRule, createIssueDescription(moduleInfoProcessor, nextIssue), null);
                }
            }
        }
        else
        {
            LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Failed to locate directory resource: '" + directoryLocation
                    + "'\nBaseDir: " + baseDir + "\nrelDirectory:'" + relDirectory);
        }
    }

    private void processSystem(final SensorContext context, final InputComponent inputComponent, final ISoftwareSystem softwareSystem,
            final ISystemInfoProcessor systemInfoProcessor, final ProcessingData data)
    {
        assert context != null : "Parameter 'context' of method 'processSystem' must not be null";
        assert inputComponent != null : "Parameter 'inputComponent' of method 'processSystem' must not be null";
        assert softwareSystem != null : "Parameter 'softwareSystem' of method 'processSystem' must not be null";
        assert systemInfoProcessor != null : "Parameter 'systemInfoProcessor' of method 'processSystem' must not be null";
        assert data != null : "Parameter 'data' of method 'processSystem' must not be null";

        final Optional<IMetricLevel> systemLevelOptional = systemInfoProcessor.getMetricLevel(IMetricLevel.SYSTEM);
        if (systemLevelOptional.isPresent())
        {
            processMetrics(context, inputComponent, softwareSystem, softwareSystem, systemInfoProcessor, systemLevelOptional.get(), data);
        }

        final List<IIssue> systemIssues = systemInfoProcessor.getIssues(issue -> !issue.isIgnored()
                && !SonargraphBase.ignoreIssueType(issue.getIssueType()) && issue.getAffectedNamedElements().contains(softwareSystem));
        for (final IIssue nextIssue : systemIssues)
        {
            final IIssueType nextIssueType = nextIssue.getIssueType();
            final String nextRuleKey = SonargraphBase.isScriptIssue(nextIssueType) ? SonargraphBase.createRuleKey(SonargraphBase.SCRIPT_ISSUE_NAME)
                    : SonargraphBase.createRuleKey(nextIssueType.getName());
            final ActiveRule nextRule = data.getActiveRules().get(nextRuleKey);
            if (nextRule != null && !data.issueAlreadyCreated(SonargraphBase.createRuleKey(nextIssueType.getName())))
            {
                createIssue(context, inputComponent, nextRule, createIssueDescription(systemInfoProcessor, nextIssue), null);
            }
        }

        final List<IIssue> workspaceIssues = systemInfoProcessor
                .getIssues(issue -> SonargraphBase.isErrorOrWarningWorkspoceIssue(issue.getIssueType()));
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
    }

    private final void processModule(final SensorContext context, final InputComponent inputComponent, final ISoftwareSystem system,
            final IModule module, final IModuleInfoProcessor moduleInfoProcessor, final ProcessingData data)
    {
        assert context != null : "Parameter 'context' of method 'processModule' must not be null";
        assert inputComponent != null : "Parameter 'inputComponent' of method 'processModule' must not be null";
        assert system != null : "Parameter 'system' of method 'processModule' must not be null";
        assert module != null : "Parameter 'module' of method 'processModule' must not be null";
        assert moduleInfoProcessor != null : "Parameter 'moduleInfoProcessor' of method 'processModule' must not be null";
        assert data != null : "Parameter 'data' of method 'processModule' must not be null";

        final Optional<IMetricLevel> metricLevelOptional = moduleInfoProcessor.getMetricLevels().stream()
                .filter(level -> level.getName().equals(IMetricLevel.MODULE)).findAny();
        if (metricLevelOptional.isPresent())
        {
            processMetrics(context, inputComponent, system, module, moduleInfoProcessor, metricLevelOptional.get(), data);
        }

        final List<IIssue> systemIssues = moduleInfoProcessor.getIssues(issue -> !issue.isIgnored()
                && !SonargraphBase.ignoreIssueType(issue.getIssueType()) && issue.getAffectedNamedElements().contains(module));
        for (final IIssue nextIssue : systemIssues)
        {
            final IIssueType nextIssueType = nextIssue.getIssueType();
            final String nextRealRuleKey = SonargraphBase.createRuleKey(nextIssueType.getName());
            final String nextRuleKeyToCheck = SonargraphBase.isScriptIssue(nextIssueType)
                    ? SonargraphBase.createRuleKey(SonargraphBase.SCRIPT_ISSUE_NAME)
                    : nextRealRuleKey;
            final ActiveRule nextRule = data.getActiveRules().get(nextRuleKeyToCheck);
            if (nextRule != null && !data.issueAlreadyCreated(nextRealRuleKey))
            {
                createIssue(context, inputComponent, nextRule, createIssueDescription(moduleInfoProcessor, nextIssue), null);
                data.addCreatedIssue(nextRealRuleKey);
            }
        }

        final Map<ISourceFile, List<IIssue>> sourceFileIssueMap = moduleInfoProcessor
                .getIssuesForSourceFiles(issue -> !issue.isIgnored() && !SonargraphBase.ignoreIssueType(issue.getIssueType()));
        for (final Entry<ISourceFile, List<IIssue>> issuesPerSourceFile : sourceFileIssueMap.entrySet())
        {
            addIssuesToSourceFile(context, moduleInfoProcessor, data.getActiveRules(), moduleInfoProcessor.getBaseDirectory(),
                    issuesPerSourceFile.getKey(), issuesPerSourceFile.getValue());
        }

        final Map<String, List<IIssue>> directoryIssueMap = moduleInfoProcessor
                .getIssuesForDirectories(issue -> !issue.isIgnored() && !SonargraphBase.ignoreIssueType(issue.getIssueType()));
        for (final Entry<String, List<IIssue>> issuesPerDirectory : directoryIssueMap.entrySet())
        {
            addIssuesToDirectory(context, moduleInfoProcessor, data.getActiveRules(), moduleInfoProcessor.getBaseDirectory(),
                    issuesPerDirectory.getKey(), issuesPerDirectory.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private void createNewMeasure(final SensorContext context, final InputComponent inputComponent, final Metric<? extends Serializable> metric,
            final IMetricValue metricValue)
    {
        assert context != null : "Parameter 'context' of method 'createNewMeasure' must not be null";
        assert inputComponent != null : "Parameter 'inputComponent' of method 'createNewMeasure' must not be null";
        assert metric != null : "Parameter 'metric' of method 'createNewMeasure' must not be null";
        assert metricValue != null : "Parameter 'metricValue' of method 'createNewMeasure' must not be null";

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

    private void processMetrics(final SensorContext context, final InputComponent inputComponent, final ISoftwareSystem softwareSystem,
            final INamedElementContainer container, final IInfoProcessor infoProcessor, final IMetricLevel level, final ProcessingData data)
    {
        assert context != null : "Parameter 'context' of method 'processMetrics' must not be null";
        assert inputComponent != null : "Parameter 'inputComponent' of method 'processMetrics' must not be null";
        assert softwareSystem != null : "Parameter 'softwareSystem' of method 'processMetrics' must not be null";
        assert container != null : "Parameter 'container' of method 'processMetrics' must not be null";
        assert infoProcessor != null : "Parameter 'infoProcessor' of method 'processMetrics' must not be null";
        assert level != null : "Parameter 'level' of method 'processMetrics' must not be null";
        assert data != null : "Parameter 'data' of method 'processMetrics' must not be null";

        for (final IMetricId nextMetricId : infoProcessor.getMetricIdsForLevel(level))
        {
            String nextMetricKey = SonargraphBase.createMetricKeyFromStandardName(nextMetricId.getName());
            Metric<Serializable> metric = data.getMetrics().get(nextMetricKey);
            if (metric == null)
            {
                //Try custom metrics
                nextMetricKey = SonargraphBase.createCustomMetricKeyFromStandardName(softwareSystem.getName(), nextMetricId.getName());
                metric = data.getMetrics().get(nextMetricKey);
            }
            if (metric == null)
            {
                if (customMetrics == null)
                {
                    customMetrics = SonargraphBase.loadCustomMetrics();
                }

                SonargraphBase.addCustomMetric(softwareSystem, nextMetricId, customMetrics);
                LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Custom metric added '" + softwareSystem.getName() + "/"
                        + nextMetricId.getName() + "'");
                continue;
            }

            final Optional<IMetricValue> metricValueOptional = infoProcessor.getMetricValueForElement(nextMetricId, level, container.getFqName());
            if (metricValueOptional.isPresent())
            {
                if (!data.measureAlreadyCreated(nextMetricKey))
                {
                    createNewMeasure(context, inputComponent, metric, metricValueOptional.get());
                    data.addCreatedMeasure(nextMetricKey);
                }
            }
            else
            {
                LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": No value found for metric '" + nextMetricKey + "'");
            }
        }
    }

    private void createIssue(final SensorContext context, final InputComponent inputComponent, final ActiveRule rule, final String msg,
            final Consumer<NewIssueLocation> consumer)
    {
        assert context != null : "Parameter 'context' of method 'createIssue' must not be null";
        assert inputComponent != null : "Parameter 'inputComponent' of method 'createIssue' must not be null";
        assert rule != null : "Parameter 'rule' of method 'createIssue' must not be null";
        assert msg != null && msg.length() > 0 : "Parameter 'msg' of method 'createIssue' must not be empty";

        final NewIssue newIssue = context.newIssue();
        newIssue.forRule(rule.getRule().ruleKey());

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

    @Override
    public void describe(final SensorDescriptor descriptor)
    {
        assert descriptor != null : "Parameter 'descriptor' of method 'describe' must not be null";
        descriptor.name(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
    }

    private List<IModule> getModuleCandidates(final ISoftwareSystem softwareSystem)
    {
        assert softwareSystem != null : "Parameter 'softwareSystem' of method 'getModuleCandidates' must not be null";

        final TreeMap<Integer, List<IModule>> numberOfMatchedRootDirsToModules = new TreeMap<>();
        for (final IModule nextModule : softwareSystem.getModules().values())
        {
            int matchedRootDirs = 0;

            for (final IRootDirectory nextRootDirectory : nextModule.getRootDirectories())
            {
                final String nextRelPath = nextRootDirectory.getRelativePath();
                final File nextResolved = fileSystem.resolvePath(nextRelPath);
                if (nextResolved != null && nextResolved.exists())
                {
                    matchedRootDirs++;
                }
            }

            if (matchedRootDirs > 0)
            {
                final Integer nextMatchedRootDirsAsInteger = Integer.valueOf(matchedRootDirs);
                List<IModule> nextMatched = numberOfMatchedRootDirsToModules.get(nextMatchedRootDirsAsInteger);
                if (nextMatched == null)
                {
                    nextMatched = new ArrayList<>(2);
                    numberOfMatchedRootDirsToModules.put(nextMatchedRootDirsAsInteger, nextMatched);
                }
                nextMatched.add(nextModule);
            }
        }

        if (!numberOfMatchedRootDirsToModules.isEmpty())
        {
            return numberOfMatchedRootDirsToModules.lastEntry().getValue();
        }

        return Collections.emptyList();
    }

    private IModule resolveMatchingModuleByName(final List<IModule> moduleCandidates, final String inputModuleKey)
    {
        assert moduleCandidates != null && moduleCandidates
                .size() >= 2 : "Parameter 'moduleCandidates' of method 'resolveMatchingModuleByName' must at least contain 2 elements";

        IModule matched = null;

        for (final IModule nextMatchedModule : moduleCandidates)
        {
            final String nextModuleFqName = nextMatchedModule.getFqName();
            if (nextModuleFqName == null || nextModuleFqName.isEmpty() || !nextModuleFqName.startsWith(WORKSPACE_ID))
            {
                LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Ignoring invalid module fq name '" + nextModuleFqName + "'");
            }
            else
            {
                final String nextModuleName = nextModuleFqName.substring(WORKSPACE_ID.length(), nextModuleFqName.length());
                if (inputModuleKey.indexOf(nextModuleName) != -1)
                {
                    if (matched != null)
                    {
                        //More than 1 module matched - impossible to decide
                        matched = null;
                        break;
                    }
                    matched = nextMatchedModule;
                }
            }
        }

        return matched;
    }

    private IModule matchModule(final ISoftwareSystem softwareSystem, final String inputModuleKey)
    {
        assert softwareSystem != null : "Parameter 'softwareSystem' of method 'matchModule' must not be null";
        assert inputModuleKey != null && inputModuleKey.length() > 0 : "Parameter 'inputModuleKey' of method 'matchModule' must not be empty";

        IModule matched = null;

        final List<IModule> moduleCandidates = getModuleCandidates(softwareSystem);
        if (moduleCandidates.size() == 1)
        {
            matched = moduleCandidates.get(0);
        }
        else if (moduleCandidates.size() > 1)
        {
            matched = resolveMatchingModuleByName(moduleCandidates, inputModuleKey);
        }

        if (matched == null)
        {
            LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": No module match found in report for '" + inputModuleKey + "'");
        }
        else
        {
            LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Matched module '" + matched.getName() + "'");
        }

        return matched;
    }

    private File getReportFile(final Configuration configuration)
    {
        assert configuration != null : "Parameter 'configuration' of method 'getReportFile' must not be null";

        String relativeReportPath = null;

        final Optional<String> configuredRelativeReportPathOptional = configuration.get(SonargraphBase.RELATIVE_REPORT_PATH);
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
            relativeReportPath = SonargraphBase.RELATIVE_REPORT_PATH_DEFAULT;
            LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Relative report path not configured - using default '"
                    + SonargraphBase.RELATIVE_REPORT_PATH_DEFAULT + "'");
        }

        final File reportFile = fileSystem.resolvePath(relativeReportPath);
        if (reportFile.exists())
        {
            LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Using report file '" + reportFile.getAbsolutePath() + "'");
            return reportFile;
        }

        LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Report file '" + reportFile.getAbsolutePath() + "' not found");
        return null;
    }

    private ProcessingData createProcessingData()
    {
        final Map<String, ActiveRule> activeRules = new HashMap<>();
        qualityProfile.getActiveRulesByRepository(SonargraphBase.SONARGRAPH_PLUGIN_KEY).forEach(a -> activeRules.put(a.getRuleKey(), a));
        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + activeRules.size() + " rule(s) activated");

        final Map<String, Metric<Serializable>> metrics = metricFinder.findAll().stream()
                .filter(m -> m.key().startsWith(SonargraphBase.METRIC_ID_PREFIX)).collect(Collectors.toMap(Metric::key, m -> m));
        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + metrics.size() + " metric(s) defined");

        return new ProcessingData(activeRules, metrics);
    }

    private boolean isRootModule(final Configuration configuration, final InputModule inputModule)
    {
        assert configuration != null : "Parameter 'configuration' of method 'isRootModule' must not be null";
        assert inputModule != null : "Parameter 'inputModule' of method 'isRootModule' must not be null";

        boolean isRoot = true;
        final Optional<String> projectKeyOptional = configuration.get("sonar.projectKey");
        if (projectKeyOptional.isPresent() && !projectKeyOptional.get().equals(inputModule.key()))
        {
            isRoot = false;
        }
        return isRoot;
    }

    private void process(final SensorContext context, final ISonargraphSystemController controller, final InputModule inputModule,
            final boolean isRoot)
    {
        assert controller != null : "Parameter 'controller' of method 'process' must not be null";

        final ISoftwareSystem softwareSystem = controller.getSoftwareSystem();
        if (!softwareSystem.getModules().isEmpty())
        {
            final IModule module = matchModule(softwareSystem, inputModule.key());
            if (isRoot || module != null)
            {
                final ProcessingData data = createProcessingData();
                if (module != null)
                {
                    processModule(context, inputModule, softwareSystem, module, controller.createModuleInfoProcessor(module), data);
                }
                if (isRoot)
                {
                    processSystem(context, inputModule, softwareSystem, controller.createSystemInfoProcessor(), data);
                }
                if (customMetrics != null)
                {
                    SonargraphBase.save(customMetrics);
                    customMetrics = null;
                }
            }
        }
        else
        {
            LOGGER.warn(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": No modules defined in Sonargraph system");
        }
    }

    @Override
    public void execute(final SensorContext context)
    {
        assert context != null : "Parameter 'context' of method 'execute' must not be null";

        final InputModule inputModule = context.module();
        assert inputModule != null : "'inputModule' of method 'execute' must not be null";

        final Configuration configuration = context.config();
        assert configuration != null : "'configuration' of method 'execute' must not be null";

        final boolean isRoot = isRootModule(configuration, inputModule);
        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Processing " + (isRoot ? "root " : "") + "module '" + inputModule.key()
                + "' with project base directory '" + fileSystem.baseDir() + "'");

        final File reportFile = getReportFile(configuration);
        if (reportFile != null)
        {
            LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Reading report from '" + reportFile.getAbsolutePath() + "'");

            final ISonargraphSystemController controller = ControllerAccess.createController();
            final Result result = controller.loadSystemReport(reportFile);
            if (result.isSuccess())
            {
                process(context, controller, inputModule, isRoot);
            }
            else
            {
                LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + result.toString());
            }
        }

        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Finished processing module '" + inputModule.key() + "'");
    }
}