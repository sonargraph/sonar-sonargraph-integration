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

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.sonar.api.config.Configuration;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.Metrics;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import com.hello2morrow.sonargraph.integration.access.controller.IMetaDataController;
import com.hello2morrow.sonargraph.integration.access.foundation.ResultWithOutcome;
import com.hello2morrow.sonargraph.integration.access.model.IExportMetaData;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;
import com.hello2morrow.sonargraph.integration.access.model.IMetricLevel;

public final class SonargraphMetrics implements Metrics
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

    private static final Logger LOGGER = Loggers.get(SonargraphMetrics.class);
    private static final String BUILT_IN_META_DATA_RESOURCE_PATH = "/com/hello2morrow/sonargraph/integration/sonarqube/ExportMetaData.xml";

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
        try (InputStream inputStream = SonargraphMetrics.class.getResourceAsStream(BUILT_IN_META_DATA_RESOURCE_PATH))
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

    private final List<Metric<? extends Serializable>> metrics = new ArrayList<>();
    private final Configuration configuration;

    public SonargraphMetrics(final Configuration configuration)
    {
        assert configuration != null : "Parameter 'configuration' of method 'SonargraphMetrics' must not be null";
        this.configuration = configuration;
    }

    /*
        //Metrics
        final Map<String, IMetricId> metricMap = new HashMap<>();
    
        //We are currently only interested in metrics on System and Module levels
        getMetricsForLevel(builtInMetaData, builtInMetaData.getMetricLevels().get(IMetricLevel.SYSTEM), metricMap);
        getMetricsForLevel(builtInMetaData, builtInMetaData.getMetricLevels().get(IMetricLevel.MODULE), metricMap);
    
        //        if (additionalMetaData != null)
        //        {
        //            getMetricsForLevel(additionalMetaData, additionalMetaData.getMetricLevels().get(IMetricLevel.SYSTEM), metricMap);
        //            getMetricsForLevel(additionalMetaData, additionalMetaData.getMetricLevels().get(IMetricLevel.MODULE), metricMap);
        //        }
    
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
    */
    @SuppressWarnings("rawtypes")
    @Override
    public List<Metric> getMetrics()
    {
        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Loading metrics");

        return Collections.unmodifiableList(this.metrics);
    }
}