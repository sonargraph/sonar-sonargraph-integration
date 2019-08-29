package com.h2m.alarm.model;

import com.h2m.common.observer.Observable;

public final class AlarmClock extends Observable implements Runnable
{
    public final static String ALARM_EVENT = "alarm";

    static
    {
    }
    
    public AlarmClock()
    {
        super(ALARM_EVENT);
    }

    public void run()
    {
        for (int i = 0; i < 5; i++)
        {
            try
            {
                System.out.println("Tick");
                Thread.sleep(1000);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        notifyAboutEvent(ALARM_EVENT);
    }
}