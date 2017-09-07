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
import static org.junit.Assert.assertTrue;

import org.junit.Test;
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
