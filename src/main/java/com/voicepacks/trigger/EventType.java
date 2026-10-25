package com.voicepacks.trigger;

import java.util.Locale;
import java.util.Set;

/**
 * The fixed trigger catalogue (mirrors schema/events.json). Packs can only fill these slots: the Plugin Hub does
 * not accept plugins whose behaviour comes from player-supplied IDs, so ID-based events are PERSONAL and are only
 * loaded in developer mode.
 */
public enum EventType
{
	LEVEL_UP(Tier.CORE, "milestone", "skill", "level", "virtual", "milestone", "multi"),
	XP_MILESTONE(Tier.CORE, "skill", "skill", "xp"),
	QUEST_COMPLETE(Tier.CORE, null, "quest"),
	DIARY_COMPLETE(Tier.CORE, "tier", "area", "tier"),
	COMBAT_TASK(Tier.CORE, "tier", "tier"),
	COLLECTION_LOG(Tier.CORE, null, "item", "boss", "dryKc"),
	CLUE_COMPLETE(Tier.CORE, "tier", "tier", "count"),
	SLAYER_TASK(Tier.CORE, null),
	BOSS_KC(Tier.CORE, "boss", "boss", "kc", "dryKc"),
	PET(Tier.CORE, null, "pet", "duplicate", "boss", "dryKc"),
	LOOT(Tier.CORE, "source", "source", "item", "top", "value", "record", "sourceRecord"),
	VALUABLE_DROP(Tier.CORE, null, "item", "value"),
	DEATH(Tier.CORE, null),
	LOGIN(Tier.CORE, null, "daysAway"),
	MUSIC_UNLOCK(Tier.CORE, null, "track"),

	GAME_MESSAGE(Tier.EXTENDED, null, "contains"),
	NPC_SEEN(Tier.EXTENDED, "npc", "npc"),
	SKILLING_IDLE(Tier.EXTENDED, null, "seconds"),

	NPC_ID(Tier.PERSONAL, "id", "id"),
	ITEM_DROP_ID(Tier.PERSONAL, "id", "id"),
	REGION_ENTER(Tier.PERSONAL, "region", "region"),
	VARBIT(Tier.PERSONAL, "id", "id", "value");

	public enum Tier
	{
		CORE, EXTENDED, PERSONAL
	}

	/** Filled on every event by the plugin (who the player is, when, where). */
	public static final Set<String> CONTEXT_FIELDS = Set.of("account", "totalLevel", "combatLevel", "questPoints",
		"world", "timeOfDay", "weekday", "holiday", "sessionMinutes", "inWilderness", "afterAction");

	/** Filled on every event from the account's journey memory. */
	public static final Set<String> JOURNEY_FIELDS = Set.of("first", "count", "firstAll", "countAll", "sinceLastDays");

	private final Tier tier;
	private final String keyField;
	private final Set<String> fields;

	EventType(Tier tier, String keyField, String... fields)
	{
		this.tier = tier;
		this.keyField = keyField;
		this.fields = Set.of(fields);
	}

	public Tier getTier()
	{
		return tier;
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
