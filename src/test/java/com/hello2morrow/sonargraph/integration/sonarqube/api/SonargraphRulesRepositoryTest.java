/**
 * SonarQube Sonargraph Integration Plugin
 * Copyright (C) 2016-2017 hello2morrow GmbH
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
package com.hello2morrow.sonargraph.integration.sonarqube.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.After;
import org.junit.Test;
import org.sonar.api.config.Settings;
import org.sonar.api.measures.Metric;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Repository;
import org.sonar.plugins.java.Java;

import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphPluginBase;

public class SonargraphRulesRepositoryTest
{
    private static final double TOLERANCE = 0.0001;
    private static final String META_DATA_PATH = "./src/test/resources/metadata";
    private static final String META_DATA_MERGED_PATH = "./src/test/resources/metadata_merge";
    private static final String META_DATA_CORRUPT_PATH = "./src/test/resources/metadata_corrupt";

    private SonargraphRulesRepository m_rulesDefinition;

    @After
    public void clear()
    {
        if (m_rulesDefinition != null)
        {
            m_rulesDefinition.clearLoadedMetrics();
        }
    }

    @Test
    public void testCreateRulesFromPlugin()
    {
        m_rulesDefinition = new SonargraphRulesRepository(TestHelper.initSettings());
        verifyRules(m_rulesDefinition);
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testCreateRulesFromDirectory()
    {
        final SonargraphRulesRepository rulesDefinition = new SonargraphRulesRepository(TestHelper.initSettings(META_DATA_PATH));
        verifyRules(rulesDefinition);

        final List<Metric> metrics = rulesDefinition.getMetrics();

        metrics.stream().forEach(m -> System.out.println(m.getKey()));
        {
            final String relCyclicityId = SonargraphMetrics.createMetricKeyFromStandardName("JavaRelativeCyclicityPackages");
            final Optional<Metric> relCyclicityOpt = metrics.stream().filter(m -> m.getKey().equals(relCyclicityId)).findFirst();
            assertTrue("Metric not found", relCyclicityOpt.isPresent());
            final Metric relCyclicityMetric = relCyclicityOpt.get();
            assertEquals("Wrong bestValue", 0.0, relCyclicityMetric.getBestValue(), TOLERANCE);
            assertNull("Wrong worstValue", relCyclicityMetric.getWorstValue());
            assertEquals("Wrong direction", Metric.DIRECTION_WORST, relCyclicityMetric.getDirection().intValue());
        }
        {
            final String componentsId = SonargraphMetrics.createMetricKeyFromStandardName("CoreComponents");
            final Optional<Metric> componentsOpt = metrics.stream().filter(m -> m.getKey().equals(componentsId)).findFirst();
            assertTrue("Metric not found", componentsOpt.isPresent());
            final Metric components = componentsOpt.get();
            assertNull("Wrong bestValue", components.getBestValue());
            assertNull("Wrong worstValue", components.getWorstValue());
            assertEquals("Wrong direction", Metric.DIRECTION_NONE, components.getDirection().intValue());
        }
        {
            final String phantasticId = SonargraphMetrics.createMetricKeyFromStandardName("Phantastic");
            final Optional<Metric> phantasticOpt = metrics.stream().filter(m -> m.getKey().equals(phantasticId)).findFirst();
            assertTrue("Metric not found", phantasticOpt.isPresent());
            final Metric phantastic = phantasticOpt.get();
            assertEquals("Wrong bestValue", 0.0, phantastic.getBestValue(), TOLERANCE);
            assertEquals("Wrong worstValue", 1.0, phantastic.getWorstValue(), TOLERANCE);
            assertEquals("Wrong direction", Metric.DIRECTION_WORST, phantastic.getDirection().intValue());
        }
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testLoadCorruptConfigurationFromPath()
    {
        m_rulesDefinition = new SonargraphRulesRepository(TestHelper.initSettings(META_DATA_CORRUPT_PATH));
        final List<Metric> metrics = m_rulesDefinition.getMetrics();
        assertEquals("Wrong number of metrics", 65, metrics.size());
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testMergeRulesFromDirectory()
    {
        m_rulesDefinition = new SonargraphRulesRepository(TestHelper.initSettings(META_DATA_MERGED_PATH));

        final List<Metric> metrics = m_rulesDefinition.getMetrics();

        assertTrue("Common metric not found",
                metrics.stream().filter(metric -> metric.getKey().equals(SonargraphPluginBase.ABBREVIATION + "CORE_LINES_OF_CODE")).findFirst()
                        .isPresent());
        assertTrue("Individual metric of first file not found",
                metrics.stream().filter(metric -> metric.getKey().equals(SonargraphPluginBase.ABBREVIATION + "UNUSED_TYPES")).findFirst().isPresent());
        assertTrue("Individual metric of second file not found",
                metrics.stream().filter(metric -> metric.getKey().equals(SonargraphPluginBase.ABBREVIATION + "UNUSED_TYPES_2")).findFirst()
                        .isPresent());

        final List<Metric> reloaded = m_rulesDefinition.getMetrics();
        assertEquals("Metrics must be the same", metrics, reloaded);
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testReloadRules()
    {
        final Settings settings = TestHelper.initSettings();
        m_rulesDefinition = new SonargraphRulesRepository(settings);
        verifyRules(m_rulesDefinition);
        final List<Metric> original = m_rulesDefinition.getMetrics();

        settings.setProperty(SonargraphPluginBase.METADATA_PATH, META_DATA_MERGED_PATH);
        final List<Metric> reloaded = m_rulesDefinition.getMetrics();

        assertFalse("Metrics must not be the same after reload and changed path", original.equals(reloaded));
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testReloadRulesFromPlugin()
    {
        final Settings settings = TestHelper.initSettings();
        m_rulesDefinition = new SonargraphRulesRepository(settings);
        verifyRules(m_rulesDefinition);
        final List<Metric> original = m_rulesDefinition.getMetrics();

        settings.setProperty(SonargraphPluginBase.METADATA_PATH, "");
        final List<Metric> reloaded = m_rulesDefinition.getMetrics();

        assertEquals("Metrics must not be changed if meta-data path has been removed", original, reloaded);
    }

    @Test
    public void testCreateRulesFromInvalidFilePath()
    {
        verifyRules(new SonargraphRulesRepository(TestHelper.initSettings("./not_existing_path")));
    }

    @Test
    public void testCreateRulesWithInvalidResourcePath()
    {
        final SonargraphRulesRepository rulesDefinition = new SonargraphRulesRepository(TestHelper.initSettings(), "/invalid/resource/path.xml");
        final RulesDefinition.Context context = new RulesDefinition.Context();
        rulesDefinition.define(context);

        final Repository repository = context.repository(SonargraphPluginBase.PLUGIN_KEY);
        assertNull("No repository created. Wrong default resource path is a fatal error.", repository);
    }

    @Test
    public void testLoadedMetrics()
    {
        m_rulesDefinition = new SonargraphRulesRepository(TestHelper.initSettings());
        assertTrue("No metrics expected", m_rulesDefinition.getLoadedMetrics().isEmpty());

        m_rulesDefinition.getMetrics();
        assertFalse("Metrics must have been loaded", m_rulesDefinition.getLoadedMetrics().isEmpty());
    }

    private void verifyRules(final RulesDefinition rulesDefinition)
    {
        final RulesDefinition.Context context = new RulesDefinition.Context();
        rulesDefinition.define(context);

        final Repository repository = context.repository(SonargraphPluginBase.PLUGIN_KEY);
        assertNotNull(SonargraphPluginBase.PLUGIN_KEY, repository);
        assertEquals(Java.KEY, repository.language());
        final List<RulesDefinition.Rule> rules = repository.rules();
        assertEquals("Wrong number of default rules", 58, rules.size());

        final String[] ruleNames = new String[] { "UnresolvedRequiredArtifact", "ArchitectureViolation", "DuplicateCodeBlock",
                "WorkspaceDependencyProblematic", "ThresholdViolation", };
        for (final String ruleName : ruleNames)
        {
            assertNotNull("Expected rule '" + ruleName + "' not found", repository.rule(SonargraphMetrics.createRuleKey(ruleName)));
        }
    }
}