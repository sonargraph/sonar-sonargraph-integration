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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.sonar.api.measures.Measure;
import org.sonar.api.measures.Metric;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Qualifiers;
import org.sonar.plugins.java.Java;

import com.hello2morrow.sonargraph.integration.access.foundation.OperationResult;
import com.hello2morrow.sonargraph.integration.access.model.IMetricId;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphMetrics;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphPluginBase;

public class SonargraphSensorTest extends AbstractSonargraphSensorTest
{
    @Override
    protected String getReport()
    {
        return TestHelper.REPORT_PATH_MULTI_MODULES;
    }

    @Override
    protected String getBasePath()
    {
        return "D:/00_repos/00_e4-sgng/com.hello2morrow.sonargraph.integration.sonarqube/src/test/AlarmClockMain_ant/AlarmClock/src/main/java";
    }

    @After
    public void after() throws Throwable
    {
        final Throwable exception = sensor.getSensorExecutionException();
        if (exception != null)
        {
            throw exception;
        }
    }

    @Test
    public void testShouldExecuteOnProject()
    {
        final Project project = new Project("hello2morrow:AlarmClock", "", "AlarmClock");
        project.setLanguage(new Java(settings));
        assertTrue(sensor.shouldExecuteOnProject(project));

        final Project module = new Project("hello2morrow:Foundation", "", "Foundation");
        module.setParent(project);
        module.setLanguage(new Java(settings));
        assertTrue(sensor.shouldExecuteOnProject(project));
        assertTrue(sensor.shouldExecuteOnProject(module));
    }

    @Test
    public void testShouldNotExecuteOnProject()
    {
        final RulesProfile rulesProfile = RulesProfile.create(SonargraphPluginBase.PLUGIN_KEY, "JAVA");
        this.sensor = new SonargraphSensor(null, rulesProfile, settings, moduleFileSystem, TestHelper.initPerspectives());
        final Project project = new Project("hello2morrow:AlarmClock", "", "AlarmClock");
        project.setLanguage(new Java(settings));
        Assert.assertFalse("Sensor should not execute because neither sonargraph rules are active, nor alerts are defined for sonargraph rules",
                this.sensor.shouldExecuteOnProject(project));
    }

    @Test
    public void testShouldNotExecuteOnProjectWithoutReport()
    {
        final Project project = new Project("hello2morrow:AlarmClock", "", "AlarmClock");
        project.setLanguage(new Java(settings));
        final Project module = new Project("hello2morrow:Foundation", "", "Foundation");
        module.setParent(project);
        module.setLanguage(new Java(settings));
        settings.setProperty(SonargraphPluginBase.REPORT_PATH_OLD, "c:/fantasyPath");
        Assert.assertFalse("Sensor must not execute on aggregating project", sensor.shouldExecuteOnProject(project));
        Assert.assertFalse("Sensor must not execute on module without report", sensor.shouldExecuteOnProject(module));
    }

    @Test
    public void testAnalyseSystemMetricsOfReportWithMultipleModules()
    {
        settings.setProperty(SonargraphPluginBase.REPORT_PATH_OLD, TestHelper.REPORT_PATH_MULTI_MODULES);

        final Project project = mock(Project.class);
        doReturn("hello2morrow:AlarmClockMain").when(project).key();
        doReturn("AlarmClockMain").when(project).name();
        doReturn(Qualifiers.PROJECT).when(project).getQualifier();
        doReturn(Boolean.TRUE).when(project).isRoot();

        sensor.analyse(project, sensorContext);

        final String coreTotalLines = SonargraphMetrics.createMetricKeyFromStandardName("CoreTotalLines");
        assertTrue("Metric not found!", sonargraphRulesRepository.getLoadedMetrics().containsKey(coreTotalLines));
        assertTrue("Successfully analyzed report!", true);
        final Map<String, Measure<?>> measures = TestHelper.getMeasures();
        assertEquals("Wrong value for system metric 'total lines'", 454,
                measures.get(sonargraphRulesRepository.getLoadedMetrics().get(coreTotalLines).key()).getValue().intValue());

        assertEquals("Wrong value for Highest NCCD", 1.2, measures.get(SonargraphMetrics.MAX_MODULE_NCCD.key()).getValue().doubleValue(), 0.02);

        assertEquals("Wrong value for critical unresolved issues", 25,
                measures.get(SonargraphMetrics.NUMBER_OF_CRITICAL_ISSUES_WITHOUT_RESOLUTION.key()).getValue().intValue());

        assertEquals("Wrong structural debt cost", 238, measures.get(SonargraphMetrics.STRUCTURAL_DEBT_COST.key()).getValue().doubleValue(), 0.2);
    }

