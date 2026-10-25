package com.voicepacks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.voicepacks.pack.PackManifest;
import com.voicepacks.trigger.TriggerEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.Assume;

import com.voicepacks.trigger.EventType;
import com.voicepacks.trigger.GameEvent;
import org.junit.Test;

public class DemoCommandTest
{
	@Test
	public void parsesTypedFields()
	{
		String[] args = {"boss_kc", "boss=Corporeal_Beast", "kc=1", "first=true", "timeOfDay=late"};
		assertEquals(EventType.BOSS_KC, DemoCommand.type(args));
		GameEvent e = DemoCommand.apply(new GameEvent(EventType.BOSS_KC).with("timeOfDay", "morning"), args);
		assertEquals("Corporeal Beast", e.get("boss"));
		assertEquals(1L, e.get("kc"));
		assertEquals(true, e.get("first"));
		assertEquals("late", e.get("timeOfDay"));
		assertNull(DemoCommand.type(new String[]{"nonsense"}));
		assertNull(DemoCommand.type(new String[0]));
	}

	@Test
	public void everyShowcaseStepIsAValidEvent()
	{
		assertEquals(DemoCommand.SHOWCASE[0], String.join(" ", DemoCommand.showcase(1)));
		assertEquals(DemoCommand.SHOWCASE[0], String.join(" ", DemoCommand.showcase(DemoCommand.SHOWCASE.length + 1)));
		for (int n = 1; n <= DemoCommand.SHOWCASE.length; n++)
		{
			String[] args = DemoCommand.showcase(n);
			EventType type = DemoCommand.type(args);
			assertNotNull(args[0], type);
			for (int i = 1; i < args.length; i++)
			{
				String field = args[i].substring(0, args[i].indexOf('='));
				assertTrue(args[0] + " " + field, type.allows(field));
			}
		}
	}

	/** Steps 16-21 must reach the lines vgen drafted, not a generic fallback. */
	private static final String[] SPECIFIC = {"quest-song-of-the-elves", "boss-tztok-jad-first",
		"boss-vorkath-milestone", "pet-vorki", "clog-twisted-bow", "clog-scythe-of-vitur"};

	/** With an example pack built by the packer (../out/mlx/<pack>), every showcase step must pick a line. */
	@Test
	public void everyShowcaseStepPlaysALineFromTheExamplePack() throws Exception
	{
		PackManifest m = examplePack();
		Assume.assumeNotNull(m);
		TriggerEngine engine = new TriggerEngine(new Random(1));
		for (PackManifest.Trigger t : m.triggers)
		{
			engine.add(m.id, t, EventType.fromKey(t.event), m.defaults);
		}
		for (int n = 1; n <= DemoCommand.SHOWCASE.length; n++)
		{
			String[] args = DemoCommand.showcase(n);
			GameEvent e = DemoCommand.apply(new GameEvent(DemoCommand.type(args)).with("timeOfDay", "afternoon"), args);
			TriggerEngine.Selection picked = engine.select(e, n * 60_000L);
			assertNotNull("no line for demo " + n + ": " + DemoCommand.SHOWCASE[n - 1], picked);
			if (n > 15)
			{
				assertEquals(SPECIFIC[n - 16], picked.triggerId);
			}
		}
	}

	/** The first locally built pack that has every specific showcase trigger, or null. */
	private static PackManifest examplePack() throws Exception
	{
		Path built = Path.of("../out/mlx");
		if (!Files.isDirectory(built))
		{
			return null;
		}
		try (var dirs = Files.list(built))
		{
			for (Path dir : (Iterable<Path>) dirs.sorted()::iterator)
			{
				Path manifest = dir.resolve("voicepack.json");
				if (!Files.isRegularFile(manifest))
				{
					continue;
				}
				PackManifest m = new Gson().fromJson(Files.readString(manifest), PackManifest.class);
				var ids = m.triggers.stream().map(t -> t.id).collect(java.util.stream.Collectors.toSet());
				if (ids.containsAll(java.util.List.of(SPECIFIC)))
				{
					return m;
				}
			}
		}
		return null;
	}
}
