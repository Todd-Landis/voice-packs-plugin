package com.voicepacks.pack;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * voicepack.json, format 1. Field names match schema/voicepack.schema.json; Gson fills them and ignores unknown
 * keys, so packs can carry fields newer than this plugin as long as {@link #format} doesn't change.
 */
public class PackManifest
{
	public static final int FORMAT = 1;

	public int format;
	public String id;
	public String name;
	public String version;
	public String author;
	public String description;
	public Voice voice;
	public Tuning defaults;
	public List<Trigger> triggers = Collections.emptyList();

	public static class Voice
	{
		public String engine;
		public boolean cloned;
		public String likeness;      // original | licensed | real-person
		public String distribution;  // private | shareable
	}

	/** Per-trigger tuning; null means "inherit from defaults". */
	public static class Tuning
	{
		public Integer priority;
		public Double cooldownSeconds;
		public Double chance;
		public String pick;          // shuffle | random | sequence
		public Double volume;
	}

	public static class Trigger extends Tuning
	{
		public String id;
		public String event;
		/** field -> scalar | list | {min,max}; values arrive from Gson as String, Double, Boolean, List or Map. */
		public Map<String, Object> when = Collections.emptyMap();
		public List<Line> lines = Collections.emptyList();
	}

	public static class Line
	{
		public String file;
		public String text;
		public double weight = 1;
		public double gainDb;
		/** Plays at most this many times per account, ever; 0 = unlimited. */
		public int maxPlays;
	}
}
