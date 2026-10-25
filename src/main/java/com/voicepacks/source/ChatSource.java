package com.voicepacks.source;

import com.voicepacks.trigger.EventSink;
import com.voicepacks.trigger.EventType;
import com.voicepacks.trigger.GameEvent;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Achievement events announced by game messages. The patterns are fixed in code; packs never supply them.
 * Kill-count and pet wording follows the Dink plugin's notifiers.
 */
public class ChatSource
{
	private static final Pattern QUEST = Pattern.compile("Congratulations, you've completed a quest: (.+?)\\.?$");
	private static final Pattern DIARY = Pattern.compile("completed all of the (\\w+) tasks in the (.+?) area");
	private static final Pattern COMBAT_TASK = Pattern.compile("Congratulations, you've completed an? (\\w+) combat task");
	private static final Pattern COLLECTION_LOG = Pattern.compile("New item added to your collection log: (.+)$");
	private static final Pattern CLUE = Pattern.compile("You have completed ([\\d,]+) (\\w+) Treasure Trails?\\.");
	private static final Pattern SLAYER = Pattern.compile("You have completed your task! You killed");
	private static final Pattern PET = Pattern.compile(
		"You have a funny feeling like you(?:'re being| would have been) followed|You feel something weird sneaking into your backpack");
	private static final Pattern UNTRADEABLE = Pattern.compile("Untradeable drop: (.+)$");
	private static final Pattern VALUABLE_DROP = Pattern.compile("Valuable drop: (.+?) \\(([\\d,]+) coins\\)");
	private static final Pattern MUSIC = Pattern.compile("You have unlocked a new music track: (.+?)\\.?$");
	private static final Pattern KC = Pattern.compile(
		"Your (.+?)\\s(?:kill|chest|completion|harvest|success|opened|lap)\\s?count is: ?([\\d,]+)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern KC_PREFIXED = Pattern.compile("Your (?:completed|subdued) (.+?) count is: ([\\d,]+)\\b");
	/** Ticks to wait after a pet message for the item message that names the pet. */
	private static final int PET_NAME_TICKS = 3;

	@Inject
	private EventSink sink;

	private GameEvent pendingPet;
	private int pendingPetTicks;

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE && chatMessage.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		String msg = Text.removeTags(chatMessage.getMessage());
		if (pendingPet != null)
		{
			String name = petName(msg);
			if (name != null)
			{
				pendingPet.with("pet", name);
			}
		}
		GameEvent event = parse(msg);
		if (event == null)
		{
			return;
		}
		if (event.getType() == EventType.PET)
		{
			pendingPet = event;                    // held until the name arrives or a few ticks pass
			pendingPetTicks = PET_NAME_TICKS;
		}
		else
		{
			sink.accept(event);
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (pendingPet != null && (pendingPet.get("pet") != null || --pendingPetTicks <= 0))
		{
			sink.accept(pendingPet);
			pendingPet = null;
		}
	}

	static GameEvent parse(String msg)
	{
		Matcher m;
		if ((m = QUEST.matcher(msg)).find())
		{
			return new GameEvent(EventType.QUEST_COMPLETE).with("quest", m.group(1));
		}
		if ((m = DIARY.matcher(msg)).find())
		{
			return new GameEvent(EventType.DIARY_COMPLETE).with("tier", lower(m.group(1))).with("area", m.group(2));
		}
		if ((m = COMBAT_TASK.matcher(msg)).find())
		{
			return new GameEvent(EventType.COMBAT_TASK).with("tier", lower(m.group(1)));
		}
		if ((m = COLLECTION_LOG.matcher(msg)).find())
		{
			return new GameEvent(EventType.COLLECTION_LOG).with("item", m.group(1));
		}
		if ((m = CLUE.matcher(msg)).find())
		{
			return new GameEvent(EventType.CLUE_COMPLETE).with("count", number(m.group(1))).with("tier", lower(m.group(2)));
		}
		if (SLAYER.matcher(msg).find())
		{
			return new GameEvent(EventType.SLAYER_TASK);
		}
		if ((m = PET.matcher(msg)).find())
		{
			return new GameEvent(EventType.PET).with("duplicate", m.group().contains("would have been"));
		}
		if ((m = VALUABLE_DROP.matcher(msg)).find())
		{
			return new GameEvent(EventType.VALUABLE_DROP).with("item", m.group(1)).with("value", number(m.group(2)));
		}
		if ((m = MUSIC.matcher(msg)).find())
		{
			return new GameEvent(EventType.MUSIC_UNLOCK).with("track", m.group(1));
		}
		if ((m = KC_PREFIXED.matcher(msg)).find() || (m = KC.matcher(msg)).find())
		{
			return new GameEvent(EventType.BOSS_KC).with("boss", m.group(1).trim()).with("kc", number(m.group(2)));
		}
		return null;
	}

	static String petName(String msg)
	{
		Matcher m = UNTRADEABLE.matcher(msg);
		if (m.find())
		{
			return m.group(1).trim();
		}
		m = COLLECTION_LOG.matcher(msg);
		return m.find() ? m.group(1).trim() : null;
	}

	private static long number(String s)
	{
		return Long.parseLong(s.replace(",", ""));
	}

	private static String lower(String s)
	{
		return s.toLowerCase(Locale.ROOT);
	}
}
