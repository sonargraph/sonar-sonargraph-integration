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

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sonar.api.server.rule.RulesDefinition;

public final class SonargraphRulesTest
{
    static final class TestRules implements RulesDefinition
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
    public void testRulesDefinition()
    {
        final RulesDefinition.Context context = TestRules.createTestContext();
        final SonargraphRules sonargraphRules = new SonargraphRules();
        sonargraphRules.define(context);
        assertTrue(context.repository(SonargraphBase.SONARGRAPH_PLUGIN_KEY).rules().size() > 0);
    }
}