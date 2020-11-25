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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class SonargraphProfile implements BuiltInQualityProfilesDefinition
{
    private static final Logger LOGGER = Loggers.get(SonargraphProfile.class);
    static final List<String> RULE_KEYS = Arrays.asList("ARCHITECTURE_VIOLATION", "CRITICAL_MODULE_CYCLE_GROUP", "CRITICAL_NAMESPACE_CYCLE_GROUP",
            "CRITICAL_COMPONENT_CYCLE_GROUP", "THRESHOLD_VIOLATION_ERROR", "TODO", "DELETE_REFACTORING", "MOVE_REFACTORING",
            "MOVE_RENAME_REFACTORING", "RENAME_REFACTORING", "DUPLICATE_CODE_BLOCK", "QUALITY_GATE_ISSUE");

    private final String profileName;

    public SonargraphProfile()
    {
        this(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);
    }

    protected SonargraphProfile(final String profileName)
    {
        this.profileName = profileName;
    }

    @Override
    public void define(final Context context)
    {
        final BuiltInQualityProfile profile = context.profile(SonargraphBase.JAVA, profileName);
        if (profile == null)
        {
            final NewBuiltInQualityProfile newProfile = context.createBuiltInQualityProfile(profileName, SonargraphBase.JAVA);
            newProfile.setDefault(false);

            final Set<String> activeRuleKeys = new HashSet<>();
            newProfile.activeRules().forEach(ar -> activeRuleKeys.add(ar.ruleKey()));

            for (final String nextRuleKey : getRuleKeys())
            {
                if (!activeRuleKeys.contains(nextRuleKey))
                {
                    newProfile.activateRule(SonargraphBase.SONARGRAPH_PLUGIN_KEY, nextRuleKey);
                }
            }

            newProfile.done();
            LOGGER.info("{}: Profile created", profileName);
        }
    }

    protected List<String> getRuleKeys()
    {
        return RULE_KEYS;
    }
}