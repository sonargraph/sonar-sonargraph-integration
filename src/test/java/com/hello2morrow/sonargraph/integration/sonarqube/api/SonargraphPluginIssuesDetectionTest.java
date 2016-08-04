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
package com.hello2morrow.sonargraph.integration.sonarqube.api;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Qualifiers;

public class SonargraphPluginIssuesDetectionTest extends AbstractSonargraphSensorTest
{
    @Test
    public void testWorkspaceIssuesNotSaved()
    {
        final Project project = mock(Project.class);
        doReturn("AlarmClock").when(project).key();
        doReturn("AlarmClock").when(project).name();
        doReturn(Qualifiers.MODULE).when(project).getQualifier();
        doReturn(Boolean.FALSE).when(project).isRoot();
        doReturn(Boolean.TRUE).when(project).isModule();

        sensor.analyse(project, sensorContext);
        assertEquals("Wrong number of workspace warnings", 10, sensor.getNumberOfWorkspaceWarnings());
        assertEquals("Wrong number of issues", 0, TestHelper.getNumberOfIssues("WORKSPACE"));
    }

    @Test
    public void testHandleScriptIssues()
    {
        final Project project = mock(Project.class);
        doReturn("Foundation").when(project).key();
        doReturn("Foundation").when(project).name();
        doReturn(Qualifiers.MODULE).when(project).getQualifier();
        doReturn(Boolean.FALSE).when(project).isRoot();
        doReturn(Boolean.TRUE).when(project).isModule();

        sensor.analyse(project, sensorContext);
        assertEquals("Wrong number of issues", 1, TestHelper.getNumberOfIssues("SCRIPT_BASED"));
    }

    @Override
    protected String getReport()
    {
        return TestHelper.REPORT_PATH_WORKSPACE_ISSUES;
    }

    @Override
    protected String getBasePath()
    {
        return "D:/00_repos/sonar-sonargraph-integration/src/test/AlarmClockMain";
    }
}
