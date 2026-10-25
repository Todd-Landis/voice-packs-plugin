package com.voicepacks;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import com.voicepacks.audio.VoicePlayer;
import com.voicepacks.journey.ContextProvider;
import com.voicepacks.journey.JourneyStore;
import com.voicepacks.pack.PackImporter;
import com.voicepacks.pack.PackLoader;
import com.voicepacks.pack.PackManifest;
import com.voicepacks.source.ChatSource;
import com.voicepacks.source.LootSource;
import com.voicepacks.source.ProgressSource;
import com.voicepacks.trigger.EventSink;
import com.voicepacks.trigger.EventType;
import com.voicepacks.trigger.GameEvent;
import com.voicepacks.trigger.TriggerEngine;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Voice Packs",
	internalName = "voice-packs",
	description = "Plays pre-generated voice lines from installable voice packs",
	tags = {"voice", "sound", "tts", "announce", "level", "quest"}
)
public class VoicePacksPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(VoicePacksPlugin.class);
	private static final String JOURNEY_KEY = "journey";
	/** Lines saved up in a boss or raid region play after leaving it, unless they're older than this. */
	private static final long AFTER_ACTION_MAX_AGE_MS = 10 * 60_000;
	private static final int AFTER_ACTION_MAX = 30;
	private static final int SAVE_EVERY_TICKS = 100;   // about a minute

	@Inject
	private Client client;

	@Inject
	private EventBus eventBus;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	private VoicePacksConfig config;

	@Inject
	private EventSink sink;

	@Inject
	private ChatSource chatSource;

	@Inject
	private ProgressSource progressSource;

	@Inject
	private LootSource lootSource;

	@Inject
	private ContextProvider context;

	@Inject
	private PackLoader packLoader;

	@Inject
	private VoicePlayer player;

	@Inject
	private PackImporter importer;

	@Inject
	private ClientToolbar clientToolbar;

	private final JourneyStore journey = new JourneyStore();
	private final List<Deferred> afterAction = new ArrayList<>();
	private volatile PackLoader.Loaded loaded;
	private boolean journeyLoaded;
	private long lastPlayMs;
	private int ticksSinceSave;
	private int demoStep;
	private VoicePacksPanel panel;
	private NavigationButton navButton;

	/** An event that happened in a restricted region, waiting for the player to leave it. */
	private static final class Deferred
	{
		final GameEvent event;
		final long atMs;

		Deferred(GameEvent event, long atMs)
		{
			this.event = event;
			this.atMs = atMs;
		}
	}

	@Override
	protected void startUp()
	{
		eventBus.register(chatSource);
		eventBus.register(progressSource);
		eventBus.register(lootSource);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loadJourney();
		}
		panel = new VoicePacksPanel(this::importPack, this::removePack);
		navButton = NavigationButton.builder()
			.tooltip("Voice Packs")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		executor.execute(this::reloadPacks);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		saveJourney();
		eventBus.unregister(chatSource);
		eventBus.unregister(progressSource);
		eventBus.unregister(lootSource);
		progressSource.reset();
		sink.drain();
		afterAction.clear();
		player.stop();
		loaded = null;
		journeyLoaded = false;
	}

	@Provides
	VoicePacksConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(VoicePacksConfig.class);
	}

	// ------------------------------------------------------------------ packs

	private void reloadPacks()
	{
		Set<String> enabled = Arrays.stream(config.enabledPacks().split(","))
			.map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
		try
		{
			PackLoader.Loaded result = packLoader.load(packsDir(), enabled);
			result.engine.setPlayCounts(new TriggerEngine.PlayCounts()
			{
				@Override
				public int plays(String lineKey)
				{
					return journey.linePlays(lineKey);
				}

				@Override
				public void played(String lineKey)
				{
					journey.linePlayed(lineKey);
				}
			});
			loaded = result;
			showPacks(result.manifests);
		}
		catch (Exception e)
		{
			log.warn("Could not load voice packs", e);
		}
	}

	private Filepath packsDir() throws IOException
	{
		return getPluginDirectory().joinSegment("packs");
	}

	private void showPacks(List<PackManifest> manifests)
	{
		List<PackManifest> copy = new ArrayList<>(manifests);
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setPacks(copy);
			}
		});
	}

	private void showStatus(String text)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setStatus(text);
			}
		});
	}

	/** Swing thread: pick a zip, then unpack and reload off it. */
	private void importPack()
	{
		List<Filepath> picked = new Filepath.Chooser()
			.setIsOpen()
			.setAcceptsFiles()
			.setDialogTitle("Import a voice pack")
			.addExtensionFilter("Voice pack (.zip)", "zip")
			.showDialog(panel);
		if (picked == null || picked.isEmpty())
		{
			return;
		}
		Filepath zip = picked.get(0);
		panel.setStatus("Importing " + zip.getFileName() + "…");
		executor.execute(() ->
		{
			try
			{
				PackManifest m = importer.importZip(zip, packsDir());
				reloadPacks();
				showStatus("Installed " + m.name + " " + m.version + ".");
			}
			catch (Exception e)
			{
				log.warn("Could not import voice pack {}", zip, e);
				showStatus("Couldn't import that zip: " + e.getMessage());
			}
		});
	}

	private void removePack(String id)
	{
		executor.execute(() ->
		{
			try
			{
				Filepath dir = packsDir().joinSegment(id);
				if (dir.exists())
				{
					dir.deleteRecursively();
				}
				reloadPacks();
				showStatus("Removed " + id + ".");
			}
			catch (Exception e)
			{
				log.warn("Could not remove voice pack {}", id, e);
				showStatus("Couldn't remove " + id + ": " + e.getMessage());
			}
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (VoicePacksConfig.GROUP.equals(event.getGroup())
			&& "enabledPacks".equals(event.getKey()))
		{
			executor.execute(this::reloadPacks);
		}
	}

	// ------------------------------------------------------------------ journey memory

	private void loadJourney()
	{
		String json = configManager.getRSProfileConfiguration(VoicePacksConfig.GROUP, JOURNEY_KEY);
		try
		{
			journey.setData(json == null ? null : gson.fromJson(json, JourneyStore.Data.class));
		}
		catch (JsonParseException e)
		{
			log.warn("Unreadable voice pack journey data; starting fresh", e);
			journey.setData(null);
		}
		journeyLoaded = true;
	}

	private void saveJourney()
	{
		if (journeyLoaded && journey.takeDirty())
		{
			configManager.setRSProfileConfiguration(VoicePacksConfig.GROUP, JOURNEY_KEY, gson.toJson(journey.getData()));
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadJourney();                              // the previous account was saved at logout
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			saveJourney();
			afterAction.clear();
		}
	}

	// ------------------------------------------------------------------ playing lines

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		long now = System.currentTimeMillis();
		boolean restricted = inRestrictedRegion();
		List<GameEvent> events = sink.drain();
		// Kill counts first, so a pet or collection-log item in the same tick is credited to that kill.
		events.sort(Comparator.comparing(e -> e.getType() != EventType.BOSS_KC));
		for (GameEvent e : events)
		{
			annotate(e, now);
		}
		if (journeyLoaded)
		{
			journey.seen(now);                       // after annotating, so LOGIN still sees the old lastSeen
			if (++ticksSinceSave >= SAVE_EVERY_TICKS)
			{
				ticksSinceSave = 0;
				saveJourney();
			}
		}

		if (restricted)
		{
			for (GameEvent e : events)
			{
				if (afterAction.size() < AFTER_ACTION_MAX)
				{
					afterAction.add(new Deferred(e.with("afterAction", true), now));
				}
			}
			return;
		}
		if (!afterAction.isEmpty())
		{
			if (now - lastPlayMs < config.minGapSeconds() * 1000L)
			{
				return;                              // keep them until the gap has passed
			}
			afterAction.removeIf(d -> now - d.atMs > AFTER_ACTION_MAX_AGE_MS);
			afterAction.forEach(d -> events.add(d.event));
			afterAction.clear();
		}
		play(events, now);
	}

	private void annotate(GameEvent e, long now)
	{
		if (e.getType() == EventType.LOGIN)
		{
			context.loggedIn(now);
		}
		context.fill(e, now, LocalDateTime.now());
		if (journeyLoaded)
		{
			journey.annotate(e, now);
		}
	}

	/** @return the line that played, or null */
	private TriggerEngine.Selection play(List<GameEvent> events, long now)
	{
		PackLoader.Loaded packs = loaded;
		if (events.isEmpty() || packs == null || now - lastPlayMs < config.minGapSeconds() * 1000L)
		{
			return null;
		}
		TriggerEngine.Selection best = null;
		for (GameEvent event : events)
		{
			TriggerEngine.Selection s = packs.engine.select(event, now);
			if (s != null && (best == null || s.priority > best.priority))
			{
				best = s;
			}
		}
		if (best == null)
		{
			return null;
		}
		double linear = config.volume() / 100.0 * best.volume;
		if (linear <= 0)
		{
			return null;
		}
		lastPlayMs = now;
		float gainDb = (float) (20 * Math.log10(linear) + best.line.gainDb);
		player.play(packs.audioFor(best.packId, best.line.file), gainDb, best.priority, config.overlap());
		if (config.subtitles() && best.line.text != null)
		{
			say(best.line.text);
		}
		return best;
	}

	private void say(String text)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(text)
			.build());
	}

	// ------------------------------------------------------------------ demo

	@Subscribe
	public void onCommandExecuted(CommandExecuted command)
	{
		if (!"vp".equalsIgnoreCase(command.getCommand()))
		{
			return;
		}
		String[] args = command.getArguments();
		PackLoader.Loaded packs = loaded;
		if (args.length > 0 && "list".equalsIgnoreCase(args[0]))
		{
			say(packs == null ? "Voice packs: none loaded"
				: "Voice packs: " + packs.packs + ", " + packs.engine.size() + " triggers");
			return;
		}
		if (args.length > 0 && "demo".equalsIgnoreCase(args[0]))
		{
			int step = args.length > 1 && args[1].matches("\\d+") ? Integer.parseInt(args[1]) : demoStep + 1;
			demoStep = Math.floorMod(step - 1, DemoCommand.SHOWCASE.length) + 1;
			args = DemoCommand.showcase(demoStep);
			say("Demo " + demoStep + "/" + DemoCommand.SHOWCASE.length + ": " + String.join(" ", args));
		}
		EventType type = DemoCommand.type(args);
		if (type == null)
		{
			say(DemoCommand.USAGE);
			return;
		}
		long now = System.currentTimeMillis();
		GameEvent event = new GameEvent(type);
		context.fill(event, now, LocalDateTime.now());   // real context, no journey memory...
		DemoCommand.apply(event, args);                  // ...and typed fields override both
		lastPlayMs = 0;                                   // a demo shouldn't wait out the gap
		if (play(List.of(event), now) == null)
		{
			say("Voice packs: no line matches " + event + " (or it's cooling down)");
		}
	}

	private boolean inRestrictedRegion()
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return false;
		}
		WorldPoint wp = WorldPoint.fromLocalInstance(client, local.getLocalLocation());
		return wp != null && RestrictedRegions.contains(wp.getRegionID());
	}
}
