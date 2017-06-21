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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.After;
import org.junit.Test;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Qualifiers;

import com.hello2morrow.sonargraph.integration.sonarqube.foundation.FixResolutionIssueType;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphPluginBase;

public class SonargraphSensorFixIssuesTest extends AbstractSonargraphSensorTest
{
    @Override
    protected String getReport()
    {
        return TestHelper.REPORT_WITH_FIX_RESOLUTIONS;
    }

    @Override
    protected String getBasePath()
    {
        return "D:/00_repos/sonar-sonargraph-integration/src/test/AlarmClockMain";
    }

    @After
    public void after() throws Throwable
    {
        final Throwable exception = sensor.getSensorExecutionException();
        if (exception != null)
        {
            throw exception;
        }
    }

    @Test
    public void testFixIssues()
    {
        settings.setProperty(SonargraphPluginBase.REPORT_PATH_OLD, TestHelper.REPORT_WITH_FIX_RESOLUTIONS);

        final Project project = mock(Project.class);
        doReturn("AlarmClock").when(project).key();
        doReturn("AlarmClock").when(project).name();
        doReturn(Qualifiers.MODULE).when(project).getQualifier();
        doReturn(Boolean.FALSE).when(project).isRoot();
        doReturn(Boolean.TRUE).when(project).isModule();

        sensor.analyse(project, sensorContext);
        assertTrue(sensor.getProcessReportResult().toString(), sensor.getProcessReportResult().isSuccess());

        assertEquals("Wrong number of fix issues", 3,
                TestHelper.getNumberOfIssues(SonargraphMetrics.createRuleKey(FixResolutionIssueType.FIX_RESOLUTION_RULE)));
    }
}
