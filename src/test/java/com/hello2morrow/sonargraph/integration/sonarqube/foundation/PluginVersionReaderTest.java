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
package com.hello2morrow.sonargraph.integration.sonarqube.foundation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PluginVersionReaderTest
{
    @Test
    public void initFromResources()
    {
        final String version = PluginVersionReader.getInstance().getVersion();
        assertNotNull("Version must not be null", version);
        assertTrue("Version must not be empty", version.trim().length() > 0);
    }

    @Test
    public void initFromNotExistingProperties()
    {
        final String version = new PluginVersionReader("/notExisting.properties").getVersion();
        assertEquals("Wrong version", PluginVersionReader.UNKNOWN, version);
    }

    @Test
    public void initFromInvalidProperties()
    {
        final String version = new PluginVersionReader("/pluginVersionTest/version.properties").getVersion();
        assertEquals("Wrong version", PluginVersionReader.UNKNOWN, version);
    }
}
