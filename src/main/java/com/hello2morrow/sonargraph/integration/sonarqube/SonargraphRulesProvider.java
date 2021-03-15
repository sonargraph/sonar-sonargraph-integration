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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.rule.Severity;

import com.hello2morrow.sonargraph.integration.access.model.IIssue;
import com.hello2morrow.sonargraph.integration.access.model.IIssueCategory;
import com.hello2morrow.sonargraph.integration.access.model.IIssueType;

class SonargraphRulesProvider extends AbstractDataProvider
{
    public static class RuleDto
    {
        private final String key;
        private final String name;
        private final String categoryName;
        private final String[] categoryTag;
        private final String severity;
        private final String description;

        public RuleDto(final String key, final String name, final String categoryName, final String severity,
                final String description, final String... categoryTag)
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

        public String[] getCategoryTags()
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
    private static final String BUILT_IN_RULES_RESOURCE_PATH = "/com/hello2morrow/sonargraph/integration/sonarqube/"
            + PROPERTIES_FILENAME;
    private static final int NUMBER_OF_VALUE_PARTS = 5;

    private SortedProperties customRules;

    SonargraphRulesProvider()
    {
        super(PROPERTIES_FILENAME);
    }

    public SonargraphRulesProvider(final String customRulesDirectoryPath)
    {
        super(PROPERTIES_FILENAME, customRulesDirectoryPath);
    }

    void addRule(final String issueTypeName, final String issueTypePresentationName, final String presentationName,
            final com.hello2morrow.sonargraph.integration.access.model.Severity severity, final IIssueType issueType,
            final Properties ruleProperties)
    {
        if (SonargraphBase.ignoreIssueType(issueType.getCategory().getName()))
        {
            LOGGER.info("Ignoring issue type: {}", issueType.getName());
            return;
        }

        final String name = SonargraphBase.createRuleName(presentationName);
        final IIssueCategory category = issueType.getCategory();
        final String categoryPresentationName = category.getPresentationName();
        final String description = createDescription(issueTypePresentationName, categoryPresentationName);

        final String key;
        final List<String> categoryTags = new ArrayList<>();
        categoryTags.add(SonargraphBase.createRuleCategoryTag(categoryPresentationName));
        if (category.getName().equals(SonargraphBase.SCRIPT_ISSUE_CATEGORY)
                || category.getName().equals(SonargraphBase.PLUGIN_ISSUE_CATEGORY))
        {
            final String issueTag = SonargraphBase.createRuleCategoryTag(issueTypePresentationName);
            categoryTags.add(issueTag);
            final String providerTag = SonargraphBase.createRuleCategoryTag(issueType.getProvider().getName());
            categoryTags.add(providerTag);

            key = SonargraphBase.createRuleKeyToCheck(issueType, severity);
        }
        else
        {
            key = SonargraphBase.createRuleKey(issueTypeName);
        }

        final String convertedSeverity;
        switch (severity)
        {
        case ERROR:
            convertedSeverity = Severity.MAJOR;
            break;
        case WARNING:
            convertedSeverity = Severity.MINOR;
            break;
        case INFO:
            //$FALL-THROUGH$
        case NONE:
            //$FALL-THROUGH$
        default:
            convertedSeverity = Severity.INFO;
            break;
        }

        final StringJoiner propertiesValue = new StringJoiner(SEPARATOR);
        propertiesValue.add(name);
        propertiesValue.add(category.getName());
        propertiesValue.add(categoryTags.stream().collect(Collectors.joining(",")));
        propertiesValue.add(convertedSeverity);
        propertiesValue.add(description);

        ruleProperties.put(key, propertiesValue.toString());
    }

    void addCustomRuleForIssue(final IIssue issue)
    {
        final IIssueType type = issue.getIssueType();
        final String presentationName = type.getPresentationName() + " (" + type.getProvider().getPresentationName()
                + ")";
        addRule(type.getName(), type.getPresentationName(), presentationName, issue.getSeverity(), type, customRules);
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

    File saveCustomRuleProperties(final String comment) throws IOException
    {
        return saveProperties(customRules, new File(getFilePath()), comment);
    }

    List<RuleDto> loadCustomRules()
    {
        final List<RuleDto> result = new ArrayList<>();
        final Properties ruleProperties;
        try
        {
            ruleProperties = loadCustomRulesProperties();
        }
        catch (final IOException e)
        {
            LOGGER.error("Failed to load standard rules from properties file", e);
            return Collections.emptyList();
        }

        convertPropertiesToDtos(result, ruleProperties);
        return result;
    }

    private Properties loadCustomRulesProperties() throws IOException
    {
        customRules = new SortedProperties();

        final String filePath = getFilePath();
        final File customPropertiesFile = new File(filePath);
        if (!customPropertiesFile.exists())
        {
            LOGGER.info("{}: No custom rules file found at '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                    filePath);
            return customRules;
        }
        try (InputStream inputStream = new FileInputStream(filePath))
        {
            customRules.load(inputStream);
            LOGGER.info("{}: Loaded custom rules file '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                    filePath);
        }
        return customRules;
    }

    List<RuleDto> loadStandardRules()
    {
        final List<RuleDto> result = new ArrayList<>();
        Properties standardRules;
        try
        {
            standardRules = loadBuiltInRulesProperties();
        }
        catch (final IOException e)
        {
            LOGGER.error("Failed to load standard rules from properties file", e);
            return Collections.emptyList();
        }

        convertPropertiesToDtos(result, standardRules);
        return result;
    }

    private void convertPropertiesToDtos(final List<RuleDto> result, final Properties ruleProperties)
    {
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
                final String[] tags = convertTags(categoryTag.split(","));

                final String severity = splitValues[index++];
                final String description = splitValues[index];

                final RuleDto rule = new RuleDto(key, name, categoryName, severity, description, tags);
                result.add(rule);
            }
            else
            {
                LOGGER.warn("Unable to create rule from '{}={}'", key, value);
            }
        }
    }

    private String[] convertTags(final String[] rawTags)
    {
        if (rawTags.length == 0)
        {
            return rawTags;
        }

        final String[] tags = new String[rawTags.length];
        for (int i = 0; i < rawTags.length; i++)
        {
            final String next = rawTags[i];
            tags[i] = SonargraphBase.createRuleCategoryTag(next);
        }

        return tags;
    }

    private Properties loadBuiltInRulesProperties() throws IOException
    {
        final Properties properties = new SortedProperties();
        try (InputStream inputStream = SonargraphBase.class.getResourceAsStream(BUILT_IN_RULES_RESOURCE_PATH))
        {
            properties.load(inputStream);
            LOGGER.info("{}: Loaded standard rules file '{}'", SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME,
                    BUILT_IN_RULES_RESOURCE_PATH);
        }

        return properties;
    }
}