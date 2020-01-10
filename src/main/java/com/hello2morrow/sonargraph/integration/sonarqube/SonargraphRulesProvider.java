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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.rule.Severity;

import com.hello2morrow.sonargraph.integration.access.model.IIssueCategory;
import com.hello2morrow.sonargraph.integration.access.model.IIssueType;

class SonargraphRulesProvider
{
    public static class RuleDto
    {
        final String key;
        final String name;
        private final String categoryName;
        final String categoryTag;
        final String severity;
        final String description;

        public RuleDto(final String key, final String name, final String categoryName, final String categoryTag, final String severity,
                final String description)
        {
            this.key = key;
            this.name = name;
            this.categoryName = categoryName;
            this.categoryTag = categoryTag;
            this.severity = severity;
            this.description = description;
        }

        public String getKey()
        {
            return key;
        }

        public String getName()
        {
            return name;
        }

        public String getCategoryName()
        {
            return categoryName;
        }

        public String getCategoryTag()
        {
            return categoryTag;
        }

        public String getSeverity()
        {
            return severity;
        }

        public String getDescription()
        {
            return description;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SonargraphRulesProvider.class);
    private static final String SEPARATOR = "|";
    private static final String PROPERTIES_FILENAME = "SonargraphRules.properties";
    private static final String BUILT_IN_RULES_RESOURCE_PATH = "/com/hello2morrow/sonargraph/integration/sonarqube/" + PROPERTIES_FILENAME;
    private static final int NUMBER_OF_VALUE_PARTS = 5;

    public SonargraphRulesProvider()
    {
        super();
    }

    public void addRule(final IIssueType issueType, final Properties properties)
    {
        if (SonargraphBase.ignoreIssueType(issueType.getCategory().getName()))
        {
            LOGGER.info("Ignoring issue type: {}", issueType.getName());
            return;
        }

        final String key = SonargraphBase.createRuleKey(issueType.getName());
        final String name = SonargraphBase.createRuleName(issueType.getPresentationName());
        final IIssueCategory category = issueType.getCategory();
        final String categoryPresentationName = category.getPresentationName();
        final String issuePresentationName = issueType.getDescription().length() > 0 ? issueType.getDescription() : issueType.getPresentationName();
        final String description = createDescription(issuePresentationName, categoryPresentationName);
        final String categoryTag = SonargraphBase.createRuleCategoryTag(categoryPresentationName);

        final String severity;
        switch (issueType.getSeverity())
        {
        case ERROR:
            severity = Severity.MAJOR;
            break;
        case WARNING:
            severity = Severity.MINOR;
            break;
        case INFO:
            //$FALL-THROUGH$
        case NONE:
            //$FALL-THROUGH$
        default:
            severity = Severity.INFO;
            break;
        }

        final StringJoiner propertiesValue = new StringJoiner(SEPARATOR);
        propertiesValue.add(name);
        propertiesValue.add(category.getName());
        propertiesValue.add(categoryTag);
        propertiesValue.add(severity);
        propertiesValue.add(description);

        properties.put(key, propertiesValue.toString());
    }

    private String createDescription(final String issuePresentationName, final String issueCategoryPresentationName)
    {
        return String.format("Description '%s', category '%s'", issuePresentationName, issueCategoryPresentationName);
    }

    void save(final Properties rules, final File targetDirectory, final String comment) throws IOException
    {
        final File propertiesFile = new File(targetDirectory, PROPERTIES_FILENAME);
        try (FileWriter writer = new FileWriter(propertiesFile))
        {
            rules.store(writer, comment);
        }
    }

    public List<RuleDto> loadRules()
    {
        final List<RuleDto> result = new ArrayList<>();
        final Properties ruleProperties;
        try
        {
            ruleProperties = load();
        }
        catch (final IOException e)
        {
            LOGGER.error("Failed to load standard rules from properties file", e);
            return Collections.emptyList();
        }

        for (final Entry<Object, Object> nextEntry : ruleProperties.entrySet())
        {
            final String key = SonargraphBase.getNonEmptyString(nextEntry.getKey());
            final String value = SonargraphBase.getNonEmptyString(nextEntry.getValue());

            final String[] splitValues = value.split("\\" + SEPARATOR);
            if (splitValues.length == NUMBER_OF_VALUE_PARTS)
            {
                int index = 0;
                final String name = splitValues[index++];
                final String categoryName = splitValues[index++];
                final String categoryTag = splitValues[index++];
                final String severity = splitValues[index++];
                final String description = splitValues[index++];

                final RuleDto rule = new RuleDto(key, name, categoryName, categoryTag, severity, description);
                result.add(rule);
            }
            else
            {
                LOGGER.warn("Unable to create rule from '{}={}'", key, value);
            }

        }

        return result;
    }

    private Properties load() throws IOException
    {
        final Properties standardMetrics = new Properties();
        try (InputStream inputStream = SonargraphBase.class.getResourceAsStream(BUILT_IN_RULES_RESOURCE_PATH))
        {
            standardMetrics.load(inputStream);
            LOGGER.info("{}: Loaded standard rules file '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME, BUILT_IN_RULES_RESOURCE_PATH);
        }

        return standardMetrics;
    }
}