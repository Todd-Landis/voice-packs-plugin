package com.voicepacks.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import com.voicepacks.trigger.EventType;
import com.voicepacks.trigger.GameEvent;
import java.util.List;
import org.junit.Test;

public class JourneyStoreTest
{
	private static final long DAY = 24L * 60 * 60 * 1000;

	private final JourneyStore store = new JourneyStore();

	private GameEvent kc(String boss, long at)
	{
		GameEvent e = new GameEvent(EventType.BOSS_KC).with("boss", boss);
		store.annotate(e, at);
		return e;
	}

	@Test
	public void firstAndCountArePerKeyAndPerType()
	{
		GameEvent z1 = kc("Zulrah", 0);
		GameEvent z2 = kc("Zulrah", 1000);
		GameEvent v1 = kc("Vorkath", 2000);
		assertEquals(true, z1.get("first"));
		assertEquals(false, z2.get("first"));
		assertEquals(2, z2.get("count"));
		assertEquals(true, v1.get("first"));
		assertEquals(false, v1.get("firstAll"));
		assertEquals(3, v1.get("countAll"));
	}

	@Test
	public void dryKcCountsKillsUntilAUniqueIsCredited()
	{
		for (int i = 0; i < 4; i++)
		{
			kc("Zulrah", i * 60_000L);
		}
		assertEquals(5, kc("Zulrah", 300_000).get("dryKc"));

		GameEvent clog = new GameEvent(EventType.COLLECTION_LOG).with("item", "Tanzanite fang");
		store.annotate(clog, 305_000);             // 5 s after the kill: credited to Zulrah
		assertEquals("zulrah", clog.get("boss"));
		assertEquals(5, clog.get("dryKc"));
		assertEquals(1, kc("Zulrah", 400_000).get("dryKc"));

		GameEvent late = new GameEvent(EventType.PET).with("pet", "Snakeling");
		store.annotate(late, 400_000 + 60_000);    // a minute later: not credited
		assertNull(late.get("boss"));
	}

	@Test
	public void lootRecordsAndSinceLastDays()
	{
		GameEvent a = loot("Zulrah", 100_000, 0);
		GameEvent b = loot("Zulrah", 50_000, DAY);
		GameEvent c = loot("Vorkath", 2_000_000, 3 * DAY);
		assertEquals(false, a.get("record"));      // nothing to beat yet
		assertEquals(false, b.get("sourceRecord"));
		assertEquals(1, b.get("sinceLastDays"));
		assertEquals(true, c.get("record"));
		assertEquals(false, c.get("sourceRecord")); // first Vorkath loot
		assertEquals(true, loot("Vorkath", 3_000_000, 4 * DAY).get("sourceRecord"));
	}

	private GameEvent loot(String source, long value, long at)
	{
		GameEvent e = new GameEvent(EventType.LOOT).with("source", source).with("item", List.of("x")).with("value", value);
		store.annotate(e, at);
		return e;
	}

	@Test
	public void daysAwayAndRoundTripThroughJson()
	{
		store.seen(1);
		store.linePlayed("p/audio/a.wav");
		Gson gson = new Gson();
		JourneyStore reloaded = new JourneyStore();
		reloaded.setData(gson.fromJson(gson.toJson(store.getData()), JourneyStore.Data.class));
		GameEvent login = new GameEvent(EventType.LOGIN);
		reloaded.annotate(login, 9 * DAY + 5);
		assertEquals(9, login.get("daysAway"));
		assertEquals(1, reloaded.linePlays("p/audio/a.wav"));

		JourneyStore old = new JourneyStore();
		old.setData(gson.fromJson("{\"lastSeen\":5}", JourneyStore.Data.class)); // blob from an older version
		old.annotate(new GameEvent(EventType.DEATH), 10);
	}
}
