package com.voicepacks.pack;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.voicepacks.trigger.EventType;
import com.voicepacks.trigger.TriggerEngine;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads every pack folder under plugin-data/voice-packs/packs/. Uses {@link Filepath} only (the Plugin Hub forbids
 * java.io file APIs), so a pack can't reach outside its own folder.
 */
@Singleton
public class PackLoader
{
	private static final Logger log = LoggerFactory.getLogger(PackLoader.class);
	private static final Pattern LINE_FILE = Pattern.compile("audio/[A-Za-z0-9._-]+\\.wav");
	static final long MAX_CLIP_BYTES = 30 * 48_000 + 1024;   // ~30 s of 24 kHz mono 16-bit (~16 s at 44.1 kHz)

	/** The result of a load: built off the client thread, then swapped in whole. */
	public static final class Loaded
	{
		public final TriggerEngine engine = new TriggerEngine(new Random());
		public final List<String> packs = new ArrayList<>();
		public final List<PackManifest> manifests = new ArrayList<>();
		private final Map<String, Filepath> audio = new HashMap<>();

		public Filepath audioFor(String packId, String file)
		{
			return audio.get(packId + "/" + file);
		}
	}

	@Inject
	private Gson gson;

	/**
	 * @param enabled pack ids to load; empty loads all
	 * @param tiers   which trigger tiers this build may use
	 */
	public Loaded load(Filepath packsDir, Set<String> enabled, Set<EventType.Tier> tiers) throws IOException
	{
		Loaded result = new Loaded();
		if (!packsDir.exists())
		{
			packsDir.createDirectories();
		}
		List<Filepath> dirs;
		try (Stream<Filepath> s = packsDir.walk(1))
		{
			dirs = s.filter(p -> !p.equals(packsDir) && p.isDirectory() && !p.getFileName().startsWith("."))
				.sorted().collect(Collectors.toList());     // hidden folders are imports in progress
		}
		for (Filepath dir : dirs)
		{
			if (!enabled.isEmpty() && !enabled.contains(dir.getFileName()))
			{
				continue;
			}
			try
			{
				PackManifest m = loadPack(dir, tiers, result);
				result.packs.add(m.name + " " + m.version);
				result.manifests.add(m);
			}
			catch (IOException | JsonParseException | IllegalArgumentException e)
			{
				log.warn("Skipping voice pack {}: {}", dir.getFileName(), e.getMessage());
			}
		}
		log.info("Loaded {} voice pack(s), {} triggers: {}", result.packs.size(), result.engine.size(), result.packs);
		return result;
	}

	private PackManifest loadPack(Filepath dir, Set<EventType.Tier> tiers, Loaded result) throws IOException
	{
		PackManifest m;
		try (Reader r = dir.joinSegment("voicepack.json").openBufferedReader())
		{
			m = gson.fromJson(r, PackManifest.class);
		}
		if (m == null || m.format != PackManifest.FORMAT)
		{
			throw new IllegalArgumentException("unsupported format " + (m == null ? "?" : m.format));
		}
		if (!dir.getFileName().equals(m.id))
		{
			throw new IllegalArgumentException("folder name differs from id " + m.id);
		}
		for (PackManifest.Trigger t : m.triggers)
		{
			EventType type = EventType.fromKey(t.event);
			if (type == null || !tiers.contains(type.getTier()))
			{
				log.debug("{}: skipping trigger {} ({} not available in this build)", m.id, t.id, t.event);
				continue;
			}
			if (!t.when.keySet().stream().allMatch(type::allows))
			{
				log.warn("{}: skipping trigger {}: unknown fields in {}", m.id, t.id, t.when.keySet());
				continue;
			}
			List<PackManifest.Line> playable = new ArrayList<>();
			for (PackManifest.Line line : t.lines)
			{
				Filepath f = resolveLine(dir, line);
				if (f != null)
				{
					result.audio.put(m.id + "/" + line.file, f);
					playable.add(line);
				}
			}
			if (!playable.isEmpty())
			{
				t.lines = playable;
				result.engine.add(m.id, t, type, m.defaults);
			}
		}
		return m;
	}

	private Filepath resolveLine(Filepath dir, PackManifest.Line line) throws IOException
	{
		if (line.file == null || !LINE_FILE.matcher(line.file).matches())
		{
			log.warn("Bad line path {}", line.file);
			return null;
		}
		Filepath f = dir.join(line.file);          // throws if it would escape the pack folder
		if (!f.isFile() || f.size() > MAX_CLIP_BYTES)
		{
			log.warn("Missing or oversized clip {}", f);
			return null;
		}
		return f;
	}
}
