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
import java.util.Arrays;
import java.util.List;

import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import com.hello2morrow.sonargraph.integration.access.controller.ControllerAccess;
import com.hello2morrow.sonargraph.integration.access.controller.IMetaDataController;
import com.hello2morrow.sonargraph.integration.access.foundation.ResultWithOutcome;
import com.hello2morrow.sonargraph.integration.access.foundation.Utility;
import com.hello2morrow.sonargraph.integration.access.model.IExportMetaData;
import com.hello2morrow.sonargraph.integration.access.model.IIssueType;

final class SonargraphBase
{
    static final String SONARGRAPH_PLUGIN_KEY = "sonargraphintegration";
    static final String SONARGRAPH_PLUGIN_PRESENTATION_NAME = "Sonargraph Integration";
    static final String SONARGRAPH_RULE_TAG = "sonargraph-integration";
    static final String JAVA = "java";
    static final String METRIC_ID_PREFIX = "sg_i.";//There is a max length of 64 characters for metric keys

    static final String CONFIG_PREFIX = "sonar.sonargraph.integration";
    static final String RELATIVE_REPORT_PATH = SonargraphBase.CONFIG_PREFIX + ":" + "relative.report.path";
    static final String RELATIVE_REPORT_PATH_DEFAULT = "target/sonargraph/sonargraph-sonarqube-report.xml";

    static final String WORKSPACE = "Workspace";
    static final String SCRIPT_ISSUE_CATEGORY = "ScriptBased";
    static final String SCRIPT_ISSUE_CATEGORY_PRESENTATION_NAME = "Script Based";
    static final String SCRIPT_ISSUE_NAME = "ScriptIssue";
    static final String SCRIPT_ISSUE_PRESENTATION_NAME = "Script Issue";

    private static final Logger LOGGER = Loggers.get(SonargraphBase.class);
    private static final String BUILT_IN_META_DATA_RESOURCE_PATH = "/com/hello2morrow/sonargraph/integration/sonarqube/ExportMetaData.xml";
    private static final List<String> IGNORE_ISSUE_TYPE_CATEGORIES = Arrays.asList(WORKSPACE, "InstallationConfiguration");

    private SonargraphBase()
    {
        super();
    }

    static IExportMetaData readBuiltInMetaData()
    {
        final String errorMsg = "Failed to load built in meta data from '" + BUILT_IN_META_DATA_RESOURCE_PATH + "'";
        try (InputStream inputStream = SonargraphBase.class.getResourceAsStream(BUILT_IN_META_DATA_RESOURCE_PATH))
        {
            if (inputStream != null)
            {
                final IMetaDataController controller = ControllerAccess.createMetaDataController();
                final ResultWithOutcome<IExportMetaData> result = controller.loadExportMetaData(inputStream, BUILT_IN_META_DATA_RESOURCE_PATH);
                if (result.isFailure())
                {
                    LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + errorMsg + " - " + result.toString());
                }
                else
                {
                    return result.getOutcome();
                }
            }
            else
            {
                LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + errorMsg);
            }
        }
        catch (final IOException ex)
        {
            LOGGER.error(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": " + errorMsg, ex);
        }

        return null;
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
}