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

import java.util.List;

import org.sonar.api.rule.Severity;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import com.hello2morrow.sonargraph.integration.sonarqube.SonargraphRulesProvider.RuleDto;

public final class SonargraphRules implements RulesDefinition
{
    private static final Logger LOGGER = Loggers.get(SonargraphRules.class);

    public SonargraphRules()
    {
        super();
    }

    @Override
    public void define(final Context context)
    {
        final SonargraphRulesProvider rulesProvider = new SonargraphRulesProvider();
        final List<RuleDto> ruleDtos = rulesProvider.loadRules();
        final NewRepository repository = context.createRepository(SonargraphBase.SONARGRAPH_PLUGIN_KEY, SonargraphBase.JAVA)
                .setName(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME);

        for (final RuleDto ruleDto : ruleDtos)
        {
            if (!SonargraphBase.ignoreIssueType(ruleDto.getCategoryName()))
            {
                final String key = ruleDto.getKey();
                final String name = ruleDto.getName();
                createRule(key, name, ruleDto.getCategoryTag(), ruleDto.getSeverity(), ruleDto.getDescription(), repository);
            }
        }

        final String scriptIssueDescription = createDescription(SonargraphBase.SCRIPT_ISSUE_PRESENTATION_NAME,
                SonargraphBase.SCRIPT_ISSUE_CATEGORY_PRESENTATION_NAME);
        createRule(SonargraphBase.createRuleKey(SonargraphBase.SCRIPT_ISSUE_NAME),
                SonargraphBase.createRuleName(SonargraphBase.SCRIPT_ISSUE_PRESENTATION_NAME),
                SonargraphBase.createRuleCategoryTag(SonargraphBase.SCRIPT_ISSUE_CATEGORY_PRESENTATION_NAME), Severity.MINOR, scriptIssueDescription,
                repository);

        final String pluginIssueDescription = createDescription(SonargraphBase.PLUGIN_ISSUE_PRESENTATION_NAME,
                SonargraphBase.PLUGIN_ISSUE_CATEGORY_PRESENTATION_NAME);
        createRule(SonargraphBase.createRuleKey(SonargraphBase.PLUGIN_ISSUE_NAME),
                SonargraphBase.createRuleName(SonargraphBase.PLUGIN_ISSUE_PRESENTATION_NAME),
                SonargraphBase.createRuleCategoryTag(SonargraphBase.PLUGIN_ISSUE_CATEGORY_PRESENTATION_NAME), Severity.MINOR, pluginIssueDescription,
                repository);

        repository.done();

        LOGGER.info(SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + ": Created " + repository.rules().size() + " predefined rule(s)");
    }

    private void createRule(final String key, final String name, final String categoryTag, final String severity, final String description,
            final NewRepository repository)
    {
        final NewRule rule = repository.createRule(key);
        rule.setName(name);
        rule.addTags(SonargraphBase.SONARGRAPH_RULE_TAG, categoryTag);
        rule.setSeverity(severity);
        rule.setHtmlDescription(description);
    }

    private String createDescription(final String issuePresentationName, final String issueCategoryPresentationName)
    {
        return String.format("Description '%s', category '%s'", issuePresentationName, issueCategoryPresentationName);
    }
}