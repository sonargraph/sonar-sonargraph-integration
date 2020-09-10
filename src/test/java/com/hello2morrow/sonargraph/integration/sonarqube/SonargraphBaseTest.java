/**
 * SonarQube Sonargraph Integration Plugin
 * Copyright (C) 2016-2020 hello2morrow GmbH
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

import java.io.File;

import org.junit.Test;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.internal.SensorContextTester;

import com.hello2morrow.sonargraph.integration.access.controller.ControllerFactory;
import com.hello2morrow.sonargraph.integration.access.controller.ISonargraphSystemController;
import com.hello2morrow.sonargraph.integration.access.foundation.Result;
import com.hello2morrow.sonargraph.integration.access.model.IModule;
import com.hello2morrow.sonargraph.integration.access.model.Severity;

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
        assertEquals("sg_i.NUMBER_OF_STATEMENTS", SonargraphBase.createMetricKeyFromStandardName("NumberOfStatements"));
    }

    //Checks that issues with different severities are correctly converted to separated SonarQube issue types (rules)
    @Test
    public void testIssueTypeName()
    {
        assertEquals("ThresholdViolation", SonargraphBase.adjustIssueTypeName("ThresholdViolation", Severity.WARNING));
        assertEquals("ThresholdViolationError", SonargraphBase.adjustIssueTypeName("ThresholdViolation", Severity.ERROR));
        assertEquals("ComponentCycleGroup", SonargraphBase.adjustIssueTypeName("ComponentCycleGroup", Severity.WARNING));
        assertEquals("CriticalComponentCycleGroup", SonargraphBase.adjustIssueTypeName("ComponentCycleGroup", Severity.ERROR));
        assertEquals("NamespaceCycleGroup", SonargraphBase.adjustIssueTypeName("NamespaceCycleGroup", Severity.WARNING));
        assertEquals("CriticalNamespaceCycleGroup", SonargraphBase.adjustIssueTypeName("NamespaceCycleGroup", Severity.ERROR));
        assertEquals("DirectoryCycleGroup", SonargraphBase.adjustIssueTypeName("DirectoryCycleGroup", Severity.WARNING));
        assertEquals("CriticalDirectoryCycleGroup", SonargraphBase.adjustIssueTypeName("DirectoryCycleGroup", Severity.ERROR));
        assertEquals("ModuleCycleGroup", SonargraphBase.adjustIssueTypeName("ModuleCycleGroup", Severity.WARNING));
        assertEquals("CriticalModuleCycleGroup", SonargraphBase.adjustIssueTypeName("ModuleCycleGroup", Severity.ERROR));
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

}