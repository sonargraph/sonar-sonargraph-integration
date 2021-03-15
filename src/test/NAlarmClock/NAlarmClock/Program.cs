using System;

namespace NAlarmClock
{
	class MainClass
	{
		public static void Main (string[] args)
		{
			Presentation.Console.AlarmToConsole alarmToConsole = new Presentation.Console.AlarmToConsole();
			Presentation.File.AlarmToFile alarmToFile = new Presentation.File.AlarmToFile();
			Model.AlarmClock alarmClock = new Model.AlarmClock();
			alarmClock.addObserver(alarmToConsole, Model.AlarmClock.ALARM_EVENT);
			alarmClock.addObserver(alarmToFile, Model.AlarmClock.ALARM_EVENT);
			alarmClock.runIt();
		}
	}
}
