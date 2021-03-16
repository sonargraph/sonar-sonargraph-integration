using System;
using System.Collections.Generic;
using System.Diagnostics;

namespace Presentation
{
	public abstract class AlarmHandler : Foundation.Observable.IObserver {

		private static List<AlarmHandler> s_AlarmHandler = new List<AlarmHandler>();

		protected AlarmHandler()
		{
			s_AlarmHandler.Add(this);
		}

		public static List<AlarmHandler> getAlarmHandler()
		{
			return s_AlarmHandler;
		}

		public virtual void handleEvent(Foundation.Observable observable, String eventString)
		{
			Debug.Assert(observable != null, "'observable' must not be null");
			Debug.Assert (eventString != null, "'event' must not be null");
			System.Console.Write("Handling event: " + eventString);
			handleAlarm();
		}

		public abstract void handleAlarm();
	}

	namespace Console 
	{
		public class AlarmToConsole : AlarmHandler
		{
			public AlarmToConsole () : base()
			{
			}

			private File.AlarmToFile cyclicDependency = new File.AlarmToFile(); 

			public override void handleAlarm()
			{
				System.Console.Write("Alarm received");
			}
		}
	}

	namespace File 
	{
		public class AlarmToFile : AlarmHandler
		{
			private Console.AlarmToConsole cyclicDependency = new Console.AlarmToConsole();

			public override void handleAlarm()
			{
				System.IO.File.WriteAllText(@"alarm.txt", "Alarm received");
			}
		}
	}
}

