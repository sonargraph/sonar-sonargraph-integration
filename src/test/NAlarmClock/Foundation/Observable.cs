using System;
using System.Collections.Generic;
using System.Diagnostics;

namespace Foundation
{
	public abstract class Observable
	{
		public interface IObserver
		{
			void handleEvent(Observable observable, String eventString);
		}

		private Dictionary<String, List<IObserver>> m_EventToObservers = new Dictionary<String, List<IObserver>>();

		protected Observable(params String[] events)
		{
			Debug.Assert(events != null, "'events' must not be null");
			Debug.Assert(events.Length > 0, "At least one event needs to be defined");
			foreach (String nextEvent in events)
			{
				Debug.Assert(m_EventToObservers.ContainsKey(nextEvent), "Duplicate event defined: " + nextEvent);
				m_EventToObservers.Add(nextEvent, new List<IObserver>());
			}
		}

		public virtual void addObserver(IObserver observer, String eventString)
		{
			Debug.Assert(observer != null, "'observer' must not be null");
			Debug.Assert(eventString != null, "'event' must not be null");
			Debug.Assert(m_EventToObservers.ContainsKey(eventString), "Event not supported: " + eventString);
			List<IObserver> list = m_EventToObservers[eventString];
			Debug.Assert(!list.Contains(observer),"Observer already added" + observer);
			list.Add (observer);
		}

		protected virtual void notifyAboutEvent(String eventString)
		{
			Debug.Assert(eventString != null, "'event' must not be null");
			Debug.Assert(m_EventToObservers.ContainsKey(eventString), "Event not supported: " + eventString);
			foreach (IObserver nextObserver in m_EventToObservers[eventString])
			{
				nextObserver.handleEvent(this, eventString);
			}
		}
	}
}




