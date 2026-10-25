package com.voicepacks.trigger;

import java.util.Locale;
import java.util.Set;

/**
 * The fixed trigger catalogue (mirrors schema/events.json). Packs can only fill these slots. Every event is detected
 * by fixed code in the plugin; none takes IDs, game-message text or NPC names from a pack (Plugin Hub rule).
 */
public enum EventType
{
	LEVEL_UP("milestone", "skill", "level", "virtual", "milestone", "multi"),
	XP_MILESTONE("skill", "skill", "xp"),
	QUEST_COMPLETE(null, "quest"),
	DIARY_COMPLETE("tier", "area", "tier"),
	COMBAT_TASK("tier", "tier"),
	COLLECTION_LOG(null, "item", "boss", "dryKc"),
	CLUE_COMPLETE("tier", "tier", "count"),
	SLAYER_TASK(null),
	BOSS_KC("boss", "boss", "kc", "dryKc"),
	PET(null, "pet", "duplicate", "boss", "dryKc"),
	LOOT("source", "source", "item", "top", "value", "record", "sourceRecord"),
	VALUABLE_DROP(null, "item", "value"),
	DEATH(null),
	LOGIN(null, "daysAway"),
	MUSIC_UNLOCK(null, "track");

	/** Filled on every event by the plugin (who the player is, when, where). */
	public static final Set<String> CONTEXT_FIELDS = Set.of("account", "totalLevel", "combatLevel", "questPoints",
		"world", "timeOfDay", "weekday", "holiday", "sessionMinutes", "inWilderness", "afterAction");

	/** Filled on every event from the account's journey memory. */
	public static final Set<String> JOURNEY_FIELDS = Set.of("first", "count", "firstAll", "countAll", "sinceLastDays");

	private final String keyField;
	private final Set<String> fields;

	EventType(String keyField, String... fields)
	{
		this.keyField = keyField;
		this.fields = Set.of(fields);
	}

	/** The field "first"/"count" are counted per (e.g. per boss), or null to count the event type as a whole. */
	public String getKeyField()
	{
		return keyField;
	}

	public Set<String> getFields()
	{
		return fields;
	}

	/** Whether a pack's {@code when} may name this field for this event. */
	public boolean allows(String field)
	{
		return fields.contains(field) || CONTEXT_FIELDS.contains(field) || JOURNEY_FIELDS.contains(field);
	}

	/** "level_up" -> LEVEL_UP, or null if the pack names an event this plugin doesn't know. */
	public static EventType fromKey(String key)
	{
		try
		{
			return key == null ? null : valueOf(key.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}
}
