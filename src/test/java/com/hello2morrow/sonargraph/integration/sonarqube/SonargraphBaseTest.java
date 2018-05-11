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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.hello2morrow.sonargraph.integration.access.model.IExportMetaData;
import com.hello2morrow.sonargraph.integration.access.model.IIssueType;

public final class SonargraphBaseTest
{
    @Test
    public void testCreateKeysAndNames()
    {
        //Rules
        assertEquals("SCRIPT_ISSUE", SonargraphBase.createRuleKey(SonargraphBase.SCRIPT_ISSUE_NAME));
        assertEquals("Sonargraph Integration: Script Issue", SonargraphBase.createRuleName(SonargraphBase.SCRIPT_ISSUE_PRESENTATION_NAME));
        assertEquals("script-based", SonargraphBase.createRuleCategoryTag(SonargraphBase.SCRIPT_ISSUE_CATEGORY_PRESENTATION_NAME));
        //Metrics
        assertEquals("sg_i.Sonargraph.NUMBER_OF_STATEMENTS",
                SonargraphBase.createCustomMetricKeyFromStandardName("Sonargraph", "NumberOfStatements"));
        assertEquals("sg_i.NUMBER_OF_STATEMENTS", SonargraphBase.createMetricKeyFromStandardName("NumberOfStatements"));
    }

    @Test
    public void testReadBuiltInData()
    {
        final IExportMetaData exportMetaData = SonargraphBase.readBuiltInMetaData();
        assertNotNull(exportMetaData);

        int ignored = 0;
        int errorWarningWorkspace = 0;
        int script = 0;

        for (final IIssueType nextIssueType : exportMetaData.getIssueTypes().values())
        {
            if (SonargraphBase.ignoreIssueType(nextIssueType))
            {
                ignored++;
            }
            else if (SonargraphBase.isErrorOrWarningWorkspoceIssue(nextIssueType))
            {
                errorWarningWorkspace++;
            }
            else if (SonargraphBase.isScriptIssue(nextIssueType))
            {
                script++;
            }
        }

        assertTrue(ignored > 0);
        assertTrue(errorWarningWorkspace == 0);
        assertTrue(script == 0);
    }
}