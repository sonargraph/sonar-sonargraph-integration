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
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.sonar.api.config.Configuration;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.Metrics;
import org.sonar.api.rule.Severity;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import com.hello2morrow.sonargraph.integration.access.controller.ControllerAccess;
import com.hello2morrow.sonargraph.integration.access.controller.IMetaDataController;
import com.hello2morrow.sonargraph.integration.access.foundation.ResultWithOutcome;
import com.hello2morrow.sonargraph.integration.access.model.IExportMetaData;
import com.hello2morrow.sonargraph.integration.access.model.IIssueCategory;
import com.hello2morrow.sonargraph.integration.access.model.IIssueType;
import com.hello2morrow.sonargraph.integration.access.model.IMergedExportMetaData;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;
import com.hello2morrow.sonargraph.integration.access.model.IMetricLevel;

public final class SonargraphRulesAndMetrics implements RulesDefinition, Metrics
{
    //    /*
    //     * Additional metric for Structural Debt widget
    //     */
    //    public static final Metric<?> VIRTUAL_MODEL_FEATURE_AVAILABLE = new Metric.Builder(createMetricKey("VIRTUAL_MODEL_FEATURE_AVAILABLE"),
    //            "Is virtual model feature enabled", Metric.ValueType.BOOL).setDescription("Used to signal if the virtual model feature is available")
    //                    .setQualitative(false).setDomain(DOMAIN_SONARGRAPH).setHidden(true).create();
    //
    //    public static final Metric<?> STRUCTURAL_DEBT_COST = new Metric.Builder(createMetricKey("STRUCTURAL_DEBT_COST"), "Structural Debt Cost",
    //            Metric.ValueType.FLOAT).setDescription("Estimated Cost to Repair Structural Erosion").setQualitative(true)
    //                    .setDirection(Metric.DIRECTION_WORST)
    //                    .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).setHidden(false)
    //                    .create();
    //
    //    public static final Metric<?> CURRENT_VIRTUAL_MODEL = new Metric.Builder(createMetricKey("CURRENT_VIRTUAL_MODEL"), "Currently Used Virtual Model",
    //            Metric.ValueType.STRING).setDescription("Virtual model that has been active when the report has been created").setQualitative(false)
    //                    .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).setHidden(true)
    //                    .create();
    //
    //    public static final Metric<?> NUMBER_OF_RESOLUTIONS = new Metric.Builder(createMetricKey("NUMBER_OF_RESOLUTIONS"), "Number of Resolutions",
    //            Metric.ValueType.INT).setDescription("Number of defined resolutions").setDirection(Metric.DIRECTION_WORST).setQualitative(true)
    //                    .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    //    public static final Metric<?> NUMBER_OF_UNAPPLICABLE_RESOLUTIONS = new Metric.Builder(createMetricKey("NUMBER_OF_UNAPPLICABLE_RESOLUTIONS"),
    //            "Number of non-applicable Resolutions", Metric.ValueType.INT).setDescription("Number of resolutions that have no matching elements")
    //                    .setDirection(Metric.DIRECTION_WORST).setQualitative(true)
    //                    .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    //
    //    public static final Metric<?> NUMBER_OF_TASKS = new Metric.Builder(createMetricKey("NUMBER_OF_TASKS"), "Number of Tasks", Metric.ValueType.INT)
    //            .setDescription("Number of task definitions").setDirection(Metric.DIRECTION_WORST).setQualitative(true)
    //            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    //    public static final Metric<?> NUMBER_OF_UNAPPLICABLE_TASKS = new Metric.Builder(createMetricKey("NUMBER_OF_UNAPPLICABLE_TASKS"),
    //            "Number of non-applicable Tasks", Metric.ValueType.INT).setDescription("Number of tasks that have no matching elements")
    //                    .setDirection(Metric.DIRECTION_WORST).setQualitative(true)
    //                    .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    //
    //    public static final Metric<?> NUMBER_OF_REFACTORINGS = new Metric.Builder(createMetricKey("NUMBER_OF_REFACTORINGS"), "Number of Refactorings",
    //            Metric.ValueType.INT).setDescription("Number of refactoring definitions").setDirection(Metric.DIRECTION_WORST).setQualitative(true)
    //                    .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    //    public static final Metric<?> NUMBER_OF_UNAPPLICABLE_REFACTORINGS = new Metric.Builder(createMetricKey("NUMBER_OF_UNAPPLICABLE_REFACTORINGS"),
    //            "Number of non-applicable Refactorings", Metric.ValueType.INT).setDescription("Number of refactorings that have no matching elements")
    //                    .setDirection(Metric.DIRECTION_WORST).setQualitative(true)
    //                    .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    //
    //    public static final Metric<?> NUMBER_OF_PARSER_DEPENDENCIES_AFFECTED_BY_REFACTORINGS = new Metric.Builder(
    //            createMetricKey("NUMBER_OF_PARSER_DEPENDENCIES_AFFECTED_BY_REFACTORINGS"), "Number of Parser Depencencies affected by Refactorings",
    //            Metric.ValueType.INT).setDescription("Number of parser depencencies affected by refactorings").setDirection(Metric.DIRECTION_WORST)
    //                    .setQualitative(true).setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH)
    //                    .create();
    //
    //    /*
    //    * Additional metrics for Structure widget
    //    */
    //    public static final Metric<?> CYCLIC_PACKAGES_PERCENT = new Metric.Builder(createMetricKey("JAVA_CYCLIC_PACKAGES_PERCENT"),
    //            "Percentage of Cyclic Packages", Metric.ValueType.PERCENT).setDescription("Percentage of cyclically coupled packages")
    //                    .setDirection(Metric.DIRECTION_WORST).setQualitative(true)
    //                    .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    //    public static final Metric<Serializable> MAX_MODULE_NCCD = new Metric.Builder(createMetricKey("MAX_MODULE_NCCD"), "Highest Module NCCD",
    //            Metric.ValueType.FLOAT).setDescription("Highest Module NCCD").setDirection(Metric.DIRECTION_WORST).setQualitative(true)
    //                    .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    //
    //    /*
    //    * Additional metrics for Architecture widget
    //    */
    //    public static final Metric<?> ARCHITECTURE_FEATURE_AVAILABLE = new Metric.Builder(createMetricKey("ARCHITECTURE_FEATURE_AVAILABLE"),
    //            "Is architecture feature enabled", Metric.ValueType.BOOL).setDescription("Used to signal if the architecture feature is available")
    //                    .setQualitative(false).setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH)
    //                    .setHidden(true).create();
    //
    //    public static final Metric<?> NUMBER_OF_ISSUES = new Metric.Builder(createMetricKey("NUMBER_OF_ISSUES"), "Number of Issues", Metric.ValueType.INT)
    //            .setDescription("Total number of issues").setDirection(Metric.DIRECTION_WORST).setQualitative(true)
    //            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    //    public static final Metric<?> NUMBER_OF_CRITICAL_ISSUES_WITHOUT_RESOLUTION = new Metric.Builder(
    //            createMetricKey("NUMBER_OF_CRITICAL_ISSUES_WITHOUT_RESOLUTION"), "Number of Critical Issues Without Resolution", Metric.ValueType.INT)
    //                    .setDescription("Total number of issues (warnings and errors) without resolution").setDirection(Metric.DIRECTION_WORST)
    //                    .setQualitative(true).setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH)
    //                    .create();
    //
    //    public static final Metric<?> VIOLATING_COMPONENTS_PERCENT = new Metric.Builder(createMetricKey("VIOLATING_COMPONENTS_PERCENT"),
    //            "Percentage of Components with Architecture Violation", Metric.ValueType.PERCENT)
    //                    .setDescription("Percentage of components with architecture violation").setDirection(Metric.DIRECTION_WORST).setQualitative(true)
    //                    .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    //    public static final Metric<?> UNASSIGNED_COMPONENTS_PERCENT = new Metric.Builder(createMetricKey("UNASSIGNED_COMPONENTS_PERCENT"),
    //            "Percentage of Unassigned Components", Metric.ValueType.PERCENT).setDescription("Percentage of unassigned components")
    //                    .setDirection(Metric.DIRECTION_WORST).setQualitative(true)
    //                    .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    //
    //    public static final Metric<?> NUMBER_OF_THRESHOLD_VIOLATIONS = new Metric.Builder(createMetricKey("NUMBER_OF_THRESHOLD_VIOLATIONS"),
    //            "Number of Threshold Violations", Metric.ValueType.INT).setDescription("Number of threshold violations")
    //                    .setDirection(Metric.DIRECTION_WORST).setQualitative(true)
    //                    .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    //
    //    public static final Metric<?> NUMBER_OF_WORKSPACE_WARNINGS = new Metric.Builder(createMetricKey("NUMBER_OF_WORKSPACE_WARNINGS"),
    //            "Number of Workspace Warnings", Metric.ValueType.INT).setDescription("Number of workspace warnings").setDirection(Metric.DIRECTION_WORST)
    //                    .setQualitative(true).setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH)
    //                    .create();
    //    public static final Metric<?> NUMBER_OF_IGNORED_CRITICAL_ISSUES = new Metric.Builder(createMetricKey("NUMBER_OF_IGNORED_CRITICAL_ISSUES"),
    //            "Number of Ignored Critical Issues", Metric.ValueType.INT).setDescription("Number of ignored critical issues")
    //                    .setDirection(Metric.DIRECTION_WORST).setQualitative(true)
    //                    .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();

