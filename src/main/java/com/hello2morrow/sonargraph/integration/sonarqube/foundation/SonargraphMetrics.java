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
package com.hello2morrow.sonargraph.integration.sonarqube.foundation;

import org.sonar.api.measures.Metric;

import com.hello2morrow.sonargraph.integration.access.foundation.StringUtility;

public class SonargraphMetrics
{
    public static final String DOMAIN_SONARGRAPH = "Sonargraph Integration";

    /*
     * Additional metric for Structural Debt widget
     */
    public static final Metric<?> VIRTUAL_MODEL_FEATURE_AVAILABLE = new Metric.Builder(createMetricKey("VIRTUAL_MODEL_FEATURE_AVAILABLE"),
            "Is virtual model feature enabled", Metric.ValueType.BOOL).setDescription("Used to signal if the virtual model feature is available")
            .setQualitative(false).setDomain(DOMAIN_SONARGRAPH).setHidden(true).create();

    public static final Metric<?> STRUCTURAL_DEBT_COST = new Metric.Builder(createMetricKey("STRUCTURAL_DEBT_COST"), "Structural Debt Cost",
            Metric.ValueType.FLOAT).setDescription("Estimated Cost to Repair Structural Erosion").setQualitative(true)
            .setDirection(Metric.DIRECTION_WORST)
            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).setHidden(false).create();

    public static final Metric<?> CURRENT_VIRTUAL_MODEL = new Metric.Builder(createMetricKey("CURRENT_VIRTUAL_MODEL"),
            "Currently Used Virtual Model", Metric.ValueType.STRING)
            .setDescription("Virtual model that has been active when the report has been created").setQualitative(false)
            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).setHidden(true).create();

    public static final Metric<?> NUMBER_OF_RESOLUTIONS = new Metric.Builder(createMetricKey("NUMBER_OF_RESOLUTIONS"), "Number of Resolutions",
            Metric.ValueType.INT).setDescription("Number of defined resolutions").setDirection(Metric.DIRECTION_WORST).setQualitative(true)
            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    public static final Metric<?> NUMBER_OF_UNAPPLICABLE_RESOLUTIONS = new Metric.Builder(createMetricKey("NUMBER_OF_UNAPPLICABLE_RESOLUTIONS"),
            "Number of non-applicable Resolutions", Metric.ValueType.INT).setDescription("Number of resolutions that have no matching elements")
            .setDirection(Metric.DIRECTION_WORST).setQualitative(true)
            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();

    public static final Metric<?> NUMBER_OF_TASKS = new Metric.Builder(createMetricKey("NUMBER_OF_TASKS"), "Number of Tasks", Metric.ValueType.INT)
            .setDescription("Number of task definitions").setDirection(Metric.DIRECTION_WORST).setQualitative(true)
            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    public static final Metric<?> NUMBER_OF_UNAPPLICABLE_TASKS = new Metric.Builder(createMetricKey("NUMBER_OF_UNAPPLICABLE_TASKS"),
            "Number of non-applicable Tasks", Metric.ValueType.INT).setDescription("Number of tasks that have no matching elements")
            .setDirection(Metric.DIRECTION_WORST).setQualitative(true)
            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();

    public static final Metric<?> NUMBER_OF_REFACTORINGS = new Metric.Builder(createMetricKey("NUMBER_OF_REFACTORINGS"), "Number of Refactorings",
            Metric.ValueType.INT).setDescription("Number of refactoring definitions").setDirection(Metric.DIRECTION_WORST).setQualitative(true)
            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    public static final Metric<?> NUMBER_OF_UNAPPLICABLE_REFACTORINGS = new Metric.Builder(createMetricKey("NUMBER_OF_UNAPPLICABLE_REFACTORINGS"),
            "Number of non-applicable Refactorings", Metric.ValueType.INT).setDescription("Number of refactorings that have no matching elements")
            .setDirection(Metric.DIRECTION_WORST).setQualitative(true)
            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();

    public static final Metric<?> NUMBER_OF_PARSER_DEPENDENCIES_AFFECTED_BY_REFACTORINGS = new Metric.Builder(
            createMetricKey("NUMBER_OF_PARSER_DEPENDENCIES_AFFECTED_BY_REFACTORINGS"), "Number of Parser Depencencies affected by Refactorings",
            Metric.ValueType.INT).setDescription("Number of parser depencencies affected by refactorings").setDirection(Metric.DIRECTION_WORST)
            .setQualitative(true).setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH)
            .create();

    /*
    * Additional metrics for Structure widget
    */
    public static final Metric<?> CYCLIC_PACKAGES_PERCENT = new Metric.Builder(createMetricKey("JAVA_CYCLIC_PACKAGES_PERCENT"),
            "Percentage of Cyclic Packages", Metric.ValueType.PERCENT).setDescription("Percentage of cyclically coupled packages")
            .setDirection(Metric.DIRECTION_WORST).setQualitative(true)
            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    public static final Metric<?> MAX_MODULE_NCCD = new Metric.Builder(createMetricKey("MAX_MODULE_NCCD"), "Highest Module NCCD",
            Metric.ValueType.FLOAT).setDescription("Highest Module NCCD").setDirection(Metric.DIRECTION_WORST).setQualitative(true)
            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();

    /*
    * Additional metrics for Architecture widget
    */
    public static final Metric<?> ARCHITECTURE_FEATURE_AVAILABLE = new Metric.Builder(createMetricKey("ARCHITECTURE_FEATURE_AVAILABLE"),
            "Is architecture feature enabled", Metric.ValueType.BOOL).setDescription("Used to signal if the architecture feature is available")
            .setQualitative(false).setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH)
            .setHidden(true).create();

    public static final Metric<?> NUMBER_OF_ISSUES = new Metric.Builder(createMetricKey("NUMBER_OF_ISSUES"), "Number of Issues", Metric.ValueType.INT)
            .setDescription("Total number of issues").setDirection(Metric.DIRECTION_WORST).setQualitative(true)
            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    public static final Metric<?> NUMBER_OF_CRITICAL_ISSUES_WITHOUT_RESOLUTION = new Metric.Builder(
            createMetricKey("NUMBER_OF_CRITICAL_ISSUES_WITHOUT_RESOLUTION"), "Number of Critical Issues Without Resolution", Metric.ValueType.INT)
            .setDescription("Total number of issues (warnings and errors) without resolution").setDirection(Metric.DIRECTION_WORST)
            .setQualitative(true).setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH)
            .create();

    public static final Metric<?> VIOLATING_COMPONENTS_PERCENT = new Metric.Builder(createMetricKey("VIOLATING_COMPONENTS_PERCENT"),
            "Percentage of Components with Architecture Violation", Metric.ValueType.PERCENT)
            .setDescription("Percentage of components with architecture violation").setDirection(Metric.DIRECTION_WORST).setQualitative(true)
            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();
    public static final Metric<?> UNASSIGNED_COMPONENTS_PERCENT = new Metric.Builder(createMetricKey("UNASSIGNED_COMPONENTS_PERCENT"),
            "Percentage of Unassigned Components", Metric.ValueType.PERCENT).setDescription("Percentage of unassigned components")
            .setDirection(Metric.DIRECTION_WORST).setQualitative(true)
            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();

    public static final Metric<?> NUMBER_OF_THRESHOLD_VIOLATIONS = new Metric.Builder(createMetricKey("NUMBER_OF_THRESHOLD_VIOLATIONS"),
            "Number of Threshold Violations", Metric.ValueType.INT).setDescription("Number of threshold violations")
            .setDirection(Metric.DIRECTION_WORST).setQualitative(true)
            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();

    public static final Metric<?> NUMBER_OF_WORKSPACE_WARNINGS = new Metric.Builder(createMetricKey("NUMBER_OF_WORKSPACE_WARNINGS"),
            "Number of Workspace Warnings", Metric.ValueType.INT).setDescription("Number of workspace warnings").setDirection(Metric.DIRECTION_WORST)
            .setQualitative(true).setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH)
            .create();
    public static final Metric<?> NUMBER_OF_IGNORED_CRITICAL_ISSUES = new Metric.Builder(createMetricKey("NUMBER_OF_IGNORED_CRITICAL_ISSUES"),
            "Number of Ignored Critical Issues", Metric.ValueType.INT).setDescription("Number of ignored critical issues")
            .setDirection(Metric.DIRECTION_WORST).setQualitative(true)
            .setDomain(com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics.DOMAIN_SONARGRAPH).create();

    private SonargraphMetrics()
    {
        //utility class
    }

    public static String createRuleKey(final String name)
    {
        return StringUtility.convertMixedCaseStringToConstantName(name).replace(" ", "_");
    }

    public static String createMetricKey(final String constantName)
    {
        return SonargraphPluginBase.ABBREVIATION + constantName;
    }

    public static String createMetricKeyFromStandardName(final String metricIdName)
    {
        return createMetricKey(StringUtility.convertMixedCaseStringToConstantName(metricIdName).replace(" ", ""));
    }

}
