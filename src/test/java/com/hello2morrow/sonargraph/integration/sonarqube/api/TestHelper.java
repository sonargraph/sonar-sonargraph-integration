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
package com.hello2morrow.sonargraph.integration.sonarqube.api;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFile.Status;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issuable;
import org.sonar.api.issue.Issuable.IssueBuilder;
import org.sonar.api.measures.Measure;
import org.sonar.api.measures.Metrics;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Resource;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.Repository;
import org.sonar.api.server.rule.RulesDefinition.Rule;

import com.hello2morrow.sonargraph.integration.sonarqube.foundation.SonargraphPluginBase;

public final class TestHelper
{
    public static final String REPORT_PATH_MULTI_MODULES = "src/test/AlarmClockMain/AlarmClockMain.xml";
    public static final String REPORT_PATH_SINGLE_MODULE = "src/test/report/AlarmClock_Ant.xml";
    public static final String REPORT_CRM = new File("./src/test/report/CRM-Sonargraph-Report.xml").getAbsolutePath();
    public static final String REPORT_EXPLORER = "src/test/report/explorer/AlarmClockMain.xml";
    public static final String REPORT_PATH_WORKSPACE_ISSUES = "src/test/report/AlarmClockMain_WorkpaceProblems.xml";

    public static final String REPORT_WITH_FIX_RESOLUTIONS = "src/test/report/AlarmClockMain_Fix_Resolution.xml";

    private static Map<String, Measure<?>> s_systemMetrics;
    private static Map<String, Integer> s_ruleKeyToNumberOfIssuesMap;
    private static final List<InputFile> inputFiles = new ArrayList<>();

    private TestHelper()
    {
        super();
    }

    public static RulesProfile initRulesProfile(final String... keysOfInactiveRules)
    {
        final RulesDefinition rulesDefinition = new SonargraphRulesRepository(initSettings());
        final RulesDefinition.Context context = new RulesDefinition.Context();
        rulesDefinition.define(context);
        ((Metrics) rulesDefinition).getMetrics();

        final Repository repository = context.repository(SonargraphPluginBase.PLUGIN_KEY);
        final RulesProfile profile = RulesProfile.create(SonargraphPluginBase.PLUGIN_KEY, "JAVA");

        final List<String> inactiveRules = keysOfInactiveRules != null ? Arrays.asList(keysOfInactiveRules) : Collections.emptyList();

        for (final Rule rule : repository.rules())
        {
            if (!inactiveRules.contains(rule.key()))
            {
                profile.activateRule(org.sonar.api.rules.Rule.create(repository.key(), rule.key(), rule.name()), null);
            }
        }
        return profile;
    }

    public static Settings initSettings()
    {
        final Settings settings = new Settings();
        settings.setProperty(SonargraphPluginBase.COST_PER_INDEX_POINT, 7.0);
        return settings;
    }

    public static Settings initSettings(final String configurationPath)
    {
        final Settings settings = initSettings();
        settings.setProperty(SonargraphPluginBase.METADATA_PATH, configurationPath);
        return settings;
    }

    @SuppressWarnings("rawtypes")
    public static SensorContext initSensorContext()
    {
        s_systemMetrics = new HashMap<>();
        s_ruleKeyToNumberOfIssuesMap = new HashMap<>();
        final SensorContext sensorContext = mock(SensorContext.class);

        when(sensorContext.getResource(any(Resource.class))).thenAnswer(new Answer()
        {

            @Override
            public Object answer(final InvocationOnMock invocation)
            {
                final Object[] args = invocation.getArguments();
                return args[0];
            }
        });
        when(sensorContext.saveMeasure(any(Measure.class))).thenAnswer(new Answer()
        {
            @Override
            public final Object answer(final InvocationOnMock invocation) throws Throwable
            {
                final Object a0 = invocation.getArguments()[0];
                final Measure measure = (Measure) a0;
                final String metricKey = measure.getMetricKey();

                s_systemMetrics.put(metricKey, measure);

                return measure;
            }
        });

        return sensorContext;
    }

    public static Map<String, Measure<?>> getMeasures()
    {
        return Collections.unmodifiableMap(s_systemMetrics);
    }