    private static final Logger LOGGER = Loggers.get(SonargraphRulesAndMetrics.class);
    private static final String BUILT_IN_META_DATA_RESOURCE_PATH = "/com/hello2morrow/sonargraph/integration/sonarqube/ExportMetaData.xml";
    private static final String RULE_TAG_SONARGRAPH = "sonargraph-integration";

    private static String trimDescription(final IMetricId id)
    {
        assert id != null : "Parameter 'id' of method 'trimDescription' must not be null";
        final String description = id.getDescription();
        return description.length() > 255 ? description.substring(0, 252) + "..." : description;
    }

    private static void setMetricDirection(final IMetricId id, final Metric.Builder metric)
    {
        assert id != null : "Parameter 'id' of method 'setMetricDirection' must not be null";
        assert metric != null : "Parameter 'metric' of method 'setMetricDirection' must not be null";

        if (id.getBestValue() > id.getWorstValue())
        {
            metric.setDirection(Metric.DIRECTION_BETTER);
        }
        else if (id.getBestValue() < id.getWorstValue())
        {
            metric.setDirection(Metric.DIRECTION_WORST);
        }
        else
        {
            metric.setDirection(Metric.DIRECTION_NONE);
        }
    }

    private static void setWorstValue(final IMetricId id, final Metric.Builder metric)
    {
        assert id != null : "Parameter 'id' of method 'setWorstValue' must not be null";
        assert metric != null : "Parameter 'metric' of method 'setWorstValue' must not be null";

        if (!id.getWorstValue().equals(Double.NaN) && !id.getWorstValue().equals(Double.POSITIVE_INFINITY)
                && !id.getWorstValue().equals(Double.NEGATIVE_INFINITY))
        {
            metric.setWorstValue(id.getWorstValue());
        }
    }

