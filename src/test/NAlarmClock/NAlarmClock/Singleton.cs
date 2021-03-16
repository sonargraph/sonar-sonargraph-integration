using System;

namespace NAlarmClock
{
	public class Singleton
	{
		private static Singleton m_instance = null;

		private Singleton () : base()
		{
		}

		public static Singleton getInstance()
		{
			if (m_instance == null)
			{
				m_instance = new Singleton();
			}
			return m_instance;
		}
	}
}

