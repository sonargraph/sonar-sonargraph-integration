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

import java.util.List;

import com.hello2morrow.sonargraph.integration.access.model.IDuplicateCodeBlockIssue;
import com.hello2morrow.sonargraph.integration.access.model.IDuplicateCodeBlockOccurrence;
import com.hello2morrow.sonargraph.integration.access.model.INamedElement;
import com.hello2morrow.sonargraph.integration.access.model.IResolution;

public final class DuplicateFixResolutionIssue extends StandardFixResolutionIssue implements IDuplicateCodeBlockIssue
{
    private static final long serialVersionUID = -8852427965647146746L;

    public DuplicateFixResolutionIssue(final IResolution resolution, final IDuplicateCodeBlockIssue issue)
    {
        super(resolution, issue);
    }

    @Override
    public String getName()
    {
        return ((IDuplicateCodeBlockIssue) getIssue()).getName();
    }

    @Override
    public List<INamedElement> getAffectedElements()
    {
        return ((IDuplicateCodeBlockIssue) getIssue()).getAffectedElements();
    }

    @Override
    public String getPresentationName()
    {
        return ((IDuplicateCodeBlockIssue) getIssue()).getPresentationName();
    }

    @Override
    public int getBlockSize()
    {
        return ((IDuplicateCodeBlockIssue) getIssue()).getBlockSize();
    }

    @Override
    public List<IDuplicateCodeBlockOccurrence> getOccurrences()
    {
        return ((IDuplicateCodeBlockIssue) getIssue()).getOccurrences();
    }
}