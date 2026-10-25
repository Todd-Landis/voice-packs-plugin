package com.voicepacks.trigger;

import com.voicepacks.pack.PackManifest.Line;
import com.voicepacks.pack.PackManifest.Trigger;
import com.voicepacks.pack.PackManifest.Tuning;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Picks the line to say for a game event. Pure Java (no RuneLite types) so it can be unit tested.
 *
 * <p>Candidates for an event are ranked by priority, then specificity (number of {@code when} keys), then pack and
 * declaration order. The first matching candidate that is off cooldown wins, so a pack can hold hundreds of
 * specific lines (per quest, per skill, per level) and a few generic ones that cover everything else. Achievement
 * events are rare, so a linear scan per event is plenty fast even for thousands of triggers.
 */
public class TriggerEngine
{
	/** A loaded trigger with its pack's defaults applied. */
	public static final class Candidate
	{
		final String packId;
		final Trigger trigger;
		final int priority;
		final double cooldownMs;
		final double chance;
		final String pick;
		final double volume;
		final int order;

		Candidate(String packId, Trigger trigger, Tuning defaults, int order)
		{
			this.packId = packId;
			this.trigger = trigger;
			this.priority = first(trigger.priority, defaults == null ? null : defaults.priority, 50);
			this.cooldownMs = 1000 * first(trigger.cooldownSeconds, defaults == null ? null : defaults.cooldownSeconds, 0.0);
			this.chance = first(trigger.chance, defaults == null ? null : defaults.chance, 1.0);
			this.pick = first(trigger.pick, defaults == null ? null : defaults.pick, "shuffle");
			this.volume = first(trigger.volume, defaults == null ? null : defaults.volume, 1.0);
			this.order = order;
		}

		String key()
		{
			return packId + "/" + trigger.id;
		}

		int specificity()
		{
			return trigger.when.size();
		}
	}

	/** How often each line has played on this account (journey memory); drives {@code maxPlays}. */
	public interface PlayCounts
	{
		int plays(String lineKey);

		void played(String lineKey);
	}

	/** What to play. */
	public static final class Selection
	{
		public final String packId;
		public final String triggerId;
		public final Line line;
		public final int priority;
		public final double volume;

		Selection(Candidate c, Line line)
		{
			this.packId = c.packId;
			this.triggerId = c.trigger.id;
			this.line = line;
			this.priority = c.priority;
			this.volume = c.volume;
		}
	}

	private static final Comparator<Candidate> RANK = Comparator
		.comparingInt((Candidate c) -> -c.priority)
		.thenComparingInt(c -> -c.specificity())
		.thenComparingInt(c -> c.order);

	private final Map<EventType, List<Candidate>> byEvent = new EnumMap<>(EventType.class);
	private final Map<String, Long> lastFired = new HashMap<>();
	private final Map<String, Deque<Line>> bags = new HashMap<>();
	private final Map<String, Integer> sequence = new HashMap<>();
	private final Random random;
	private PlayCounts playCounts = new PlayCounts()
	{
		private final Map<String, Integer> counts = new HashMap<>();

		@Override
		public int plays(String lineKey)
		{
			return counts.getOrDefault(lineKey, 0);
		}

		@Override
		public void played(String lineKey)
		{
			counts.merge(lineKey, 1, Integer::sum);
		}
	};
	private int order;

	public TriggerEngine(Random random)
	{
		this.random = random;
	}

	public void setPlayCounts(PlayCounts playCounts)
	{
		this.playCounts = playCounts;
	}

	public static String lineKey(String packId, Line line)
	{
		return packId + "/" + line.file;
	}

	public void clear()
	{
		byEvent.clear();
		lastFired.clear();
		bags.clear();
		sequence.clear();
		order = 0;
	}

	public void add(String packId, Trigger trigger, EventType type, Tuning defaults)
	{
		List<Candidate> list = byEvent.computeIfAbsent(type, t -> new ArrayList<>());
		list.add(new Candidate(packId, trigger, defaults, order++));
		list.sort(RANK);
	}

