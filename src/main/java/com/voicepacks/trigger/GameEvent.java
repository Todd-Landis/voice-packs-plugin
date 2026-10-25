package com.voicepacks.trigger;

import java.util.HashMap;
import java.util.Map;

/** Something that happened in game, reduced to an event type and the fields packs may filter on. */
public final class GameEvent
{
	private final EventType type;
	private final Map<String, Object> fields = new HashMap<>();

	public GameEvent(EventType type)
	{
		this.type = type;
	}

	public GameEvent with(String field, Object value)
	{
		fields.put(field, value);
		return this;
	}

	public EventType getType()
	{
		return type;
	}

	public Object get(String field)
	{
		return fields.get(field);
	}

	@Override
	public String toString()
	{
		return type + fields.toString();
	}
}
