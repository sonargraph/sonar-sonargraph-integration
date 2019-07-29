package com.h2m.alarm.presentation;

import java.util.ArrayList;
import java.util.List;

import com.h2m.common.observer.Observable;
import com.h2m.common.observer.Observable.IObserver;

public abstract class AlarmHandler implements IObserver
{
    private final static List<AlarmHandler> s_AlarmHandler = new ArrayList<AlarmHandler>();

    static
    {
    }
    
    protected AlarmHandler()
    {
        s_AlarmHandler.add(this);
    }

    public static List<AlarmHandler> getAlarmHandler()
    {
        return s_AlarmHandler;
    }

    public final void handleEvent(Observable observable, String event)
    {
        assert observable != null : "'observable' must not be null";
        assert event != null : "'event' must not be null";
        System.out.println("Handling event: " + event);
        handleAlarm();
    }

    public abstract void handleAlarm();
}