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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public final class SonargraphProfile implements BuiltInQualityProfilesDefinition
{
    private static final Logger LOGGER = Loggers.get(SonargraphProfile.class);
    private static final List<String> ACTIVATE_RULES_WITH_KEY = Arrays.asList("ARCHITECTURE_VIOLATION", "CRITICAL_MODULE_CYCLE_GROUP",
            "CRITICAL_NAMESPACE_CYCLE_GROUP", "CRITICAL_COMPONENT_CYCLE_GROUP", "THRESHOLD_VIOLATION_ERROR", "TODO", "DELETE_REFACTORING",
            "MOVE_REFACTORING", "MOVE_RENAME_REFACTORING", "RENAME_REFACTORING", "SCRIPT_ISSUE", "DUPLICATE_CODE_BLOCK");

    public SonargraphProfile()
    {
        super();
    }

    @Override
    public void define(final Context context)
    {
        final BuiltInQualityProfile profile = context.profile(SonargraphBase.JAVA, SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
        if (profile == null)
        {
            final NewBuiltInQualityProfile newProfile = context.createBuiltInQualityProfile(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                    SonargraphBase.JAVA);
            newProfile.setDefault(false);

            final Set<String> activeRuleKeys = new HashSet<>();
            newProfile.activeRules().forEach(ar -> activeRuleKeys.add(ar.ruleKey()));

            for (final String nextRuleKey : ACTIVATE_RULES_WITH_KEY)
            {
                if (!activeRuleKeys.contains(nextRuleKey))
                {
                    newProfile.activateRule(SonargraphBase.SONARGRAPH_PLUGIN_KEY, nextRuleKey);
                }
            }

            newProfile.done();
            LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Profile created");
        }
    }
}