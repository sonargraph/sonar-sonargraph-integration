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