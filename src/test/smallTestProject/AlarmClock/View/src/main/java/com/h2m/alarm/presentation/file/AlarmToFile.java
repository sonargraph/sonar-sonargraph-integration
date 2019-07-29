package com.h2m.alarm.presentation.file;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import com.h2m.alarm.presentation.AlarmHandler;
import com.h2m.alarm.presentation.console.AlarmToConsole;

public final class AlarmToFile extends AlarmHandler
{
    private AlarmToConsole cyclicDependency = new AlarmToConsole();
  
    static
    {
    }
    
    public AlarmToFile() 
    {
    	super();
	}
    
    @Override
    public void handleAlarm()
    {
        try
        {
            File file = new File("alarm.txt");
            file.createNewFile();
            PrintWriter writer = new PrintWriter(file);
            writer.println("Alarm received");
            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
