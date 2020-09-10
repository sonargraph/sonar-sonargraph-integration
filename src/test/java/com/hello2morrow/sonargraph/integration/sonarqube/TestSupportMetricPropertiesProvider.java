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

final class TestSupportMetricPropertiesProvider extends SonargraphMetricsProvider
{
    static final String DEFAULT_CUSTOM_METRICS_DIRECTORY = "./src/test/." + SonargraphBase.SONARGRAPH_PLUGIN_KEY;
    private final String directoryPath;

    /**
     * Deletes the content of the {@link #DEFAULT_CUSTOM_METRICS_DIRECTORY}.
     */
    public TestSupportMetricPropertiesProvider()
    {
        this(DEFAULT_CUSTOM_METRICS_DIRECTORY);
        reset();
    }

    /**
     * Does *NOT* delete the content of the given directory path
     *
     * @param directoryPath
     */
    public TestSupportMetricPropertiesProvider(final String directoryPath)
    {
        this.directoryPath = directoryPath;
    }

    @Override
    public String getDirectory()
    {
        return directoryPath;
    }

    private void reset()
    {
        final File dir = new File(getDirectory());
        final File[] files = dir.listFiles();
        if (files != null)
        {
            for (final File next : files)
            {
                next.delete();
            }
        }

        dir.delete();
    }
}
