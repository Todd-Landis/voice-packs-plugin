package com.voicepacks;

import com.voicepacks.audio.VoicePlayer;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(VoicePacksConfig.GROUP)
public interface VoicePacksConfig extends Config
{
	String GROUP = "voicepacks";

	@Range(max = 100)
	@ConfigItem(keyName = "volume", name = "Volume", description = "Master volume for voice lines", position = 1)
	default int volume()
	{
		return 80;
	}

	@ConfigItem(keyName = "enabledPacks", name = "Enabled packs",
		description = "Comma-separated pack ids to load (folder names under .runelite/plugin-data/voice-packs/packs). Empty loads all.",
		position = 2)
	default String enabledPacks()
	{
		return "";
	}

	@ConfigItem(keyName = "overlap", name = "When a line is playing",
		description = "Queue the new line until the current one ends, drop it, or play both at once", position = 3)
	default VoicePlayer.Overlap overlap()
	{
		return VoicePlayer.Overlap.QUEUE;
	}

	@Range(max = 60)
	@ConfigItem(keyName = "minGapSeconds", name = "Minimum gap (s)",
		description = "Never start lines closer together than this", position = 4)
	default int minGapSeconds()
	{
		return 2;
	}

	@ConfigItem(keyName = "subtitles", name = "Subtitles", description = "Also print each line in the chat box",
		position = 5)
	default boolean subtitles()
	{
		return true;
	}
}
