package com.voicepacks.journey;

import com.voicepacks.trigger.GameEvent;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.util.EnumSet;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.vars.AccountType;

/** Fills the context fields (who the player is, when and where) that every trigger can filter on. */
@Singleton
public class ContextProvider
{
	@Inject
	private Client client;

	private long loginAtMs;

	public void loggedIn(long nowMs)
	{
		loginAtMs = nowMs;
	}

	/** Call on the client thread. */
	public void fill(GameEvent e, long nowMs, LocalDateTime now)
	{
		Player local = client.getLocalPlayer();
		e.with("account", account(client.getAccountType()))
			.with("totalLevel", client.getTotalLevel())
			.with("questPoints", client.getVarpValue(VarPlayerID.QP))
			.with("world", world(client.getWorldType()))
			.with("inWilderness", client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1)
			.with("sessionMinutes", loginAtMs == 0 ? 0 : (int) ((nowMs - loginAtMs) / 60_000))
			.with("afterAction", false);
		if (local != null)
		{
			e.with("combatLevel", local.getCombatLevel());
		}
		addTime(e, now);
	}

	static void addTime(GameEvent e, LocalDateTime now)
	{
		e.with("timeOfDay", timeOfDay(now.getHour()))
			.with("weekday", now.getDayOfWeek().name().toLowerCase(Locale.ROOT))
			.with("holiday", holiday(MonthDay.from(now)));
	}

	static String timeOfDay(int hour)
	{
		if (hour >= 5 && hour < 12)
		{
			return "morning";
		}
		if (hour >= 12 && hour < 17)
		{
			return "afternoon";
		}
		if (hour >= 17 && hour < 23)
		{
			return "evening";
		}
		return "late";
	}

	static String holiday(MonthDay d)
	{
		switch (d.getMonthValue() * 100 + d.getDayOfMonth())
		{
			case 1231:
			case 101:
				return "new_year";
			case 214:
				return "valentines";
			case 1031:
				return "halloween";
			case 1224:
			case 1225:
			case 1226:
				return "christmas";
			default:
				return "none";
		}
	}

	private static String account(AccountType t)
	{
		if (t == null)
		{
			return "main";
		}
		switch (t)
		{
			case IRONMAN:
				return "ironman";
			case ULTIMATE_IRONMAN:
				return "ultimate";
			case HARDCORE_IRONMAN:
				return "hardcore";
			case GROUP_IRONMAN:
				return "group";
			case HARDCORE_GROUP_IRONMAN:
				return "hardcore_group";
			default:
				return "main";
		}
	}

	private static String world(EnumSet<WorldType> types)
	{
		if (types.contains(WorldType.SEASONAL))
		{
			return "leagues";
		}
		if (types.contains(WorldType.DEADMAN))
		{
			return "deadman";
		}
		return types.contains(WorldType.MEMBERS) ? "members" : "f2p";
	}
}
