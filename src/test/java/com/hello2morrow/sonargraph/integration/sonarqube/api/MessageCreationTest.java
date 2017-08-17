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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;

import com.hello2morrow.sonargraph.integration.access.controller.ControllerAccess;
import com.hello2morrow.sonargraph.integration.access.controller.IModuleInfoProcessor;
import com.hello2morrow.sonargraph.integration.access.controller.ISonargraphSystemController;
import com.hello2morrow.sonargraph.integration.access.controller.ISystemInfoProcessor;
import com.hello2morrow.sonargraph.integration.access.foundation.OperationResult;
import com.hello2morrow.sonargraph.integration.access.model.IDuplicateCodeBlockIssue;
import com.hello2morrow.sonargraph.integration.access.model.IDuplicateCodeBlockOccurrence;
import com.hello2morrow.sonargraph.integration.access.model.IIssue;
import com.hello2morrow.sonargraph.integration.access.model.IIssueCategory;
import com.hello2morrow.sonargraph.integration.access.model.IModule;
import com.hello2morrow.sonargraph.integration.access.model.ISourceFile;

public final class MessageCreationTest
{
    private static final String REPORT_PATH = "src/test/report/sonargraph-sonarqube-report.xml";

    @Test
    public void testMessageCreation()
    {
        final ISonargraphSystemController controller = ControllerAccess.createController();
        final OperationResult result = controller.loadSystemReport(new File(REPORT_PATH));
        assertTrue("Failed to load report", result.isSuccess());

        final ISystemInfoProcessor systemInfoProcessor = controller.createSystemInfoProcessor();
        for (final IModule nextModule : systemInfoProcessor.getModules().values())
        {
            final IModuleInfoProcessor nextModuleInfoProcessor = controller.createModuleInfoProcessor(nextModule);

            final Map<ISourceFile, List<IIssue>> issueMap = nextModuleInfoProcessor.getIssuesForSourceFiles(issue -> !issue.isIgnored()
                    && !IIssueCategory.StandardName.WORKSPACE.getStandardName().equals(issue.getIssueType().getCategory().getName()));

            for (final Entry<ISourceFile, List<IIssue>> nextEntry : issueMap.entrySet())
            {
                final ISourceFile nextSourceFile = nextEntry.getKey();
                for (final IIssue nextIssue : nextEntry.getValue())
                {
                    if (nextIssue instanceof IDuplicateCodeBlockIssue)
                    {
                        final IDuplicateCodeBlockIssue duplicateCodeBlockIssue = (IDuplicateCodeBlockIssue) nextIssue;
                        final List<IDuplicateCodeBlockOccurrence> occurrences = duplicateCodeBlockIssue.getOccurrences();

                        for (final IDuplicateCodeBlockOccurrence nextOccurrence : occurrences)
                        {
                            if (nextOccurrence.getSourceFile().equals(nextSourceFile))
                            {
                                final List<IDuplicateCodeBlockOccurrence> others = new ArrayList<>(occurrences);
                                others.remove(nextOccurrence);
                                final String nextMsg = IssueMessageCreator.create(nextModuleInfoProcessor, duplicateCodeBlockIssue, nextOccurrence,
                                        others);
                                System.out.println(nextMsg);
                            }
                        }
                    }
                    else
                    {
                        final String nextMsg = IssueMessageCreator.create(nextModuleInfoProcessor, nextIssue);
                        System.out.println(nextMsg);
                    }
                }
            }
        }
    }
}