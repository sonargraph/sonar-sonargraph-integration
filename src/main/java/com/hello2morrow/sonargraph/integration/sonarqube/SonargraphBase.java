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

import java.util.Arrays;
import java.util.List;

import com.hello2morrow.sonargraph.integration.access.foundation.Utility;
import com.hello2morrow.sonargraph.integration.access.model.IIssueType;

final class SonargraphBase
{
    static final String SONARGRAPH_PLUGIN_KEY = "sonargraphintegration";
    static final String SONARGRAPH_PLUGIN_PRESENTATION_NAME = "Sonargraph Integration";
    static final String JAVA = "java";
    static final String ABBREVIATION = "sg_i.";//There is a max length of 64 characters for metric keys
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

    static String createMetricKey(final String constantName)
    {
        return SonargraphBase.ABBREVIATION + constantName;
    }

    static String createMetricKeyFromStandardName(final String metricIdName)
    {
        return createMetricKey(Utility.convertMixedCaseStringToConstantName(metricIdName).replace(" ", ""));
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