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
package com.hello2morrow.sonargraph.integration.sonarqube.api;

import java.util.List;

import com.hello2morrow.sonargraph.integration.access.controller.IModuleInfoProcessor;
import com.hello2morrow.sonargraph.integration.access.model.IDuplicateCodeBlockIssue;
import com.hello2morrow.sonargraph.integration.access.model.IDuplicateCodeBlockOccurrence;
import com.hello2morrow.sonargraph.integration.access.model.IIssue;
import com.hello2morrow.sonargraph.integration.access.model.IResolution;
import com.hello2morrow.sonargraph.integration.access.model.ResolutionType;
import com.hello2morrow.sonargraph.integration.sonarqube.foundation.Utilities;

final class IssueMessageCreator
{
    private IssueMessageCreator()
    {
        super();
    }

    private static String create(final IModuleInfoProcessor moduleInfoProcessor, final IIssue issue, final String detail)
    {
        assert moduleInfoProcessor != null : "Parameter 'moduleInfoProcessor' of method 'create' must not be null";
        assert issue != null : "Parameter 'issue' of method 'create' must not be null";
        assert detail != null : "Parameter 'detail' of method 'create' must not be null";

        final StringBuilder builder = new StringBuilder();

        final IResolution resolution = moduleInfoProcessor.getResolution(issue);
        if (resolution != null)
        {
            final ResolutionType type = resolution.getType();
            switch (type)
            {
            case FIX:
                builder.append("[").append(Utilities.toLowerCase(type.toString(), false)).append(": ").append(issue.getPresentationName())
                        .append("]");
                break;
            case REFACTORING:
            case TODO:
                builder.append("[").append(issue.getPresentationName()).append("]");
                break;
            case IGNORE:
                assert false : "Unexpected resolution type: " + type;
                break;
            default:
                assert false : "Unhandled resolution type: " + type;
                break;
            }

            builder.append(" assignee='").append(resolution.getAssignee()).append("'");
            builder.append(" priority='").append(Utilities.toLowerCase(resolution.getPriority().toString(), false)).append("'");
            builder.append(" description='").append(resolution.getDescription()).append("'");
            builder.append(" created='").append(resolution.getDate()).append("'");
        }
        else
        {
            //Or issue.getIssueType().getPresentationName()?
            builder.append("[").append(issue.getPresentationName()).append("]");
        }

        builder.append(" ").append(issue.getDescription());
        if (!detail.isEmpty())
        {
            builder.append(" ").append(detail);
        }
        builder.append(" [").append(issue.getIssueProvider().getPresentationName()).append("]");

        return builder.toString();
    }

    static String create(final IModuleInfoProcessor moduleInfoProcessor, final IDuplicateCodeBlockIssue issue,
            final IDuplicateCodeBlockOccurrence occurrence, final List<IDuplicateCodeBlockOccurrence> others)
    {
        assert moduleInfoProcessor != null : "Parameter 'moduleInfoProcessor' of method 'create' must not be null";
        assert issue != null : "Parameter 'issue' of method 'create' must not be null";
        assert occurrence != null : "Parameter 'occurrence' of method 'create' must not be null";
        assert others != null : "Parameter 'others' of method 'create' must not be null";

        final StringBuilder detail = new StringBuilder();
        detail.append("Line(s) ").append(occurrence.getStartLine()).append("-").append(occurrence.getStartLine() + occurrence.getBlockSize() - 1)
                .append(" duplicate of ");

        for (final IDuplicateCodeBlockOccurrence next : others)
        {
            detail.append(next.getSourceFile().getRelativePath() != null ? next.getSourceFile().getRelativePath() : next.getSourceFile()
                    .getPresentationName());
            detail.append(" line(s) ").append(next.getStartLine());
            detail.append("-").append(next.getStartLine() + next.getBlockSize() - 1);
        }

        return create(moduleInfoProcessor, issue, detail.toString());
    }

    static String create(final IModuleInfoProcessor moduleInfoProcessor, final IIssue issue)
    {
        assert moduleInfoProcessor != null : "Parameter 'moduleInfoProcessor' of method 'create' must not be null";
        assert issue != null : "Parameter 'issue' of method 'create' must not be null";
        return create(moduleInfoProcessor, issue, "");
    }
}