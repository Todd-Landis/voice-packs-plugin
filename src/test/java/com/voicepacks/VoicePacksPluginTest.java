package com.voicepacks;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Launches RuneLite with this plugin loaded (the Plugin Hub template's launcher). Run with `gradle run`. */
public class VoicePacksPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(VoicePacksPlugin.class);
		RuneLite.main(args);
	}
}
