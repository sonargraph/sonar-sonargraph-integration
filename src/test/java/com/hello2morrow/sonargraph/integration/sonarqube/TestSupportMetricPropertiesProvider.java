package com.hello2morrow.sonargraph.integration.sonarqube;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.hello2morrow.sonargraph.integration.sonarqube.SonargraphBase.CustomMetricsPropertiesProvider;

final class TestSupportMetricPropertiesProvider extends CustomMetricsPropertiesProvider
{
    private static final String CUSTOM_METRICS_DIRECTORY = "./src/test/." + SonargraphBase.SONARGRAPH_PLUGIN_KEY;

    public TestSupportMetricPropertiesProvider() throws IOException
    {
        reset();
    }

    @Override
    public String getDirectory()
    {
        return CUSTOM_METRICS_DIRECTORY;
    }

    private void reset() throws IOException
    {
        final Path metricProps = Paths.get(getFilePath());
        if (metricProps.toFile().exists())
        {
            Files.delete(metricProps);
        }
    }
}
