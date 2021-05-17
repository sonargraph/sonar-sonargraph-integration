using System;
using System.Threading;

namespace Model
{
	public class AlarmClock : Foundation.Observable
	{
		public static String ALARM_EVENT = "alarm";

		public AlarmClock() : base(ALARM_EVENT)
		{
		}

		public void runIt() 
		{
			Thread newThread = new Thread(new ThreadStart(Run));
			newThread.Start(); 
		}

		public void Run()
		{
			for (int i = 0; i < 5; i++)
			{
				Console.Write("Tick");
				Thread.Sleep(1000);

			}

			notifyAboutEvent(ALARM_EVENT);
		}
	}
}

