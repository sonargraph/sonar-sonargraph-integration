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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;

import com.hello2morrow.sonargraph.integration.access.controller.ControllerFactory;
import com.hello2morrow.sonargraph.integration.access.controller.IModuleInfoProcessor;
import com.hello2morrow.sonargraph.integration.access.controller.ISonargraphSystemController;
import com.hello2morrow.sonargraph.integration.access.controller.ISystemInfoProcessor;
import com.hello2morrow.sonargraph.integration.access.foundation.OperationResult;
import com.hello2morrow.sonargraph.integration.access.model.IIssue;
import com.hello2morrow.sonargraph.integration.access.model.IIssueCategory;
import com.hello2morrow.sonargraph.integration.access.model.IModule;
import com.hello2morrow.sonargraph.integration.access.model.INamedElement;

public final class NamespaceRenameReportTest
{
    private static final String REPORT_PATH = "src/test/report/namespace-rename-report.xml";

    static final class NamedElementEntry
    {
        private final INamedElement m_namedElement;
        private final List<IIssue> m_issues;

        NamedElementEntry(final INamedElement namedElement, final List<IIssue> issues)
        {
            assert namedElement != null : "Parameter 'namedElement' of method 'NamedElementEntry' must not be null";
            assert issues != null : "Parameter 'issues' of method 'NamedElementEntry' must not be null";
            m_namedElement = namedElement;
            m_issues = issues;
        }

        INamedElement getNamedElement()
        {
            return m_namedElement;
        }

        List<IIssue> getIssues()
        {
            return m_issues;
        }
    }

    @Test
    public void testNamespaceRenameReport()
    {
        final ISonargraphSystemController controller = new ControllerFactory().createController();
        final OperationResult result = controller.loadSystemReport(new File(REPORT_PATH));
        assertTrue("Failed to load report", result.isSuccess());

        final ISystemInfoProcessor systemInfoProcessor = controller.createSystemInfoProcessor();
        for (final IModule nextModule : systemInfoProcessor.getModules().values())
        {
            final IModuleInfoProcessor nextModuleInfoProcessor = controller.createModuleInfoProcessor(nextModule);
            final Map<INamedElement, List<IIssue>> issueMap = nextModuleInfoProcessor.getIssuesForModuleElements(issue -> !issue.isIgnored()
                    && !IIssueCategory.StandardName.WORKSPACE.getStandardName().equals(issue.getIssueType().getCategory().getName()));

            final Map<String, NamedElementEntry> fqNameToNamedElementIssues = new HashMap<>();
            for (final Entry<INamedElement, List<IIssue>> nextEntry : issueMap.entrySet())
            {
                fqNameToNamedElementIssues.put(nextEntry.getKey().getFqName(), new NamedElementEntry(nextEntry.getKey(), nextEntry.getValue()));
                //                final boolean found = expectedFqNames.remove(namedElement.getFqName());
                //                assertTrue("Not an expected element: " + namedElement.getFqName(), found);
                //                assertEquals("Wrong fq name of element with issues", "Workspace:M1:./src:h2m", namedElement.getFqName());
                //                final List<IIssue> issues = nextEntry.getValue();
                //                assertEquals("2 issues expected", 2, issues.size());
                //
                //                final String kind = namedElement.getKind();
                //                if ("JavaPackageFragment".equals(kind) || "JavaLogicalModuleNamespace".equals(kind))
                //                {
                //                    final String presentationName = namedElement.getPresentationName();
                //                    System.out.println(kind + ": " + presentationName);
                //                }
            }

            assertEquals("3 elements with issues expected", 3, fqNameToNamedElementIssues.size());

            final String first = "Workspace:M1:./src:h2m";
            NamedElementEntry nextNamedElementEntry = fqNameToNamedElementIssues.remove(first);
            assertNotNull("Element not found:" + first, nextNamedElementEntry);
            assertEquals("2 issues expected", 2, nextNamedElementEntry.getIssues().size());

            final String second = "Logical module namespaces:M1:h2m:p1";
            nextNamedElementEntry = fqNameToNamedElementIssues.remove(second);
            assertNotNull("Element not found:" + second, nextNamedElementEntry);

            final String third = "Logical module namespaces:M1:h2m:p2";
            nextNamedElementEntry = fqNameToNamedElementIssues.remove(third);
            assertNotNull("Element not found:" + third, nextNamedElementEntry);
        }
    }
}