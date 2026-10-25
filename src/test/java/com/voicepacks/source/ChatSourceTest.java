package com.voicepacks.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.voicepacks.trigger.EventType;
import com.voicepacks.trigger.GameEvent;
import org.junit.Test;

public class ChatSourceTest
{
	@Test
	public void parsesAchievementMessages()
	{
		GameEvent q = ChatSource.parse("Congratulations, you've completed a quest: Dragon Slayer I");
		assertEquals(EventType.QUEST_COMPLETE, q.getType());
		assertEquals("Dragon Slayer I", q.get("quest"));

		GameEvent clue = ChatSource.parse("You have completed 1,204 hard Treasure Trails.");
		assertEquals(1204L, clue.get("count"));
		assertEquals("hard", clue.get("tier"));

		GameEvent drop = ChatSource.parse("Valuable drop: Rune platebody (38,000 coins)");
		assertEquals("Rune platebody", drop.get("item"));
		assertEquals(38000L, drop.get("value"));

		assertEquals(true, ChatSource.parse("You have a funny feeling like you would have been followed...").get("duplicate"));
		assertNull(ChatSource.parse("You catch a shark."));
	}

	@Test
	public void parsesKillCounts()
	{
		GameEvent z = ChatSource.parse("Your Zulrah kill count is: 1,234.");
		assertEquals(EventType.BOSS_KC, z.getType());
		assertEquals("Zulrah", z.get("boss"));
		assertEquals(1234L, z.get("kc"));
		assertEquals("Chambers of Xeric", ChatSource.parse("Your completed Chambers of Xeric count is: 12.").get("boss"));
		assertEquals("Wintertodt", ChatSource.parse("Your subdued Wintertodt count is: 300.").get("boss"));
		assertEquals("Barrows", ChatSource.parse("Your Barrows chest count is: 77.").get("boss"));
		assertEquals("Gauntlet", ChatSource.parse("Your Gauntlet completion count is: 5.").get("boss"));
	}

	@Test
	public void namesPetsFromTheFollowingItemMessage()
	{
		assertEquals("Snakeling", ChatSource.petName("Untradeable drop: Snakeling"));
		assertEquals("Snakeling", ChatSource.petName("New item added to your collection log: Snakeling"));
		assertNull(ChatSource.petName("You feel something weird sneaking into your backpack."));
	}
}
