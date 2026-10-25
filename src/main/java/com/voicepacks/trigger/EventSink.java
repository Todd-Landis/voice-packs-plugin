package com.voicepacks.trigger;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;

/**
 * Events collected during the current game tick. Sources add on the client thread; the plugin drains once per tick
 * and plays at most one line, so a quest that grants three levels at once says one thing, not four.
 */
@Singleton
public class EventSink
{
	private final List<GameEvent> pending = new ArrayList<>();

	public void accept(GameEvent event)
	{
		pending.add(event);
	}

	public List<GameEvent> drain()
	{
		List<GameEvent> out = new ArrayList<>(pending);
		pending.clear();
		return out;
	}
}
