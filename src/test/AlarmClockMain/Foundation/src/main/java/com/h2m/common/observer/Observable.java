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
package com.h2m.common.observer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public abstract class Observable
{
    
    public interface IObserver
    {
        public void handleEvent(Observable observable, String event);
    }

    private final Map<String, List<IObserver>> m_EventToObservers = new HashMap<String, List<IObserver>>();

    protected Observable(String... events)
    {
        assert events != null : "'events' must not be null";
        assert events.length > 0 : "At least one event needs to be defined";
        for (String nextEvent : events)
        {
            List<IObserver> previous = m_EventToObservers.put(nextEvent, new ArrayList<IObserver>());
            assert previous == null : "Duplicate event defined: " + nextEvent;
        }
    }

    public final void addObserver(IObserver observer, String event)
    {
        assert observer != null : "'observer' must not be null";
        assert event != null : "'event' must not be null";
        assert m_EventToObservers.containsKey(event) : "Event not supported: " + event;
        assert !m_EventToObservers.get(event).contains(observer) : "Observer already added" + observer;
        m_EventToObservers.get(event).add(observer);
    }

    protected final void notifyAboutEvent(String event)
    {
        assert event != null : "'event' must not be null";
        assert m_EventToObservers.containsKey(event) : "Event not supported: " + event;
        for (IObserver nextObserver : m_EventToObservers.get(event))
        {
            nextObserver.handleEvent(this, event);
        }
    }
}