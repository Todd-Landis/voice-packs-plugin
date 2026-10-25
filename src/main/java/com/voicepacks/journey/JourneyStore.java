package com.voicepacks.journey;

import com.voicepacks.trigger.EventType;
import com.voicepacks.trigger.GameEvent;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What the narrator remembers about one account: how often things happened and when, kills since the last unique
 * per boss, best drops, the last time the player was seen, and how often each line has played. Pure Java (no
 * RuneLite types); the plugin persists {@link Data} as JSON in the account's RuneLite profile.
 *
 * <p>{@link #annotate} is called once per event, when it happens (even if no line ends up playing), and adds the
 * journey fields packs can filter on: first, count, firstAll, countAll, sinceLastDays, plus dryKc / boss on
 * BOSS_KC, COLLECTION_LOG and PET, record / sourceRecord on LOOT, and daysAway on LOGIN.
 */
public class JourneyStore
{
	private static final long DAY_MS = 24L * 60 * 60 * 1000;
	/** A collection-log item or pet this soon after a kill count message is credited to that boss. */
	private static final long ATTRIBUTION_MS = 15_000;

	/** The persisted state. Field names are the JSON keys. */
	public static class Data
	{
		public Map<String, Integer> counts = new HashMap<>();
		public Map<String, Long> lastAt = new HashMap<>();
		public Map<String, Integer> dryKc = new HashMap<>();
		public Map<String, Long> bestLoot = new HashMap<>();
		public Map<String, Integer> linePlays = new HashMap<>();
		public long lastSeen;
	}

	private Data data = new Data();
	private String lastBoss;
	private long lastBossAt;
	private boolean dirty;

	public Data getData()
	{
		return data;
	}

	public void setData(Data data)
	{
		this.data = data == null ? new Data() : fill(data);
		lastBoss = null;
		dirty = false;
	}

	/** True once since the last call if anything changed (the plugin uses this to decide when to save). */
	public boolean takeDirty()
	{
		boolean d = dirty;
		dirty = false;
		return d;
	}

	public void annotate(GameEvent e, long nowMs)
	{
		EventType type = e.getType();
		switch (type)
		{
			case BOSS_KC:
				bossKill(e, nowMs);
				break;
			case COLLECTION_LOG:
			case PET:
				creditUnique(e, nowMs);
				break;
			case LOOT:
				loot(e);
				break;
			case LOGIN:
				if (data.lastSeen > 0)
				{
					e.with("daysAway", (int) ((nowMs - data.lastSeen) / DAY_MS));
				}
				break;
			default:
				break;
		}

		String all = type.name();
		String keyed = type.getKeyField() == null ? all : all + ":" + norm(e.get(type.getKeyField()));
		Long before = data.lastAt.get(keyed);
		int countAll = bump(all, nowMs);
		int count = keyed.equals(all) ? countAll : bump(keyed, nowMs);
		e.with("countAll", countAll).with("firstAll", countAll == 1)
			.with("count", count).with("first", count == 1);
		if (before != null)
		{
			e.with("sinceLastDays", (int) ((nowMs - before) / DAY_MS));
		}
		dirty = true;
	}

	/** Called every game tick while logged in, so daysAway works however the session ends. */
	public void seen(long nowMs)
	{
		data.lastSeen = nowMs;
	}

	public int linePlays(String lineKey)
	{
		return data.linePlays.getOrDefault(lineKey, 0);
	}

	public void linePlayed(String lineKey)
	{
		data.linePlays.merge(lineKey, 1, Integer::sum);
		dirty = true;
	}

	// ------------------------------------------------------------------ internals

	private int bump(String key, long nowMs)
	{
		data.lastAt.put(key, nowMs);
		return data.counts.merge(key, 1, Integer::sum);
	}

	private void bossKill(GameEvent e, long nowMs)
	{
		String boss = norm(e.get("boss"));
		int dry = data.dryKc.merge(boss, 1, Integer::sum);
		e.with("dryKc", dry);
		lastBoss = boss;
		lastBossAt = nowMs;
	}

	private void creditUnique(GameEvent e, long nowMs)
	{
		if (lastBoss == null || nowMs - lastBossAt > ATTRIBUTION_MS)
		{
			return;
		}
		Integer dry = data.dryKc.put(lastBoss, 0);
		e.with("boss", lastBoss).with("dryKc", dry == null ? 0 : dry);
	}

	private void loot(GameEvent e)
	{
		Object v = e.get("value");
		if (!(v instanceof Number))
		{
			return;
		}
		long value = ((Number) v).longValue();
		long bestAll = data.bestLoot.getOrDefault("*", 0L);
		String sourceKey = norm(e.get("source"));
		long bestSource = data.bestLoot.getOrDefault(sourceKey, 0L);
		e.with("record", bestAll > 0 && value > bestAll).with("sourceRecord", bestSource > 0 && value > bestSource);
		if (value > bestAll)
		{
			data.bestLoot.put("*", value);
		}
		if (value > bestSource)
		{
			data.bestLoot.put(sourceKey, value);
		}
	}

	private static String norm(Object o)
	{
		return o == null ? "" : String.valueOf(o).toLowerCase(Locale.ROOT);
	}

	/** Gson leaves maps null when a saved blob predates a field. */
	private static Data fill(Data d)
	{
		Data blank = new Data();
		d.counts = d.counts == null ? blank.counts : d.counts;
		d.lastAt = d.lastAt == null ? blank.lastAt : d.lastAt;
		d.dryKc = d.dryKc == null ? blank.dryKc : d.dryKc;
		d.bestLoot = d.bestLoot == null ? blank.bestLoot : d.bestLoot;
		d.linePlays = d.linePlays == null ? blank.linePlays : d.linePlays;
		return d;
	}
}
