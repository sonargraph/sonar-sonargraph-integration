/**
 * SonarQube Sonargraph Integration Plugin
 * Copyright (C) 2016-2017 hello2morrow GmbH
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
package com.hello2morrow.sonargraph.integration.sonarqube.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.Test;
import org.sonar.api.measures.Measure;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Qualifiers;

import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphPluginBase;

public class SonargraphPluginIssuesDetectionTest extends AbstractSonargraphSensorTest
{
    private static final String DUPLICATE_CODE_BLOCK = SonargraphMetrics.createRuleKey("DuplicateCodeBlock");

    @Test
    public void testWorkspaceIssuesNotSaved()
    {
        sensor.analyse(initProject(), sensorContext);
        assertEquals("Wrong number of workspace warnings", 2, sensor.getNumberOfWorkspaceWarnings());
        assertEquals("Wrong number of issues", 0, TestHelper.getNumberOfIssues("WORKSPACE"));
    }

    protected Project initProject()
    {
        final Project project = mock(Project.class);
        doReturn("AlarmClock").when(project).key();
        doReturn("AlarmClock").when(project).name();
        doReturn(Qualifiers.MODULE).when(project).getQualifier();
        doReturn(Boolean.FALSE).when(project).isRoot();
        doReturn(Boolean.TRUE).when(project).isModule();
        return project;
    }

    @Test
    public void testHandleScriptIssues()
    {
        sensor.analyse(initProject(), sensorContext);
        assertEquals("Wrong number of issues", 3, TestHelper.getNumberOfIssues("POTENTIALLY_DEAD_METHOD"));
        assertEquals("Wrong number of issues", 6, TestHelper.getNumberOfIssues(DUPLICATE_CODE_BLOCK));
    }

    @Test
    public void testHandleDuplicateRuleNotActivated()
    {
        rulesProfile = TestHelper.initRulesProfile(DUPLICATE_CODE_BLOCK);
        sensor = new SonargraphSensor(this, rulesProfile, settings, moduleFileSystem, TestHelper.initPerspectives());
        sensor.analyse(initProject(), sensorContext);

        assertEquals("No issues expected", 0, TestHelper.getNumberOfIssues(DUPLICATE_CODE_BLOCK));
    }

    @Test
    public void testNoStructuralDebtCostDefined()
    {
        settings.setProperty(SonargraphPluginBase.COST_PER_INDEX_POINT, -1.0);
        sensor.analyse(initProject(), sensorContext);
        final Map<String, Measure<?>> measures = TestHelper.getMeasures();
        assertNull("Metric not expected", measures.get(SonargraphMetrics.STRUCTURAL_DEBT_COST.getKey()));
    }

    @Override
    protected String getReport()
    {
        return TestHelper.REPORT_PATH_MULTI_MODULES;
    }

    @Override
    protected String getBasePath()
    {
        return "D:/00_repos/sonar-sonargraph-integration/src/test/AlarmClockMain";
    }
}
