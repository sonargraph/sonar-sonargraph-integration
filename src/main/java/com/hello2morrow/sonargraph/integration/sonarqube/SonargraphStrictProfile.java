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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Activates additional non-critical Sonargraph rules.
 */
public final class SonargraphStrictProfile extends SonargraphProfile
{
    static final List<String> RULE_KEYS = Arrays.asList("MODULE_CYCLE_GROUP", "NAMESPACE_CYCLE_GROUP", "COMPONENT_CYCLE_GROUP",
            "THRESHOLD_VIOLATION");
    static final String NAME = SonargraphBase.SONARGRAPH_PLUGIN_PRESENTATION_NAME + " (Strict)";

    public SonargraphStrictProfile()
    {
        super(NAME);
    }

    @Override
    protected List<String> getRuleKeys()
    {
        final List<String> ruleKeys = new ArrayList<>(super.getRuleKeys());
        ruleKeys.addAll(RULE_KEYS);
        return ruleKeys;
    }
}