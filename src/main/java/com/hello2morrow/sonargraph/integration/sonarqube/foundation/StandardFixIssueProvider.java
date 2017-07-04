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

import com.hello2morrow.sonargraph.integration.access.model.IIssueProvider;

final class StandardFixIssueProvider implements IIssueProvider
{
    private static final long serialVersionUID = -1554771689536851781L;

    @Override
    public String getName()
    {
        return "Sonargraph";
    }

    @Override
    public String getPresentationName()
    {
        return getName();
    }
}