    @Test
    public void testAnalyseSystemMetricsOfReportWithSingleModule()
    {
        settings.setProperty(SonargraphPluginBase.REPORT_PATH_OLD, TestHelper.REPORT_CRM);

        final Project project = mock(Project.class);
        doReturn("com.hello2morrow:crm-domain-example").when(project).key();
        doReturn("crm-domain-example").when(project).name();
        doReturn(Qualifiers.MODULE).when(project).getQualifier();
        doReturn(Boolean.TRUE).when(project).isRoot();

        sensor.analyse(project, sensorContext);
        assertTrue(sensor.getProcessReportResult().toString(), sensor.getProcessReportResult().isSuccess());
        final String coreStatements = SonargraphMetrics.createMetricKeyFromStandardName("CoreStatements");
        assertTrue("Metric not found!", sonargraphRulesRepository.getLoadedMetrics().containsKey(coreStatements));
        assertTrue("Successfully analyzed report!", true);
        assertEquals("Wrong value for system metric 'core statements'", 2873,
                TestHelper.getMeasures().get(sonargraphRulesRepository.getLoadedMetrics().get(coreStatements).key()).getValue().intValue());
    }

    @Test
    public void testAnalyseModuleMetrisOfReport()
    {
        settings.setProperty(SonargraphPluginBase.REPORT_PATH_OLD, TestHelper.REPORT_PATH_MULTI_MODULES);

        final Project project = mock(Project.class);
        doReturn("AlarmClock").when(project).key();
        doReturn("AlarmClock").when(project).name();
        doReturn(Qualifiers.MODULE).when(project).getQualifier();
        doReturn(Boolean.FALSE).when(project).isRoot();
        doReturn(Boolean.TRUE).when(project).isModule();

        sensor.analyse(project, sensorContext);
        assertTrue(sensor.getProcessReportResult().toString(), sensor.getProcessReportResult().isSuccess());
        final String coreRacd = SonargraphMetrics.createMetricKeyFromStandardName("CoreRacd");
        assertTrue("Metric not found!", sonargraphRulesRepository.getLoadedMetrics().containsKey(coreRacd));
        assertTrue("Successfully analyzed report!", true);

        final Map<String, Measure<?>> measures = TestHelper.getMeasures();
        assertEquals("Wrong value for module metric 'coreRacd' (double values are rounded to first decimal place", 40.8,
                measures.get(sonargraphRulesRepository.getLoadedMetrics().get(coreRacd).key()).getValue().floatValue(), 0.01);

        assertEquals("Wrong value for module issues", 22, measures.get(SonargraphMetrics.NUMBER_OF_CRITICAL_ISSUES_WITHOUT_RESOLUTION.key())
                .getValue().intValue());

        assertTrue("Unassigned components percent must be > 0", measures.get(SonargraphMetrics.UNASSIGNED_COMPONENTS_PERCENT.key()).getValue()
                .doubleValue() > 0);
        assertTrue("Violating components percent must be > 0", measures.get(SonargraphMetrics.VIOLATING_COMPONENTS_PERCENT.key()).getValue()
                .doubleValue() > 0);
    }

    @Test
    public void testAnalyseSingleModuleProject()
    {
        settings.setProperty(SonargraphPluginBase.REPORT_PATH_OLD, TestHelper.REPORT_CRM);
        final Project project = mock(Project.class);
        doReturn("com.hello2morrow:crm-domain-example").when(project).key();
        doReturn("crm-domain-example").when(project).name();
        doReturn(Qualifiers.MODULE).when(project).getQualifier();
        doReturn(Boolean.TRUE).when(project).isRoot();

        sensor.analyse(project, sensorContext);
        assertTrue(sensor.getProcessReportResult().toString(), sensor.getProcessReportResult().isSuccess());

        final Map<String, Measure<?>> measures = TestHelper.getMeasures();
        assertEquals("Wrong number of issues", 25, measures.get(SonargraphMetrics.NUMBER_OF_CRITICAL_ISSUES_WITHOUT_RESOLUTION.key()).getValue()
                .intValue());

        final int isArchitectureEnabled = measures.get(SonargraphMetrics.ARCHITECTURE_FEATURE_AVAILABLE.key()).getIntValue();
        assertEquals("Architecture feature must enabled", 1, isArchitectureEnabled);

        final int isVirtualModelsEnabled = measures.get(SonargraphMetrics.VIRTUAL_MODEL_FEATURE_AVAILABLE.key()).getIntValue();
        assertEquals("Virtual models feature must be enabled", 1, isVirtualModelsEnabled);

        final String metricKey = SonargraphMetrics.createMetricKeyFromStandardName(IMetricId.StandardName.CORE_VIOLATIONS_PARSER_DEPENDENCIES
                .getStandardName());
        final Metric<?> metric = sonargraphRulesRepository.getLoadedMetrics().get(metricKey);
        assertEquals("Wrong number of architecture violations", 19, measures.get(metric.key()).getValue().intValue());
    }

