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
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

abstract class AbstractDataProvider
{
    protected static final char SEPARATOR = '|';
    private final String propertiesFileName;
    private final String customDirectoryPath;

    protected AbstractDataProvider(final String propertiesFileName)
    {
        this(propertiesFileName, System.getProperty("user.home") + "/." + SonargraphBase.SONARGRAPH_PLUGIN_KEY);
    }

    protected AbstractDataProvider(final String propertiesFileName, final String customDirectoryPath)
    {
        this.propertiesFileName = propertiesFileName;
        this.customDirectoryPath = customDirectoryPath;
    }

    String getDirectory()
    {
        return customDirectoryPath;
    }

    protected final String getFilePath()
    {
        return getDirectory() + "/" + propertiesFileName;
    }

    protected final File saveProperties(final Properties properties, final File targetFile, final String comment) throws IOException
    {
        final File targetDirectory = targetFile.getParentFile();
        targetDirectory.mkdirs();

        try (FileWriter writer = new FileWriter(targetFile))
        {
            properties.store(writer, comment);
        }

        return targetFile;
    }
}