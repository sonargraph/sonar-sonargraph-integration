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
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.junit.Test;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.measure.Metric;
import org.sonar.api.batch.measure.MetricFinder;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.Configuration;
import org.sonar.api.profiles.RulesProfile;

public final class SonargraphSensorTest
{
    @Test
    public void testSonargraphSensor()
    {
        final SensorContextTester sensorContextTester = SensorContextTester.create(new File("."));
        sensorContextTester.fileSystem().add(TestInputFileBuilder.create("module1", "myfile.java").build());

        //        final MapSettings settings = new MapSettings();
        //        settings.setProperty(SonargraphBase.RELATIVE_REPORT_PATH, "./src/test/report/IntegrationSonarqube.xml");

        final RulesProfile qualityProfile = RulesProfile.create();
        final MetricFinder metricFinder = new MetricFinder()
        {
            @Override
            public <G extends Serializable> Metric<G> findByKey(final String key)
            {
                return null;
            }

            @Override
            public Collection<Metric<Serializable>> findAll(final List<String> metricKeys)
            {
                return Collections.emptyList();
            }

            @Override
            public Collection<Metric<Serializable>> findAll()
            {
                return Collections.emptyList();
            }
        };

        final SensorDescriptor sensorDescriptor = new SensorDescriptor()
        {
            @Override
            public SensorDescriptor requireProperty(final String... propertyKey)
            {
                return null;
            }

            @Override
            public SensorDescriptor requireProperties(final String... propertyKeys)
            {
                return null;
            }

            @Override
            public SensorDescriptor onlyWhenConfiguration(final Predicate<Configuration> predicate)
            {
                return null;
            }

            @Override
            public SensorDescriptor onlyOnLanguages(final String... languageKeys)
            {
                return null;
            }

            @Override
            public SensorDescriptor onlyOnLanguage(final String languageKey)
            {
                return null;
            }

            @Override
            public SensorDescriptor onlyOnFileType(final Type type)
            {
                return null;
            }

            @Override
            public SensorDescriptor name(final String sensorName)
            {
                return null;
            }

            @Override
            public SensorDescriptor global()
            {
                return null;
            }

            @Override
            public SensorDescriptor createIssuesForRuleRepository(final String... repositoryKey)
            {
                return null;
            }

            @Override
            public SensorDescriptor createIssuesForRuleRepositories(final String... repositoryKeys)
            {
                return null;
            }
        };

        final SonargraphSensor sonargraphSensor = new SonargraphSensor(sensorContextTester.fileSystem(), qualityProfile, metricFinder);
        sonargraphSensor.describe(sensorDescriptor);
        sonargraphSensor.execute(sensorContextTester);
    }
}