	public int size()
	{
		return byEvent.values().stream().mapToInt(List::size).sum();
	}

	/** The line to play for {@code event}, or null to stay quiet. */
	public Selection select(GameEvent event, long nowMs)
	{
		for (Candidate c : byEvent.getOrDefault(event.getType(), Collections.emptyList()))
		{
			if (!matches(c.trigger.when, event))
			{
				continue;
			}
			List<Line> lines = available(c);
			if (lines.isEmpty())
			{
				continue;                          // every once-only line used up: fall back
			}
			Long last = lastFired.get(c.key());
			if (last != null && nowMs - last < c.cooldownMs)
			{
				continue;                          // fall back to a less specific line
			}
			if (c.chance < 1 && random.nextDouble() >= c.chance)
			{
				return null;                       // the best match chose silence; don't play a generic line instead
			}
			lastFired.put(c.key(), nowMs);
			Line line = pickLine(c, lines);
			playCounts.played(lineKey(c.packId, line));
			return new Selection(c, line);
		}
		return null;
	}

	private List<Line> available(Candidate c)
	{
		return c.trigger.lines.stream()
			.filter(l -> l.maxPlays <= 0 || playCounts.plays(lineKey(c.packId, l)) < l.maxPlays)
			.collect(Collectors.toList());
	}

	private Line pickLine(Candidate c, List<Line> lines)
	{
		switch (c.pick)
		{
			case "sequence":
			{
				int i = sequence.merge(c.key(), 1, Integer::sum) - 1;
				return lines.get(i % lines.size());
			}
			case "random":
			{
				double total = lines.stream().mapToDouble(l -> l.weight).sum();
				double r = random.nextDouble() * total;
				for (Line l : lines)
				{
					r -= l.weight;
					if (r < 0)
					{
						return l;
					}
				}
				return lines.get(lines.size() - 1);
			}
			default:                               // shuffle: every line once before any repeats
			{
				Deque<Line> bag = bags.computeIfAbsent(c.key(), k -> new ArrayDeque<>());
				bag.retainAll(lines);              // drop lines that have since hit maxPlays
				if (bag.isEmpty())
				{
					List<Line> shuffled = new ArrayList<>(lines);
					Collections.shuffle(shuffled, random);
					bag.addAll(shuffled);
				}
				return bag.poll();
			}
		}
	}

	static boolean matches(Map<String, Object> when, GameEvent event)
	{
		for (Map.Entry<String, Object> e : when.entrySet())
		{
			if (!matchesOne(e.getValue(), event.get(e.getKey())))
			{
				return false;
			}
		}
		return true;
	}

	private static boolean matchesOne(Object cond, Object actual)
	{
		if (cond instanceof String && "any".equalsIgnoreCase((String) cond))
		{
			return actual != null;
		}
		if (actual == null)
		{
			return false;
		}
		if (actual instanceof Collection)          // e.g. every item in a loot drop: any one may match
		{
			return ((Collection<?>) actual).stream().anyMatch(a -> matchesOne(cond, a));
		}
		if (cond instanceof Collection)
		{
			return ((Collection<?>) cond).stream().anyMatch(c -> matchesOne(c, actual));
		}
		if (cond instanceof Map)
		{
			if (!(actual instanceof Number))
			{
				return false;
			}
			double v = ((Number) actual).doubleValue();
			Object min = ((Map<?, ?>) cond).get("min");
			Object max = ((Map<?, ?>) cond).get("max");
			return (!(min instanceof Number) || v >= ((Number) min).doubleValue())
				&& (!(max instanceof Number) || v <= ((Number) max).doubleValue());
		}
		if (cond instanceof Number && actual instanceof Number)
		{
			return ((Number) cond).doubleValue() == ((Number) actual).doubleValue();
		}
		if (cond instanceof Boolean)
		{
			return cond.equals(actual);
		}
		return String.valueOf(cond).toLowerCase(Locale.ROOT).equals(String.valueOf(actual).toLowerCase(Locale.ROOT));
	}

	private static <T> T first(T a, T b, T fallback)
	{
		return a != null ? a : b != null ? b : fallback;
	}
}
