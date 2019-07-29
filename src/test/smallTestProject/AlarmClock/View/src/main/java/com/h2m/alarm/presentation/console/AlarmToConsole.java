package com.h2m.alarm.presentation.console;

import com.h2m.alarm.presentation.AlarmHandler;
import com.h2m.alarm.presentation.file.AlarmToFile;

public final class AlarmToConsole extends AlarmHandler
{
    private AlarmToFile cyclicDependency = new AlarmToFile(); 
  
    static
    {
    }
    
    public AlarmToConsole() 
    {
    	super();
	}
    
    @Override
    public void handleAlarm()
    {
        System.out.println("Alarm received");
    }
    
    
}