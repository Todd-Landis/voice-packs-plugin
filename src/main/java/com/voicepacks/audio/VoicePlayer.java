package com.voicepacks.audio;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One voice channel. Like RuneLite's AudioPlayer (javax.sound Clip + MASTER_GAIN), but it keeps the playing Clip
 * so a new line can interrupt it or be dropped, instead of two lines talking over each other.
 */
@Singleton
public class VoicePlayer
{
	private static final Logger log = LoggerFactory.getLogger(VoicePlayer.class);

	public enum Overlap
	{
		INTERRUPT, DROP, OVERLAP
	}

	@Inject
	private ScheduledExecutorService executor;

	private Clip current;
	private int currentPriority;

	/** @param gainDb total gain (master volume, pack volume and per-line trim combined) */
	public void play(Filepath file, float gainDb, int priority, Overlap overlap)
	{
		executor.execute(() -> playNow(file, gainDb, priority, overlap));
	}

	public synchronized void stop()
	{
		if (current != null)
		{
			current.close();
			current = null;
		}
	}

	private void playNow(Filepath file, float gainDb, int priority, Overlap overlap)
	{
		synchronized (this)
		{
			if (current != null && current.isActive() && overlap != Overlap.OVERLAP)
			{
				if (overlap == Overlap.DROP || priority < currentPriority)
				{
					return;
				}
				current.close();
			}
		}
		try (InputStream in = new BufferedInputStream(file.openInputStream());
			AudioInputStream audio = AudioSystem.getAudioInputStream(in))
		{
			Clip clip = AudioSystem.getClip();
			clip.open(audio);
			setGain(clip, gainDb);
			clip.addLineListener(e ->
			{
				if (e.getType() == LineEvent.Type.STOP)
				{
					clip.close();
					synchronized (this)
					{
						if (current == clip)
						{
							current = null;
						}
					}
				}
			});
			synchronized (this)
			{
				current = clip;
				currentPriority = priority;
			}
			clip.start();
		}
		catch (Exception e)
		{
			log.warn("Could not play {}: {}", file, e.getMessage());
		}
	}

	private static void setGain(Clip clip, float gainDb)
	{
		if (gainDb == 0 || !clip.isControlSupported(FloatControl.Type.MASTER_GAIN))
		{
			return;
		}
		FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
		gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), gainDb)));
	}
}
