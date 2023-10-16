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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

abstract class AbstractSonargraphProfile implements BuiltInQualityProfilesDefinition
{
    private static final Logger LOGGER = Loggers.get(AbstractSonargraphProfile.class);

    protected static final List<String> STANDARD_RULE_KEYS = Arrays.asList("ARCHITECTURE_VIOLATION", "ARCHITECTURE_DEPRECATION",
            "CRITICAL_MODULE_CYCLE_GROUP", "CRITICAL_NAMESPACE_CYCLE_GROUP", "CRITICAL_COMPONENT_CYCLE_GROUP",
            "THRESHOLD_VIOLATION_ERROR", "TODO", "DELETE_REFACTORING", "MOVE_REFACTORING", "MOVE_RENAME_REFACTORING",
            "RENAME_REFACTORING", "DUPLICATE_CODE_BLOCK", "QUALITY_GATE_ISSUE");
    protected static final List<String> STRICT_RULE_KEYS = Arrays.asList("MODULE_CYCLE_GROUP", "NAMESPACE_CYCLE_GROUP",
            "COMPONENT_CYCLE_GROUP", "THRESHOLD_VIOLATION");

    private final String profileName;
    private final String language;

    protected AbstractSonargraphProfile(final String profileName, final String language)
    {
        this.profileName = profileName;
        this.language = language;
    }

    protected final String getProfileName()
    {
        return profileName;
    }

    protected abstract List<String> getRuleKeys();

    @Override
    public final void define(final Context context)
    {
        final BuiltInQualityProfile profile = context.profile(language, profileName);
        if (profile == null)
        {
            final NewBuiltInQualityProfile newProfile = context.createBuiltInQualityProfile(profileName, language);
            newProfile.setDefault(false);

            final Set<String> activeRuleKeys = new HashSet<>();
            for (final NewBuiltInActiveRule activeRule : newProfile.activeRules())
            {
                LOGGER.debug("Already activated rule key: {}", activeRule.ruleKey());
                activeRuleKeys.add(activeRule.ruleKey());
            }

            for (final String nextRuleKey : getRuleKeys())
            {
                if (!activeRuleKeys.contains(nextRuleKey))
                {
                    final String repoKey = SonargraphRules.getRepositoryKeyForLanguage(language);
                    LOGGER.info("activating rule {} in repo {}", nextRuleKey, repoKey);
                    newProfile.activateRule(repoKey, nextRuleKey);
                }
            }

            newProfile.done();
            LOGGER.info("{}: Profile created", profileName);
        }
    }
}