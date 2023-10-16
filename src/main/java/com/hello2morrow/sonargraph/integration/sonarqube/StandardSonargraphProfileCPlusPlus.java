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

import java.util.List;

public final class StandardSonargraphProfileCPlusPlus extends AbstractSonargraphProfile
{
    static final String NAME = SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + " C/C++";

    public StandardSonargraphProfileCPlusPlus()
    {
        super(NAME, SonargraphBase.CPLUSPLUS);
    }

    @Override
    protected List<String> getRuleKeys()
    {
        return STANDARD_RULE_KEYS;
    }
}