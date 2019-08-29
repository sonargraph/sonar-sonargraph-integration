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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.Test;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.measures.Metric;

import com.hello2morrow.sonargraph.integration.access.controller.ControllerFactory;
import com.hello2morrow.sonargraph.integration.access.controller.ISonargraphSystemController;
import com.hello2morrow.sonargraph.integration.access.foundation.Result;
import com.hello2morrow.sonargraph.integration.access.model.IExportMetaData;
import com.hello2morrow.sonargraph.integration.access.model.IIssueType;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;
import com.hello2morrow.sonargraph.integration.access.model.IModule;

public final class SonargraphBaseTest
{
    @Test
    public void testCreateKeysAndNames()
    {
        //Rules
        assertEquals("SCRIPT_ISSUE", SonargraphBase.createRuleKey(SonargraphBase.SCRIPT_ISSUE_NAME));
        assertEquals("Sonargraph Integration: Script Issue", SonargraphBase.createRuleName(SonargraphBase.SCRIPT_ISSUE_PRESENTATION_NAME));
        assertEquals("script-based", SonargraphBase.createRuleCategoryTag(SonargraphBase.SCRIPT_ISSUE_CATEGORY_PRESENTATION_NAME));

        assertEquals("PLUGIN_ISSUE", SonargraphBase.createRuleKey(SonargraphBase.PLUGIN_ISSUE_NAME));
        assertEquals("Sonargraph Integration: Plugin Issue", SonargraphBase.createRuleName(SonargraphBase.PLUGIN_ISSUE_PRESENTATION_NAME));
        assertEquals("plugin-based", SonargraphBase.createRuleCategoryTag(SonargraphBase.PLUGIN_ISSUE_CATEGORY_PRESENTATION_NAME));

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

        int ignoredIssueTypesCount = 0;
        int ignoredErrorOrWarningIssue = 0;
        int scriptIssuesCount = 0;
        int pluginIssuesCount = 0;

        final Set<String> ignoredIssueCategories = new HashSet<>();
        final Set<String> ruleKeysToCheck = new HashSet<>();

        for (final IIssueType nextIssueType : exportMetaData.getIssueTypes().values())
        {
            if (SonargraphBase.ignoreIssueType(nextIssueType))
            {
                ignoredIssueTypesCount++;
                ignoredIssueCategories.add(nextIssueType.getCategory().getName());
            }
            else if (SonargraphBase.isIgnoredErrorOrWarningIssue(nextIssueType))
            {
                ignoredErrorOrWarningIssue++;
            }
            else if (SonargraphBase.isScriptIssue(nextIssueType))
            {
                scriptIssuesCount++;
            }
            else if (SonargraphBase.isPluginIssue(nextIssueType))
            {
                pluginIssuesCount++;
            }

            ruleKeysToCheck.add(SonargraphBase.createRuleKeyToCheck(nextIssueType));
        }

        assertFalse(ruleKeysToCheck.isEmpty());
        final Set<String> referenceIgnored = new HashSet<>(SonargraphBase.IGNORE_ISSUE_TYPE_CATEGORIES);
        referenceIgnored.remove("Session");
        assertEquals("Wrong number of ignored issue categories", referenceIgnored, ignoredIssueCategories);
        assertEquals("Wrong nubmer of ignored issue types", 50, ignoredIssueTypesCount);
        assertEquals("Wrong number of ignored error or warning issue types", 0, ignoredErrorOrWarningIssue);
        assertEquals("Wrong number of script issue types", 0, scriptIssuesCount);
        assertEquals("Wrong number of plugin issue types", 0, pluginIssuesCount);

        final List<Metric<Serializable>> metrics = new ArrayList<>();
        for (final IMetricId nextMetricId : exportMetaData.getMetricIds().values())
        {
            metrics.add(SonargraphBase.createMetric(nextMetricId));
        }

        assertEquals("Wrong number of metrics", 94, metrics.size());
    }

    @Test
    public void testCustomMetrics()
    {
        final ISonargraphSystemController controller = ControllerFactory.createController();
        final Result result = controller.loadSystemReport(new File("./src/test/report/IntegrationSonarqube.xml"));
        assertTrue("Failed to load report", result.isSuccess());

        final Properties customMetrics = new Properties();
        final List<IMetricId> metricIds = controller.createSystemInfoProcessor().getMetricIds();
        for (final IMetricId nextMetricId : metricIds)
        {
            SonargraphBase.addCustomMetric(controller.getSoftwareSystem(), nextMetricId, customMetrics);
        }

        assertEquals("Wrong number of custom metrics", customMetrics.size(), metricIds.size());

        final List<Metric<Serializable>> metrics = SonargraphBase.getCustomMetrics(customMetrics);
        assertEquals("Wrong number of metrics", metrics.size(), metricIds.size());
    }

    @Test
    public void testSimpleModuleMatch()
    {
        final SensorContextTester sensorContextTester = SensorContextTester.create(new File("."));
        sensorContextTester.fileSystem()
                .add(TestInputFileBuilder
                        .create("projectKey", "./src/main/java/com/hello2morrow/sonargraph/integration/sonarqube/SonargraphBase.java")
                        .setLanguage(SonargraphBase.JAVA).build());

        final ISonargraphSystemController controller = ControllerFactory.createController();
        final Result result = controller.loadSystemReport(new File("./src/test/report/IntegrationSonarqube.xml"));
        assertTrue("Failed to load report", result.isSuccess());

        final IModule matched = SonargraphBase.matchModule(controller.getSoftwareSystem(), "Bla", sensorContextTester.fileSystem().baseDir(), false);
        assertNotNull("No match found for 'Bla'", matched);
    }

    @Test
    public void testToLowerCase()
    {
        assertEquals("", SonargraphBase.toLowerCase(null, true));
        assertEquals("", SonargraphBase.toLowerCase("", true));
        assertEquals("i", SonargraphBase.toLowerCase("I", true));
        assertEquals("I", SonargraphBase.toLowerCase("i", false));
        assertEquals("input", SonargraphBase.toLowerCase("Input", true));
        assertEquals("Input", SonargraphBase.toLowerCase("input", false));
    }

    @Test
    public void testTrimDescription()
    {
        assertEquals("", SonargraphBase.trimDescription(null));
        assertEquals("", SonargraphBase.trimDescription(""));
        assertEquals("Test", SonargraphBase.trimDescription("Test"));
    }

    @Test
    public void testGetNonEmptyString()
    {
        try
        {
            SonargraphBase.getNonEmptyString(null);
            fail("This line should not be reached");
        }
        catch (final IllegalArgumentException e)
        {
            //Expected
        }

        try
        {
            SonargraphBase.getNonEmptyString("");
            fail("This line should not be reached");
        }
        catch (final IllegalArgumentException e)
        {
            //Expected
        }

        try
        {
            SonargraphBase.getNonEmptyString(Integer.valueOf(42));
            fail("This line should not be reached");
        }
        catch (final IllegalArgumentException e)
        {
            //Expected
        }
    }
}