    @Test
    public void testAnalyseExplorerReport()
    {
        settings.setProperty(SonargraphPluginBase.REPORT_PATH_OLD, TestHelper.REPORT_EXPLORER);

        final Project project = mock(Project.class);
        doReturn("AlarmClock").when(project).key();
        doReturn("AlarmClock").when(project).name();
        doReturn(Qualifiers.MODULE).when(project).getQualifier();
        doReturn(Boolean.FALSE).when(project).isRoot();
        doReturn(Boolean.TRUE).when(project).isModule();

        sensor.analyse(project, sensorContext);
        assertTrue(sensor.getProcessReportResult().toString(), sensor.getProcessReportResult().isSuccess());

        //check for disabled features
        final Map<String, Measure<?>> measures = TestHelper.getMeasures();
        assertNull("Architecture feature must be disabled", measures.get(SonargraphMetrics.ARCHITECTURE_FEATURE_AVAILABLE.key()));
        assertNull("Virtual model feature must be disabled", measures.get(SonargraphMetrics.VIRTUAL_MODEL_FEATURE_AVAILABLE.key()));
    }

    @Test
    public void testErrorIfReportDoesNotExist()
    {
        settings.setProperty(SonargraphPluginBase.REPORT_PATH_OLD, "./notExistingReport.xml");

        final Project project = mock(Project.class);
        sensor.analyse(project, sensorContext);
        assertFalse(sensor.getProcessReportResult().toString(), sensor.getProcessReportResult().isSuccess());
    }

    @Test
    public void testErrorIfReportIsCorrupt()
    {
        settings.setProperty(SonargraphPluginBase.REPORT_PATH_OLD, "./src/test/report/Corrupt_Report.xml");

        final Project project = mock(Project.class);
        sensor.analyse(project, sensorContext);
        assertFalse(sensor.getProcessReportResult().toString(), sensor.getProcessReportResult().isSuccess());
    }

    @Test
    public void testMetricValuesNotPresent()
    {
        final Project project = mock(Project.class);
        doReturn("hello2morrow:AlarmClockMain").when(project).key();
        doReturn("AlarmClockMain").when(project).name();
        doReturn(Qualifiers.PROJECT).when(project).getQualifier();
        doReturn(Boolean.TRUE).when(project).isRoot();

        initSensor("./src/test/resources/metadata_reduced");
        sensor.analyse(project, sensorContext);
        assertTrue(sensor.getProcessReportResult().toString(), sensor.getProcessReportResult().isSuccess());

        final Map<String, Measure<?>> measures = TestHelper.getMeasures();
        assertEquals("Wrong number of issues", 25, measures.get(SonargraphMetrics.NUMBER_OF_CRITICAL_ISSUES_WITHOUT_RESOLUTION.key()).getValue()
                .intValue());
        assertEquals("Wrong number of lines of code", 262, measures.get(SonargraphMetrics.createMetricKeyFromStandardName("CoreLinesOfCode"))
                .getValue().intValue());

        assertNull("Metric value not expected", measures.get(SonargraphMetrics.createMetricKeyFromStandardName("CoreComponents")));
    }

    @Test
    public void testNoModulesInReport()
    {
        final Project project = mock(Project.class);
        doReturn("hello2morrow:AlarmClockMain").when(project).key();
        doReturn("AlarmClockMain").when(project).name();
        doReturn(Qualifiers.PROJECT).when(project).getQualifier();
        doReturn(Boolean.TRUE).when(project).isRoot();

        settings.setProperty(SonargraphPluginBase.REPORT_PATH_OLD, "./src/test/report/AlarmClockMain_withoutModules.xml");
        sensor.analyse(project, sensorContext);

        final OperationResult result = sensor.getProcessReportResult();
        assertTrue("Report with no modules must contain warning: " + result.toString(), result.containsWarning());
    }
}
