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
package com.h2m.alarm.presentation;

import java.util.ArrayList;
import java.util.List;

import com.h2m.common.observer.Observable;
import com.h2m.common.observer.Observable.IObserver;

public abstract class AlarmHandler implements IObserver
{
    private final static List<AlarmHandler> s_AlarmHandler = new ArrayList<AlarmHandler>();
    private AlarmToConsole m_dummyReference;
    
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