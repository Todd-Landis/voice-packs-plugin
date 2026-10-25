package com.voicepacks;

import com.voicepacks.trigger.EventType;
import com.voicepacks.trigger.GameEvent;

/**
 * {@code ::vp <event> [field=value ...]}: fakes an event so any line can be heard without grinding for it, e.g.
 * {@code ::vp boss_kc boss=Zulrah kc=1 first=true} or {@code ::vp pet pet=Baby_mole}. Underscores become spaces;
 * true/false and numbers are typed. Simulated events don't touch journey memory.
 */
final class DemoCommand
{
	static final String USAGE = "::vp demo [N]  |  ::vp <event> [field=value ...], e.g. ::vp pet pet=Snakeling  |  ::vp list";

	/** What {@code ::vp demo} steps through, one per use; matches the example pack's showcase triggers. */
	static final String[] SHOWCASE = {
		"level_up skill=attack level=40",
		"level_up skill=woodcutting level=40",
		"level_up level=99 milestone=99",
		"quest_complete quest=Cook's_Assistant",
		"quest_complete quest=Dragon_Slayer_I",
		"boss_kc boss=Zulrah kc=1 first=true",
		"boss_kc boss=Zulrah kc=500",
		"collection_log item=Tanzanite_fang dryKc=412",
		"pet pet=Snakeling",
		"loot source=Zulrah item=Tanzanite_fang value=5000000 record=true",
		"clue_complete tier=master first=true",
		"login daysAway=10",
		"login timeOfDay=late",
		"boss_kc boss=Vorkath afterAction=true",
		"death",
		// specific lines: an example pack's per-quest, per-boss, per-pet and per-item triggers
		"quest_complete quest=Song_of_the_Elves",
		"boss_kc boss=TzTok-Jad kc=1",
		"boss_kc boss=Vorkath kc=250",
		"pet pet=Vorki",
		"collection_log item=Twisted_bow",
		"collection_log item=Scythe_of_Vitur",
	};

	private DemoCommand()
	{
	}

	/**
	 * The arguments for showcase step {@code n} (1-based, wraps around).
	 */
	static String[] showcase(int n)
	{
		int i = Math.floorMod(n - 1, SHOWCASE.length);
		return SHOWCASE[i].split(" ");
	}

	/** @return the event type named by {@code args[0]}, or null */
	static EventType type(String[] args)
	{
		return args.length == 0 ? null : EventType.fromKey(args[0]);
	}

	/** Sets each {@code field=value} argument on the event, overriding anything already there. */
	static GameEvent apply(GameEvent e, String[] args)
	{
		for (int i = 1; i < args.length; i++)
		{
			int eq = args[i].indexOf('=');
			if (eq > 0)
			{
				e.with(args[i].substring(0, eq), value(args[i].substring(eq + 1)));
			}
		}
		return e;
	}

	private static Object value(String raw)
	{
		if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw))
		{
			return Boolean.parseBoolean(raw);
		}
		if (raw.matches("-?\\d+"))
		{
			return Long.parseLong(raw);
		}
		return raw.replace('_', ' ');
	}
}
