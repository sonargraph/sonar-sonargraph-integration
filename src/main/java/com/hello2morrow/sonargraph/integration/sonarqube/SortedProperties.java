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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Stores properties in alphabetical order, so that diff'ing properties files reveals meaningful results.
 */
final class SortedProperties extends Properties
{
    private static final long serialVersionUID = -6711758219183764034L;

    public SortedProperties()
    {
        super();
    }

    //Needed for Java 8
    @Override
    public synchronized Enumeration<Object> keys()
    {
        final Enumeration<Object> keysEnum = super.keys();
        final List<Object> keyList = new ArrayList<>();
        while (keysEnum.hasMoreElements())
        {
            keyList.add(keysEnum.nextElement());
        }

        Collections.sort(keyList, (o1, o2) -> o1.toString().compareTo(o2.toString()));
        return Collections.enumeration(keyList);
    }

    //Needed for Java 11
    @Override
    public Set<java.util.Map.Entry<Object, Object>> entrySet()
    {
        final List<java.util.Map.Entry<Object, Object>> entries = new ArrayList<>(super.entrySet());
        entries.sort((o1, o2) -> o1.getKey().toString().compareTo(o2.getKey().toString()));
        return new LinkedHashSet<>(entries);
    }
}