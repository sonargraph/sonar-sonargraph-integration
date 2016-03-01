/**
 * SonarQube Sonargraph Integration Plugin
 * Copyright (C) 2016 hello2morrow GmbH
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The version is dynamically generated during build and this class extracts it from the generated properties file.
 * @author Ingmar
 */
public class PluginVersionReader
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginVersionReader.class);

    private static final String VERSION_PROPERTIES = "/com/hello2morrow/sonargraph/integration/sonarqube/version.properties";
    private static final PluginVersionReader INSTANCE = new PluginVersionReader();

    static final String UNKNOWN = "unknown";

    private static String version;

    /**
     * Only to be used in JUnit tests
     */
    PluginVersionReader(final String versionProperties)
    {
        version = loadVersionProperty(versionProperties);
    }

    private PluginVersionReader()
    {
        version = loadVersionProperty(VERSION_PROPERTIES);
    }

    private String loadVersionProperty(final String properties)
    {
        try (InputStream is = getClass().getResourceAsStream(properties))
        {
            final Properties props = new Properties();
            if (is != null)
            {
                props.load(is);
                final Object versionProperty = props.get("version");
                if (versionProperty != null)
                {
                    return versionProperty.toString();
                }
            }
        }
        catch (final IOException ex)
        {
            LOGGER.error("Failed to determine version of plugin", ex);
        }
        return UNKNOWN;
    }

    public static PluginVersionReader getInstance()
    {
        return INSTANCE;
    }

    /**
     * @return the current version of the plugin
     */
    public String getVersion()
    {
        return version;
    }
}