    private static void setBestValue(final IMetricId id, final Metric.Builder metric)
    {
        assert id != null : "Parameter 'id' of method 'setBestValue' must not be null";
        assert metric != null : "Parameter 'metric' of method 'setBestValue' must not be null";

        if (!id.getBestValue().equals(Double.NaN) && !id.getBestValue().equals(Double.POSITIVE_INFINITY)
                && !id.getBestValue().equals(Double.NEGATIVE_INFINITY))
        {
            metric.setBestValue(id.getBestValue());
        }
    }

    private static void getMetricsForLevel(final IExportMetaData metaData, final IMetricLevel level, final Map<String, IMetricId> metricMap)
    {
        assert metaData != null : "Parameter 'metaData' of method 'getMetricsForLevel' must not be null";
        assert level != null : "Parameter 'level' of method 'getMetricsForLevel' must not be null";
        assert metricMap != null : "Parameter 'metricMap' of method 'getMetricsForLevel' must not be null";

        for (final IMetricId next : metaData.getMetricIdsForLevel(level))
        {
            if (!metricMap.containsKey(next.getName()))
            {
                metricMap.put(next.getName(), next);
            }
        }
    }

    private static IExportMetaData loadBuiltInMetaData(final IMetaDataController controller)
    {
        assert controller != null : "Parameter 'controller' of method 'loadBuiltInMetaData' must not be null";

        final String errorMsg = "Failed to load built in meta data from '" + BUILT_IN_META_DATA_RESOURCE_PATH + "'";
        try (InputStream inputStream = SonargraphRulesAndMetrics.class.getResourceAsStream(BUILT_IN_META_DATA_RESOURCE_PATH))
        {
            if (inputStream == null)
            {
                LOGGER.error(errorMsg);
                return null;
            }

            final ResultWithOutcome<IExportMetaData> result = controller.loadExportMetaData(inputStream, BUILT_IN_META_DATA_RESOURCE_PATH);
            if (result.isFailure())
            {
                LOGGER.error("{}: {}", errorMsg, result.toString());
                return null;
            }
            return result.getOutcome();
        }
        catch (final IOException ex)
        {
            LOGGER.error(errorMsg, ex);
        }

        return null;
    }

