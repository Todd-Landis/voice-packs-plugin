package com.voicepacks.journey;

import static org.junit.Assert.assertEquals;

import com.voicepacks.trigger.EventType;
import com.voicepacks.trigger.GameEvent;
import java.time.LocalDateTime;
import java.time.MonthDay;
import org.junit.Test;

public class ContextProviderTest
{
	@Test
	public void timeAndHolidays()
	{
		assertEquals("late", ContextProvider.timeOfDay(3));
		assertEquals("morning", ContextProvider.timeOfDay(9));
		assertEquals("evening", ContextProvider.timeOfDay(22));
		assertEquals("christmas", ContextProvider.holiday(MonthDay.of(12, 25)));
		assertEquals("none", ContextProvider.holiday(MonthDay.of(3, 3)));

		GameEvent e = new GameEvent(EventType.LOGIN);
		ContextProvider.addTime(e, LocalDateTime.of(2026, 10, 31, 2, 15));
		assertEquals("late", e.get("timeOfDay"));
		assertEquals("saturday", e.get("weekday"));
		assertEquals("halloween", e.get("holiday"));
	}
}
