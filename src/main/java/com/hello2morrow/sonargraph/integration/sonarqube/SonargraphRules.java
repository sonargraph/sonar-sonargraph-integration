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

import java.util.List;

import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import com.hello2morrow.sonargraph.integration.sonarqube.SonargraphRulesProvider.RuleDto;

public final class SonargraphRules implements RulesDefinition
{
    private static final Logger LOGGER = Loggers.get(SonargraphRules.class);
    private final SonargraphRulesProvider rulesProvider;

    public SonargraphRules()
    {
        this(new SonargraphRulesProvider());
    }

    /** Test support */
    SonargraphRules(final SonargraphRulesProvider rulesProvider)
    {
        this.rulesProvider = rulesProvider;
    }

    SonargraphRulesProvider getRulesProvider()
    {
        return rulesProvider;
    }

    @Override
    public void define(final Context context)
    {
        for (final String next : SonargraphBase.SUPPORTED_LANGUAGES)
        {
            final String repositoryKey = getRepositoryKeyForLanguage(next);
            createRepository(context, repositoryKey, next);
        }
    }

    static String getRepositoryKeyForLanguage(final String language)
    {
        if (language.equals(SonargraphBase.JAVA))
        {
            return SonargraphBase.SONARGRAPH_PLUGIN_KEY;
        }

        if (language.equals(SonargraphBase.CSHARP))
        {
            return SonargraphBase.SONARGRAPH_PLUGIN_KEY + "_" + SonargraphBase.CSHARP;
        }

        if (language.equals(SonargraphBase.CPLUSPLUS))
        {
            return SonargraphBase.SONARGRAPH_PLUGIN_KEY + "_" + SonargraphBase.CPLUSPLUS;
        }

        throw new IllegalArgumentException("Unsupported language: " + language);
    }

    private void createRepository(final Context context, final String repositoryKey, final String language)
    {
        LOGGER.debug("Creating repository {} for language {}", repositoryKey, language);
        final NewRepository repository = context.createRepository(repositoryKey, language)
                .setName(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);

        final List<RuleDto> ruleDtos = rulesProvider.loadStandardRules();
        for (final RuleDto ruleDto : ruleDtos)
        {
            if (!SonargraphBase.ignoreIssueType(ruleDto.getCategoryName()))
            {
                createRule(ruleDto.getKey(), ruleDto.getName(), ruleDto.getSeverity(), ruleDto.getDescription(),
                        repository, ruleDto.getCategoryTags());
            }
        }

        final List<RuleDto> customRuleDtos = rulesProvider.loadCustomRules();
        for (final RuleDto ruleDto : customRuleDtos)
        {
            createRule(ruleDto.getKey(), ruleDto.getName(), ruleDto.getSeverity(), ruleDto.getDescription(), repository,
                    ruleDto.getCategoryTags());
        }

        repository.done();

        LOGGER.info("{}: Created {} predefined and {} custom rule(s)",
                SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, ruleDtos.size(), customRuleDtos.size());
    }

    private void createRule(String key, final String name, final String severity, final String description,
            final NewRepository repository, final String[] categoryTag)
    {
        LOGGER.debug("Create rule {} on repository {} ", key, repository.key());
        if (key.length() > 200)
        {
            LOGGER.warn("{}: Key '{}' exceeds max length of 200 characters and is truncated.",
                    SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, key);
            key = key.substring(0, 200);
        }

        final NewRule rule = repository.createRule(key);
        rule.setName(name);
        final String[] tags = new String[categoryTag.length + 1];
        tags[0] = SonargraphBase.SONARGRAPH_RULE_TAG;
        System.arraycopy(categoryTag, 0, tags, 1, categoryTag.length);
        rule.addTags(tags);
        rule.setSeverity(severity);
        rule.setHtmlDescription(description);
    }
}