    private static IExportMetaData loadAdditionalMetaData(final IMetaDataController controller, final String directory)
    {
        assert controller != null : "Parameter 'controller' of method 'loadAdditionalMetaData' must not be null";
        assert directory != null && directory.length() > 0 : "Parameter 'directory' of method 'loadAdditionalMetaData' must not be empty";

        final File configurationDir = new File(directory);
        if (!configurationDir.exists() || !configurationDir.isDirectory())
        {
            LOGGER.error("Cannot load meta-data from directory '{}'. It does not exist.", directory);
            return null;
        }

        final List<File> files = Arrays.asList(configurationDir.listFiles()).stream().filter(f -> !f.isDirectory() && f.getName().endsWith(".xml"))
                .collect(Collectors.toList());
        if (!files.isEmpty())
        {
            try
            {
                final ResultWithOutcome<IMergedExportMetaData> result = controller.mergeExportMetaDataFiles(files);
                if (result.isSuccess())
                {
                    return result.getOutcome();
                }
                LOGGER.error("Failed to load configuration from '{}': {}", directory, result.toString());
            }
            catch (final Exception ex)
            {
                LOGGER.error("Failed to load configuration from '{}'", directory, ex);
            }
        }
        return null;
    }

    private static void createRule(final IIssueType issueType, final NewRepository repository)
    {
        assert issueType != null : "Parameter 'issueType' of method 'createRule' must not be null";
        assert repository != null : "Parameter 'repository' of method 'createRule' must not be null";

        final String ruleKey = SonargraphBase.createRuleKey(issueType.getName());
        final String ruleName = SonargraphBase.createRuleName(issueType.getPresentationName());
        final IIssueCategory category = issueType.getCategory();
        final String categoryPresentationName = category.getPresentationName();
        final String categoryTag = SonargraphBase.createRuleCategoryTag(categoryPresentationName);

        final NewRule rule = repository.createRule(ruleKey);
        rule.setName(ruleName);
        rule.setHtmlDescription(
                "Description '" + (issueType.getDescription().length() > 0 ? issueType.getDescription() : issueType.getPresentationName())
                        + "', category '" + categoryPresentationName + "'");
        rule.addTags(RULE_TAG_SONARGRAPH, categoryTag);

        final String severity;
        switch (issueType.getSeverity())
        {
        case ERROR:
            severity = Severity.MAJOR;
            break;
        case WARNING:
            severity = Severity.MINOR;
            break;
        case NONE:
            //$FALL-THROUGH$
        case INFO:
            severity = Severity.INFO;
            break;
        default:
            severity = Severity.MINOR;
        }

        rule.setSeverity(severity);
    }

    private final List<Metric<? extends Serializable>> metrics = new ArrayList<>();
    private final Configuration configuration;

    public SonargraphRulesAndMetrics(final Configuration configuration)
    {
        assert configuration != null : "Parameter 'configuration' of method 'SonargraphRulesRepository' must not be null";
        this.configuration = configuration;
    }

