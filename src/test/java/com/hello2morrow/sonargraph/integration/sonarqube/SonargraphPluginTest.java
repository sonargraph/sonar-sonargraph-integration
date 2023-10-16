/**
 * SonarQube Sonargraph Integration Plugin
 * Copyright (C) 2016-2021 hello2morrow GmbH
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

import java.util.List;

import org.junit.Test;
import org.sonar.api.Plugin;
import org.sonar.api.Plugin.Context;
import org.sonar.api.SonarEdition;
import org.sonar.api.SonarProduct;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.SonarRuntime;
import org.sonar.api.utils.Version;

public final class SonargraphPluginTest
{
    static final class TestPlugin implements Plugin
    {
        static Context createTestContext()
        {
            return new Context(new SonarRuntime()
            {
                @Override
                public SonarQubeSide getSonarQubeSide()
                {
                    return SonarQubeSide.COMPUTE_ENGINE;
                }

                @Override
                public SonarProduct getProduct()
                {
                    return SonarProduct.SONARQUBE;
                }

                @Override
                public Version getApiVersion()
                {
                    return Version.create(6, 7, 6);
                }

                @Override
                public SonarEdition getEdition()
                {
                    return SonarEdition.COMMUNITY;
                }
            });
        }

        @Override
        public void define(final Context context)
        {
            //Not used
        }
    }

    @Test
    public void testPluginDefinition()
    {
        final Context context = TestPlugin.createTestContext();
        final SonargraphPlugin sonargraphPlugin = new SonargraphPlugin();
        sonargraphPlugin.define(context);

        @SuppressWarnings("rawtypes")
        final List extensions = context.getExtensions();
        assertEquals("Wrong number of extensions", 9, extensions.size());
    }
}