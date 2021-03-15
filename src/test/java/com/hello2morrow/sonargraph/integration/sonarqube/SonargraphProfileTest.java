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

import java.util.Map;

import org.junit.Test;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition.BuiltInQualityProfile;
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
        {
            final BuiltInQualityProfilesDefinition sonargraphProfile = new StandardSonargraphProfileJava();
            sonargraphProfile.define(context);
            //Call a second time to see if this causes a problem
            sonargraphProfile.define(context);
        }

        {
            final Map<String, BuiltInQualityProfile> javaProfiles = context.profilesByLanguageAndName().get(SonargraphBase.JAVA);
            assertNotNull("Missing Java profiles", javaProfiles);
            assertEquals("Wrong number of profiles", 1, javaProfiles.size());
            final BuiltInQualityProfile sonargraphProfile = javaProfiles.get(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
            assertNotNull("Missing Sonargraph profile", sonargraphProfile);
            assertEquals("Wrong number of rules", StandardSonargraphProfileJava.STANDARD_RULE_KEYS.size(), sonargraphProfile.rules().size());
        }
    }

    @Test
    public void testStrictProfileDefinition()
    {
        final Context context = TestProfile.createTestContext();
        {
            final StrictSonargraphProfileJava sonargraphStrictProfile = new StrictSonargraphProfileJava();
            sonargraphStrictProfile.define(context);
            //Call a second time to see if this causes a problem
            sonargraphStrictProfile.define(context);
        }

        {
            final Map<String, BuiltInQualityProfile> javaProfiles = context.profilesByLanguageAndName().get(SonargraphBase.JAVA);
            assertNotNull("Missing Java profiles", javaProfiles);
            assertEquals("Wrong number of profiles", 1, javaProfiles.size());
            final BuiltInQualityProfile sonargraphProfile2 = javaProfiles.get(StrictSonargraphProfileJava.NAME);
            assertNotNull("Missing Sonargraph (Strict) profile", sonargraphProfile2);
            assertEquals("Wrong number of rules",
                    AbstractSonargraphProfile.STANDARD_RULE_KEYS.size() + AbstractSonargraphProfile.STRICT_RULE_KEYS.size(),
                    sonargraphProfile2.rules().size());
        }
    }
}