    @Override
    public void define(final Context context)
    {
        assert context != null : "Parameter 'context' of method 'define' must not be null";

        final IMetaDataController controller = ControllerAccess.createMetaDataController();
        final IExportMetaData builtInMetaData = loadBuiltInMetaData(controller);
        if (builtInMetaData == null)
        {
            return;
        }

        IExportMetaData additionalMetaData = null;
        final Optional<String> configuredMetaDataPathOptional = configuration.get(SonargraphBase.METADATA_PATH);
        if (configuredMetaDataPathOptional.isPresent())
        {
            final String configuredMetaDataPath = configuredMetaDataPathOptional.get().trim();
            if (!configuredMetaDataPath.isEmpty())
            {
                additionalMetaData = loadAdditionalMetaData(controller, configuredMetaDataPath);
            }
        }

        final NewRepository repository = context.createRepository(SonargraphBase.SONARGRAPH_PLUGIN_KEY, SonargraphBase.JAVA)
                .setName(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);

        final Set<IIssueType> builtInIssueTypes = new HashSet<>(builtInMetaData.getIssueTypes().values());
        for (final IIssueType nextIssueType : builtInIssueTypes)
        {
            if (!SonargraphBase.ignoreIssueType(nextIssueType))
            {
                createRule(nextIssueType, repository);
            }
        }

        final String scriptIssueRuleKey = SonargraphBase.createRuleKey(SonargraphBase.SCRIPT_ISSUE_NAME);
        final NewRule rule = repository.createRule(scriptIssueRuleKey);
        rule.setName(SonargraphBase.createRuleName(SonargraphBase.SCRIPT_ISSUE_PRESENTATION_NAME));
        rule.setHtmlDescription("Description '" + SonargraphBase.SCRIPT_ISSUE_PRESENTATION_NAME + "', category '"
                + SonargraphBase.SCRIPT_ISSUE_CATEGORY_PRESENTATION_NAME + "'");
        rule.addTags(RULE_TAG_SONARGRAPH, SonargraphBase.createRuleCategoryTag(SonargraphBase.SCRIPT_ISSUE_CATEGORY_PRESENTATION_NAME));

        if (additionalMetaData != null)
        {
            for (final IIssueType nextIssueType : additionalMetaData.getIssueTypes().values())
            {
                if (!SonargraphBase.ignoreIssueType(nextIssueType) && !builtInIssueTypes.contains(nextIssueType))
                {
                    //Only add additional issue types
                    createRule(nextIssueType, repository);
                }
            }
        }

        repository.done();

        //Metrics
        final Map<String, IMetricId> metricMap = new HashMap<>();

        //We are currently only interested in metrics on System and Module levels
        getMetricsForLevel(builtInMetaData, builtInMetaData.getMetricLevels().get(IMetricLevel.SYSTEM), metricMap);
        getMetricsForLevel(builtInMetaData, builtInMetaData.getMetricLevels().get(IMetricLevel.MODULE), metricMap);

        if (additionalMetaData != null)
        {
            getMetricsForLevel(additionalMetaData, additionalMetaData.getMetricLevels().get(IMetricLevel.SYSTEM), metricMap);
            getMetricsForLevel(additionalMetaData, additionalMetaData.getMetricLevels().get(IMetricLevel.MODULE), metricMap);
        }

        for (final Map.Entry<String, IMetricId> nextEntry : metricMap.entrySet())
        {
            final IMetricId next = nextEntry.getValue();
            final Metric.Builder metric = new Metric.Builder(SonargraphBase.createMetricKeyFromStandardName(next.getName()),
                    next.getPresentationName(), next.isFloat() ? Metric.ValueType.FLOAT : Metric.ValueType.INT).setDescription(trimDescription(next))
                            .setDomain(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);

            setBestValue(next, metric);
            setWorstValue(next, metric);
            setMetricDirection(next, metric);
            metrics.add(metric.create());
        }

        //Additional metrics for structural debt widget
        //        sqMetrics.add(SonargraphMetrics.STRUCTURAL_DEBT_COST);
        //
        //        sqMetrics.add(SonargraphMetrics.CURRENT_VIRTUAL_MODEL);
        //
        //        sqMetrics.add(SonargraphMetrics.VIRTUAL_MODEL_FEATURE_AVAILABLE);
        //        sqMetrics.add(SonargraphMetrics.NUMBER_OF_TASKS);
        //        sqMetrics.add(SonargraphMetrics.NUMBER_OF_UNAPPLICABLE_TASKS);
        //
        //        sqMetrics.add(SonargraphMetrics.NUMBER_OF_RESOLUTIONS);
        //        sqMetrics.add(SonargraphMetrics.NUMBER_OF_UNAPPLICABLE_RESOLUTIONS);
        //
        //        sqMetrics.add(SonargraphMetrics.NUMBER_OF_REFACTORINGS);
        //        sqMetrics.add(SonargraphMetrics.NUMBER_OF_UNAPPLICABLE_REFACTORINGS);
        //
        //        sqMetrics.add(SonargraphMetrics.NUMBER_OF_PARSER_DEPENDENCIES_AFFECTED_BY_REFACTORINGS);
        //
        //        //Additional metrics for structure widget
        //        sqMetrics.add(SonargraphMetrics.CYCLIC_PACKAGES_PERCENT);
        //        sqMetrics.add(SonargraphMetrics.MAX_MODULE_NCCD);
        //
        //        //Additional metrics for architecture widget
        //        sqMetrics.add(SonargraphMetrics.ARCHITECTURE_FEATURE_AVAILABLE);
        //        sqMetrics.add(SonargraphMetrics.NUMBER_OF_ISSUES);
        //        sqMetrics.add(SonargraphMetrics.NUMBER_OF_CRITICAL_ISSUES_WITHOUT_RESOLUTION);
        //
        //        sqMetrics.add(SonargraphMetrics.VIOLATING_COMPONENTS_PERCENT);
        //        sqMetrics.add(SonargraphMetrics.UNASSIGNED_COMPONENTS_PERCENT);
        //
        //        sqMetrics.add(SonargraphMetrics.NUMBER_OF_THRESHOLD_VIOLATIONS);
        //        sqMetrics.add(SonargraphMetrics.NUMBER_OF_WORKSPACE_WARNINGS);
        //        sqMetrics.add(SonargraphMetrics.NUMBER_OF_IGNORED_CRITICAL_ISSUES);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<Metric> getMetrics()
    {
        return Collections.unmodifiableList(this.metrics);
    }
}