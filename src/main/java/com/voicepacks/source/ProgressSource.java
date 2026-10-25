package com.voicepacks.source;

import com.voicepacks.trigger.EventSink;
import com.voicepacks.trigger.EventType;
import com.voicepacks.trigger.GameEvent;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;

/**
 * Level-ups (from an XP baseline, the approach c-engineer-completed uses, so logins and world hops don't fire),
 * deaths and logins.
 */
public class ProgressSource
{
	@Inject
	private Client client;

	@Inject
	private EventSink sink;

	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);
	private GameState lastState = GameState.UNKNOWN;
	/** LOGIN fires a few ticks after logging in, once the account's profile (and journey memory) has loaded. */
	private int loginTicks;

	public void reset()
	{
		lastXp.clear();
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		int xpAfter = event.getXp();
		Integer xpBefore = lastXp.put(skill, xpAfter);
		if (xpBefore == null || xpAfter <= xpBefore)
		{
			return;                                // first sighting since login, or no gain
		}
		int before = Experience.getLevelForXp(xpBefore);
		int after = Experience.getLevelForXp(xpAfter);
		if (after <= before)
		{
			return;
		}
		boolean virtual = after > Experience.MAX_REAL_LEVEL;
		String milestone = after == Experience.MAX_REAL_LEVEL ? "99" : after == Experience.MAX_VIRT_LEVEL ? "virtual_max" : null;
		GameEvent levelUp = new GameEvent(EventType.LEVEL_UP)
			.with("skill", skill.getName().toLowerCase(Locale.ROOT))
			.with("level", after)
			.with("virtual", virtual);
		if (milestone != null)
		{
			levelUp.with("milestone", milestone);
		}
		sink.accept(levelUp);
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() == client.getLocalPlayer())
		{
			sink.accept(new GameEvent(EventType.DEATH));
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN && lastState == GameState.LOGGING_IN)
		{
			loginTicks = 3;                         // world hops go HOPPING -> LOGGING_IN too; refine later
		}
		if (state == GameState.LOGIN_SCREEN)
		{
			lastXp.clear();
		}
		lastState = state;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (loginTicks > 0 && --loginTicks == 0)
		{
			sink.accept(new GameEvent(EventType.LOGIN));
		}
	}
}
