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

    static
    {
    }
    
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