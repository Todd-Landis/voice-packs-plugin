package com.voicepacks.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import com.voicepacks.pack.PackManifest;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class TriggerEngineTest
{
	// Same shape as a typical pack: generic level-up, a 99 line, a woodcutting line, generic + specific quest.
	private static final String PACK = "{\"format\":1,\"id\":\"t\",\"name\":\"T\",\"version\":\"0.1.0\","
		+ "\"defaults\":{\"cooldownSeconds\":3},\"triggers\":["
		+ "{\"id\":\"levelup\",\"event\":\"level_up\",\"when\":{\"level\":{\"max\":98}},"
		+ " \"lines\":[{\"file\":\"audio/a.wav\"},{\"file\":\"audio/b.wav\"},{\"file\":\"audio/c.wav\"}]},"
		+ "{\"id\":\"levelup-99\",\"event\":\"level_up\",\"when\":{\"milestone\":\"99\"},\"priority\":90,"
		+ " \"lines\":[{\"file\":\"audio/99.wav\"}]},"
		+ "{\"id\":\"levelup-woodcutting\",\"event\":\"level_up\",\"when\":{\"skill\":\"woodcutting\",\"level\":{\"max\":98}},"
		+ " \"lines\":[{\"file\":\"audio/wc.wav\"}]},"
		+ "{\"id\":\"quest\",\"event\":\"quest_complete\",\"lines\":[{\"file\":\"audio/q.wav\"}]},"
		+ "{\"id\":\"quest-ds\",\"event\":\"quest_complete\",\"when\":{\"quest\":[\"Dragon Slayer I\",\"Dragon Slayer II\"]},"
		+ " \"priority\":70,\"lines\":[{\"file\":\"audio/ds.wav\"}]}]}";

	private TriggerEngine engine;

	@Before
	public void load()
	{
		PackManifest m = new Gson().fromJson(PACK, PackManifest.class);
		engine = new TriggerEngine(new Random(1));
		for (PackManifest.Trigger t : m.triggers)
		{
			engine.add(m.id, t, EventType.fromKey(t.event), m.defaults);
		}
	}

	private static GameEvent level(String skill, int level)
	{
		GameEvent e = new GameEvent(EventType.LEVEL_UP).with("skill", skill).with("level", level);
		return level == 99 ? e.with("milestone", "99") : e;
	}

	private String file(GameEvent e, long now)
	{
		TriggerEngine.Selection s = engine.select(e, now);
		return s == null ? null : s.line.file;
	}

	@Test
	public void mostSpecificWins()
	{
		assertEquals("audio/99.wav", file(level("attack", 99), 0));
		assertEquals("audio/wc.wav", file(level("woodcutting", 40), 0));
		assertEquals("audio/ds.wav", file(new GameEvent(EventType.QUEST_COMPLETE).with("quest", "dragon slayer ii"), 0));
		assertEquals("audio/q.wav", file(new GameEvent(EventType.QUEST_COMPLETE).with("quest", "Cook's Assistant"), 0));
	}

	@Test
	public void coolingDownSpecificFallsBackToGeneric()
	{
		assertEquals("audio/wc.wav", file(level("woodcutting", 40), 0));
		String next = file(level("woodcutting", 41), 1000);
		assertNotEquals("audio/wc.wav", next);
		assertEquals(true, next.matches("audio/[abc]\\.wav"));
	}

	@Test
	public void cooldownSilencesWhenNothingElseMatches()
	{
		assertEquals("audio/99.wav", file(level("attack", 99), 0));
		assertNull(file(level("strength", 99), 1000));
		assertEquals("audio/99.wav", file(level("strength", 99), 4000));
	}

	@Test
	public void onceOnlyLinesFallBackWhenUsedUp()
	{
		String pack = "{\"format\":1,\"id\":\"t\",\"name\":\"T\",\"version\":\"0.1.0\",\"triggers\":["
			+ "{\"id\":\"q\",\"event\":\"quest_complete\",\"lines\":[{\"file\":\"audio/q.wav\"}]},"
			+ "{\"id\":\"q-first\",\"event\":\"quest_complete\",\"when\":{\"firstAll\":true},\"priority\":90,"
			+ " \"lines\":[{\"file\":\"audio/first.wav\",\"maxPlays\":1}]},"
			+ "{\"id\":\"drop\",\"event\":\"loot\",\"when\":{\"item\":\"Tanzanite fang\"},"
			+ " \"lines\":[{\"file\":\"audio/fang.wav\"}]}]}";
		PackManifest m = new Gson().fromJson(pack, PackManifest.class);
		TriggerEngine e = new TriggerEngine(new Random(1));
		for (PackManifest.Trigger t : m.triggers)
		{
			e.add(m.id, t, EventType.fromKey(t.event), m.defaults);
		}
		GameEvent firstQuest = new GameEvent(EventType.QUEST_COMPLETE).with("firstAll", true);
		assertEquals("audio/first.wav", e.select(firstQuest, 0).line.file);
		assertEquals("audio/q.wav", e.select(firstQuest, 1000).line.file);   // once-only line is spent

		GameEvent loot = new GameEvent(EventType.LOOT).with("item", java.util.List.of("Zulrah's scales", "Tanzanite fang"));
		assertEquals("audio/fang.wav", e.select(loot, 0).line.file);         // any item in the drop matches
	}

	@Test
	public void shufflePlaysEveryLineBeforeRepeating()
	{
		Set<String> heard = new HashSet<>();
		for (int i = 0; i < 3; i++)
		{
			heard.add(file(level("attack", 10 + i), i * 10_000L));
		}
		assertEquals(Set.of("audio/a.wav", "audio/b.wav", "audio/c.wav"), heard);
	}
}
