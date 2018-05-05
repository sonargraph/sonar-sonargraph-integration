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

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import org.sonar.api.measures.Metric;

import com.hello2morrow.sonargraph.integration.access.foundation.Utility;
import com.hello2morrow.sonargraph.integration.access.model.IIssueType;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;

final class SonargraphBase
{
    static final String SONARGRAPH_PLUGIN_KEY = "sonargraphintegration";
    static final String SONARGRAPH_PLUGIN_PRESENTATION_NAME = "Sonargraph Integration";
    static final String JAVA = "java";
    static final String METRIC_ID_PREFIX = "sg_i.";//There is a max length of 64 characters for metric keys
    static final String CONFIG_PREFIX = "sonar.sonargraph_integration.";
    static final String COST_PER_INDEX_POINT = CONFIG_PREFIX + "index_point_cost";
    static final double COST_PER_INDEX_POINT_DEFAULT = 11.0;
    static final String CURRENCY = CONFIG_PREFIX + "currency";
    static final String CURRENCY_DEFAULT = "USD";
    static final String REPORT_PATH = CONFIG_PREFIX + "report.path";
    static final String METADATA_PATH = CONFIG_PREFIX + "exportmetadata.path";
    static final String WORKSPACE = "Workspace";
    static final String SCRIPT_ISSUE_CATEGORY = "ScriptBased";
    static final String SCRIPT_ISSUE_CATEGORY_PRESENTATION_NAME = "Script Based";
    static final String SCRIPT_ISSUE_NAME = "ScriptIssue";
    static final String SCRIPT_ISSUE_PRESENTATION_NAME = "Script Issue";

    private static final List<String> IGNORE_ISSUE_TYPE_CATEGORIES = Arrays.asList(WORKSPACE, "InstallationConfiguration");

    private SonargraphBase()
    {
        super();
    }

    static String createRuleKey(final String issueTypeName)
    {
        assert issueTypeName != null && issueTypeName.length() > 0 : "Parameter 'issueTypeName' of method 'createRuleKey' must not be empty";
        return Utility.convertMixedCaseStringToConstantName(issueTypeName).replace(" ", "_");
    }

    static String createRuleName(final String issueTypePresentationName)
    {
        assert issueTypePresentationName != null
                && issueTypePresentationName.length() > 0 : "Parameter 'issueTypePresentationName' of method 'createRuleName' must not be empty";
        return SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + issueTypePresentationName;
    }

    static String createRuleCategoryTag(final String categoryPresentationName)
    {
        assert categoryPresentationName != null
                && categoryPresentationName.length() > 0 : "Parameter 'categoryPresentationName' of method 'createRuleCategoryTag' must not be empty";
        return categoryPresentationName.replace(' ', '-').toLowerCase();
    }

    static String createMetricKeyFromStandardName(final String metricIdName)
    {
        assert metricIdName != null
                && metricIdName.length() > 0 : "Parameter 'metricIdName' of method 'createMetricKeyFromStandardName' must not be empty";
        return SonargraphBase.METRIC_ID_PREFIX + Utility.convertMixedCaseStringToConstantName(metricIdName).replace(" ", "");
    }

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

    static Metric<Serializable> createMetric(final IMetricId metricId)
    {
        assert metricId != null : "Parameter 'metricId' of method 'createMetric' must not be null";

        final Metric.Builder metric = new Metric.Builder(SonargraphBase.createMetricKeyFromStandardName(metricId.getName()),
                metricId.getPresentationName(), metricId.isFloat() ? Metric.ValueType.FLOAT : Metric.ValueType.INT)
                        .setDescription(trimDescription(metricId)).setDomain(SONARGRAPH_PLUGIN_PRESENTATION_NAME);

        setBestValue(metricId, metric);
        setWorstValue(metricId, metric);
        setMetricDirection(metricId, metric);

        return metric.create();
    }

    static boolean ignoreIssueType(final IIssueType issueType)
    {
        assert issueType != null : "Parameter 'issueType' of method 'ignoreIssueType' must not be null";
        final String categoryName = issueType.getCategory().getName();

        for (final String next : IGNORE_ISSUE_TYPE_CATEGORIES)
        {
            if (next.equals(categoryName))
            {
                return true;
            }
        }

        return false;
    }

    static boolean isWorkspoceIssue(final IIssueType issueType)
    {
        assert issueType != null : "Parameter 'issueType' of method 'isWorkspoceIssue' must not be null";
        return WORKSPACE.equals(issueType.getCategory().getName());
    }

    static boolean isScriptIssue(final IIssueType issueType)
    {
        assert issueType != null : "Parameter 'issueType' of method 'isScriptIssue' must not be null";
        return SCRIPT_ISSUE_CATEGORY.equals(issueType.getCategory().getName());
    }

    static String toLowerCase(String input, final boolean firstLower)
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
}