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
package com.hello2morrow.sonargraph.integration.sonarqube.foundation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonar.api.resources.Project;

import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphPluginBase.ConfigurationMessageCause;

public final class SonargraphPluginBaseTest
{
    @Test
    public void test()
    {
        final ConfigurationMessageCause configurationError = SonargraphPluginBase.ConfigurationMessageCause.CONFIGURATION_ERROR;
        assertTrue("Missing presentation name", configurationError.getPresentationName().length() > 0);
        assertTrue("Missing standard name", configurationError.getStandardName().length() > 0);
    }

    @Test
    public void testGetBuildUnitName()
    {
        assertEquals(SonargraphPluginBase.UNKNOWN, SonargraphPluginBase.getBuildUnitName(null));
        assertEquals("AlarmClock", SonargraphPluginBase.getBuildUnitName("Workspace:AlarmClock"));
        assertEquals(SonargraphPluginBase.UNKNOWN, SonargraphPluginBase.getBuildUnitName("ModuleName_Without_Workspace_Prefix"));
    }

    @Test
    public void testIsRootParentProject()
    {
        assertFalse(SonargraphPluginBase.isRootParentProject(null));

        final Project singleProject = new Project("Test", null, "Test");
        assertFalse(SonargraphPluginBase.isRootParentProject(singleProject));

        final Project parentProject = new Project("Parent", null, "Parent");
        final Project module = new Project("Module", null, "Module");
        module.setParent(parentProject);
        assertTrue(SonargraphPluginBase.isRootParentProject(parentProject));
        assertFalse(SonargraphPluginBase.isRootParentProject(module));

        final Project parentRoot = new Project("Parent", null, "Parent");
        singleProject.setParent(parentRoot);

        assertFalse(SonargraphPluginBase.isRootParentProject(singleProject));
        assertTrue(SonargraphPluginBase.isRootParentProject(parentRoot));
    }

    @Test
    public void testIsAggregatingProject()
    {
        assertFalse("null cannot be aggregating", SonargraphPluginBase.isAggregatingProject(null));
        final Project project = mock(Project.class);
        when(project.getModules()).thenAnswer(new Answer<List<Project>>()
        {
            @Override
            public List<Project> answer(final InvocationOnMock invocation) throws Throwable
            {
                return null;
            }
        });

        assertFalse(SonargraphPluginBase.isAggregatingProject(project));

        final Project project2 = mock(Project.class);
        when(project2.getModules()).thenAnswer(new Answer<List<Project>>()
        {
            @Override
            public List<Project> answer(final InvocationOnMock invocation) throws Throwable
            {
                return Collections.emptyList();
            }
        });
        assertFalse(SonargraphPluginBase.isAggregatingProject(project2));

        final Project aggregating = mock(Project.class);
        when(aggregating.getModules()).thenAnswer(new Answer<List<Project>>()
        {
            @Override
            public List<Project> answer(final InvocationOnMock invocation) throws Throwable
            {
                return Arrays.asList(new Project("child"));
            }
        });
        assertTrue(SonargraphPluginBase.isAggregatingProject(aggregating));
    }

    @Test
    public void testGetSourceFilePath()
    {
        final String groupParentPath = "AlarmClock/src/main/java/com/h2m/alarm/presentation";
        final String sourceFilePath = "com/h2m/alarm/presentation/AlarmHandler.java";

        assertEquals("AlarmClock/src/main/java/com/h2m/alarm/presentation/AlarmHandler.java",
                SonargraphPluginBase.getSourceFilePath(groupParentPath, sourceFilePath));
        final String inValidSourceFilePath = "com/h2m/alarm/presentation2/AlarmHandler.java";
        assertNull("Not matching directory / source file", SonargraphPluginBase.getSourceFilePath(groupParentPath, inValidSourceFilePath));
    }

    //    No module found in report for Build.Client [SGNG:SGNG:SGNG:Build.Client]
    @Test
    public void testBuildUnitMatchesAnalyzedProject()
    {
        final String buName = "Build.Client";

        assertTrue("Match expected", SonargraphPluginBase.buildUnitMatchesAnalyzedProject(buName, new Project("Build.Client")));
        assertTrue("Match expected",
                SonargraphPluginBase.buildUnitMatchesAnalyzedProject(buName, new Project("SGNG:SGNG:Build.Client", null, buName)));
        assertTrue("Match expected",
                SonargraphPluginBase.buildUnitMatchesAnalyzedProject(buName, new Project("SGNG:SGNG:SGNG:Build Client", null, buName)));

        assertTrue("Match expected", SonargraphPluginBase.buildUnitMatchesAnalyzedProject(buName, new Project("Build.Client", "branch", buName)));
    }
}
