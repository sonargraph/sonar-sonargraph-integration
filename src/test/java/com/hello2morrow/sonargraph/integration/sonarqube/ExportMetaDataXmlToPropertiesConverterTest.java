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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

import com.hello2morrow.sonargraph.integration.access.model.IExportMetaData;

public class ExportMetaDataXmlToPropertiesConverterTest
{
    public ExportMetaDataXmlToPropertiesConverterTest()
    {
        super();
    }

    @Test
    public void convertMetrics() throws IOException
    {
        final ExportMetaDataXmlToPropertiesConverter converter = new ExportMetaDataXmlToPropertiesConverter();
        final IExportMetaData metaData = converter.readBuiltInMetaData();
        final int numberOfMetrics = converter.convertMetrics(metaData);
        assertTrue("Wrong number of metrics: " + numberOfMetrics, numberOfMetrics > 50);
    }

    @Test
    public void convertRules() throws IOException
    {
        final ExportMetaDataXmlToPropertiesConverter converter = new ExportMetaDataXmlToPropertiesConverter();
        final IExportMetaData metaData = converter.readBuiltInMetaData();
        final int numberOfRules = converter.convertRules(metaData);
        assertEquals("Wrong number of rules", 17, numberOfRules);
    }
}