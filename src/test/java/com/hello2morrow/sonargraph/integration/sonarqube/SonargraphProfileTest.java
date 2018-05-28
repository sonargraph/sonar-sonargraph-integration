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

import org.junit.Test;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition.Context;

public final class SonargraphProfileTest
{
    static final class TestProfile implements BuiltInQualityProfilesDefinition
    {
        @Override
        public void define(final Context context)
        {
            //Not used
        }

        static Context createTestContext()
        {
            return new Context();
        }
    }

    @Test
    public void testProfileDefinition()
    {
        final Context context = TestProfile.createTestContext();
        final SonargraphProfile sonargraphProfile = new SonargraphProfile();
        sonargraphProfile.define(context);
        //Call a second time to see if this causes a problem
        sonargraphProfile.define(context);
    }
}