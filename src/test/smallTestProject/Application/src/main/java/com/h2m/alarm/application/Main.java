package com.h2m.alarm.application;

import com.h2m.alarm.model.AlarmClock;
import com.h2m.alarm.presentation.console.AlarmToConsole;
import com.h2m.alarm.presentation.file.AlarmToFile;
import com.h2m.common.observer.DuplicateInFoundation;

public final class Main
{
    public static void main(final String[] args)
    {
        final AlarmToConsole alarmToConsole = new AlarmToConsole();
        final AlarmToFile alarmToFile = new AlarmToFile();
        final AlarmClock alarmClock = new AlarmClock();
        alarmClock.addObserver(alarmToConsole, AlarmClock.ALARM_EVENT);
        alarmClock.addObserver(alarmToFile, AlarmClock.ALARM_EVENT);
        new Thread(alarmClock).run();
        System.out.println("Producing system namespace cycle: " + new DuplicateInFoundation().getCount());
    }
}