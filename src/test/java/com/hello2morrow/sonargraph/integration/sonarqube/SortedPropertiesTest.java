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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

public class SortedPropertiesTest
{
    private Properties m_sorted;

    @Before
    public void setup()
    {
        m_sorted = new SortedProperties();
        m_sorted.put("z", "1");
        m_sorted.put("a", "2");
    }

    @Test
    public void testKeys()
    {
        final Enumeration<Object> keys = m_sorted.keys();
        assertEquals("a", keys.nextElement());
        assertEquals("z", keys.nextElement());
    }

    @Test
    public void testKeySet()
    {
        final Set<Object> keys = m_sorted.keySet();
        assertArrayEquals(new Object[] { "a", "z" }, keys.toArray());
    }

    @Test
    public void testEntrySet()
    {
        final Set<Entry<Object, Object>> entries = m_sorted.entrySet();
        final Iterator<Entry<Object, Object>> iter = entries.iterator();
        assertEquals("a", iter.next().getKey());
        assertEquals("z", iter.next().getKey());
    }
}