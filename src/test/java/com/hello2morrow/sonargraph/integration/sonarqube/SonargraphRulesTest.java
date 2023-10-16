/**
 * SonarQube Sonargraph Integration Plugin
 * Copyright (C) 2016-2021 hello2morrow GmbH
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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Context;

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

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testStandardRulesDefinition()
    {
        final RulesDefinition.Context context = new Context();
        final SonargraphRules sonargraphRules = new SonargraphRules(
                new SonargraphRulesProvider(tempFolder.getRoot().getAbsolutePath()));
        sonargraphRules.define(context);
        final int numberOfBuiltinRules = 19;
        assertEquals("Wrong number of rules", numberOfBuiltinRules,
                context.repository(SonargraphBase.SONARGRAPH_PLUGIN_KEY).rules().size());
    }

    @Test
    public void testEmptyCustomRulesDefinition()
    {
        final SonargraphRulesProvider rulesProvider = new SonargraphRulesProvider(
                tempFolder.getRoot().getAbsolutePath());
        assertEquals("No custom rules expected", 0, rulesProvider.loadCustomRules().size());
    }
}