    public static FileSystem initModuleFileSystem(final String basePath)
    {
        final FileSystem fileSystem = mock(FileSystem.class);

        inputFiles.clear();
        final String[] paths = new String[] { "AlarmClock/src/main/java/com/h2m/alarm/presentation/Main.java",
                "AlarmClock/src/main/java/com/h2m/alarm/model/AlarmClock.java", "Foundation/src/main/java/com/h2m/common/test/C1.java",
                "AlarmClock/src/main/java/com/h2m/alarm/p2/C2.java", "AlarmClock/src/main/java/com/h2m/alarm/presentation/AlarmHandler.java",
                "AlarmClock/src/main/java/com/h2m/alarm/presentation/AlarmToFile.java",
                "AlarmClock/src/main/java/com/h2m/alarm/presentation/AlarmToConsole.java" };
        for (final String next : paths)
        {
            final String path = basePath + "/" + next;
            final InputFile file = mock(InputFile.class);
            when(file.absolutePath()).thenAnswer(new Answer<String>()
            {
                @Override
                public String answer(final InvocationOnMock invocation) throws Throwable
                {
                    return path;
                }
            });
            inputFiles.add(file);
        }

        when(fileSystem.resolvePath(any())).thenAnswer(new Answer<File>()
        {
            @Override
            public File answer(final InvocationOnMock mock) throws Throwable
            {
                final File f = new File(String.valueOf(mock.getArguments()[0]));
                return (f.isAbsolute()) ? f : new File("./" + f.getPath());
            }
        });

        when(fileSystem.hasFiles(any(FilePredicate.class))).thenAnswer(new Answer<Boolean>()
        {
            @Override
            public Boolean answer(final InvocationOnMock invocation) throws Throwable
            {
                return true;
            }
        });

        when(fileSystem.inputFile(any(FilePredicate.class))).thenAnswer(new Answer<InputFile>()
        {
            @Override
            public InputFile answer(final InvocationOnMock invocation) throws Throwable
            {
                final Object object = invocation.getArguments()[0];
                if (object instanceof FilePredicate)
                {
                    final Optional<InputFile> findFirst = inputFiles.stream().filter(f -> ((FilePredicate) object).apply(f)).findFirst();
                    if (findFirst.isPresent())
                    {
                        return findFirst.get();
                    }
                }
                return null;
            }
        });

        when(fileSystem.files(any(FilePredicate.class))).thenAnswer(new Answer<Iterable<File>>()
        {
            @Override
            public List<File> answer(final InvocationOnMock invocation) throws Throwable
            {
                final List<File> fileList = new ArrayList<>();
                fileList.add(new File("com/h2m/alarm/model/AlarmClock.java"));
                fileList.add(new File("com/h2m/alarm/presentation/Main.java"));
                return fileList;
            }
        });

        when(fileSystem.predicates()).thenAnswer(new Answer<FilePredicates>()
        {
            @Override
            public FilePredicates answer(final InvocationOnMock invocation) throws Throwable
            {
                return new FilePredicates()
                {
                    @Override
                    public FilePredicate or(final FilePredicate first, final FilePredicate second)
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate or(final FilePredicate... or)
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate or(final Collection<FilePredicate> or)
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate not(final FilePredicate p)
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate none()
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate matchesPathPatterns(final String[] inclusionPatterns)
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate matchesPathPattern(final String inclusionPattern)
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate is(final File ioFile)
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate hasType(final Type type)
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate hasStatus(final Status status)
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate hasRelativePath(final String s)
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate hasPath(final String s)
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate hasLanguages(final Collection<String> languages)
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate hasLanguage(final String language)
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate hasAbsolutePath(final String s)
                    {
                        return new FilePredicate()
                        {
                            @Override
                            public boolean apply(final InputFile inputFile)
                            {
                                return inputFile.absolutePath().equals(s);
                            }
                        };
                    }

                    @Override
                    public FilePredicate doesNotMatchPathPatterns(final String[] exclusionPatterns)
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate doesNotMatchPathPattern(final String exclusionPattern)
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate and(final FilePredicate first, final FilePredicate second)
                    {
                        return first;
                    }

                    @Override
                    public FilePredicate and(final FilePredicate... and)
                    {
                        return new FilePredicate()
                        {
                            @Override
                            public boolean apply(final InputFile inputFile)
                            {
                                return true;
                            }
                        };
                    }

                    @Override
                    public FilePredicate and(final Collection<FilePredicate> and)
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate all()
                    {
                        return null;
                    }

                    @Override
                    public FilePredicate hasLanguages(final String... languages)
                    {
                        return new FilePredicate()
                        {
                            @Override
                            public boolean apply(final InputFile inputFile)
                            {
                                return true;
                            }
                        };
                    }
                };
            }
        });
        return fileSystem;
    }

    public static ResourcePerspectives initPerspectives()
    {
        final ResourcePerspectives perspectives = mock(ResourcePerspectives.class);
        when(perspectives.as(any(), any(InputPath.class))).thenAnswer(new Answer<Issuable>()
        {
            @Override
            public Issuable answer(final InvocationOnMock invocation) throws Throwable
            {
                final Issuable issuable = mock(Issuable.class);
                when(issuable.newIssueBuilder()).thenAnswer(new Answer<IssueBuilder>()
                {
                    @Override
                    public IssueBuilder answer(final InvocationOnMock invocation) throws Throwable
                    {
                        final IssueBuilder builder = mock(IssueBuilder.class);
                        when(builder.ruleKey(any(RuleKey.class))).thenAnswer(new Answer<IssueBuilder>()
                        {
                            @Override
                            public IssueBuilder answer(final InvocationOnMock invocation) throws Throwable
                            {
                                final Object a0 = invocation.getArguments()[0];
                                final RuleKey key = (RuleKey) a0;
                                final String rule = key.rule();
                                final Integer counter;
                                if (s_ruleKeyToNumberOfIssuesMap.containsKey(rule))
                                {
                                    counter = s_ruleKeyToNumberOfIssuesMap.get(rule);
                                }
                                else
                                {
                                    counter = new Integer(0);
                                }
                                s_ruleKeyToNumberOfIssuesMap.put(rule, counter + 1);

                                return builder;
                            }
                        });
                        return builder;
                    }
                });

                return issuable;
            }
        });
        return perspectives;
    }

    public static int getNumberOfIssues(final String ruleKey)
    {
        assert ruleKey != null && ruleKey.length() > 0 : "Parameter 'ruleKey' of method 'getNumberOfIssues' must not be empty";
        final Integer count = s_ruleKeyToNumberOfIssuesMap.get(ruleKey);
        return count == null ? 0 : count.intValue();
    }
}