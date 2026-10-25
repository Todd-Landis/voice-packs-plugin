package com.voicepacks.audio;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One voice channel on top of RuneLite's {@link AudioPlayer} (the Plugin Hub doesn't allow javax.sound directly).
 * AudioPlayer can't stop a clip or say when it ends, so the channel reads each clip's length from its WAV header and
 * keeps track of when the voice is free: a line that arrives while another is playing waits for it (QUEUE), is
 * skipped (DROP) or plays over it (OVERLAP). At most one line waits; a more important one replaces it.
 */
@Singleton
public class VoicePlayer
{
	private static final Logger log = LoggerFactory.getLogger(VoicePlayer.class);

	public enum Overlap
	{
		QUEUE, DROP, OVERLAP
	}

	private static final class Line
	{
		final Filepath file;
		final float gainDb;
		final int priority;

		Line(Filepath file, float gainDb, int priority)
		{
			this.file = file;
			this.gainDb = gainDb;
			this.priority = priority;
		}
	}

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private AudioPlayer audioPlayer;

	private long busyUntilMs;
	private Line waiting;

	/** @param gainDb total gain (master volume, pack volume and per-line trim combined) */
	public void play(Filepath file, float gainDb, int priority, Overlap overlap)
	{
		executor.execute(() -> request(new Line(file, gainDb, priority), overlap));
	}

	/** Forgets the waiting line. A line already playing finishes (clips are a few seconds long). */
	public synchronized void stop()
	{
		waiting = null;
	}

	private synchronized void request(Line line, Overlap overlap)
	{
		long now = System.currentTimeMillis();
		if (now >= busyUntilMs || overlap == Overlap.OVERLAP)
		{
			start(line, now);
		}
		else if (overlap == Overlap.QUEUE && (waiting == null || line.priority >= waiting.priority))
		{
			waiting = line;
			executor.schedule(this::playWaiting, busyUntilMs - now, TimeUnit.MILLISECONDS);
		}
	}

	private synchronized void playWaiting()
	{
		long now = System.currentTimeMillis();
		if (waiting == null || now < busyUntilMs)
		{
			return;     // already played, or a later line moved the end; that line's own schedule plays it
		}
		Line line = waiting;
		waiting = null;
		start(line, now);
	}

	private void start(Line line, long now)
	{
		long lengthMs;
		try (InputStream in = line.file.openInputStream())
		{
			lengthMs = wavLengthMs(in);
		}
		catch (IOException e)
		{
			lengthMs = 0;
			log.debug("No length for {}: {}", line.file, e.getMessage());
		}
		try
		{
			audioPlayer.play(line.file, line.gainDb);
		}
		catch (Exception e)
		{
			log.warn("Could not play {}: {}", line.file, e.getMessage());
			return;
		}
		busyUntilMs = Math.max(busyUntilMs, now + lengthMs);
	}

	/** The length of a RIFF/WAVE file from its header: the data chunk's size over the fmt chunk's byte rate. */
	static long wavLengthMs(InputStream raw) throws IOException
	{
		DataInputStream in = new DataInputStream(new BufferedInputStream(raw));
		if (!"RIFF".equals(fourCc(in)))
		{
			throw new IOException("not a RIFF file");
		}
		in.readInt();
		if (!"WAVE".equals(fourCc(in)))
		{
			throw new IOException("not a WAVE file");
		}
		long byteRate = 0;
		while (true)
		{
			String chunk = fourCc(in);
			long size = Integer.reverseBytes(in.readInt()) & 0xffffffffL;
			if (chunk.equals("fmt ") && size >= 16)
			{
				skip(in, 8);                // format, channels, sample rate
				byteRate = Integer.reverseBytes(in.readInt()) & 0xffffffffL;
				skip(in, size - 12 + (size & 1));
			}
			else if (chunk.equals("data"))
			{
				if (byteRate == 0)
				{
					throw new IOException("data before fmt");
				}
				return size * 1000 / byteRate;
			}
			else
			{
				skip(in, size + (size & 1));
			}
		}
	}

	private static String fourCc(DataInputStream in) throws IOException
	{
		byte[] b = new byte[4];
		in.readFully(b);
		return new String(b, StandardCharsets.US_ASCII);
	}

	private static void skip(InputStream in, long n) throws IOException
	{
		while (n > 0)
		{
			long s = in.skip(n);
			if (s <= 0)
			{
				if (in.read() < 0)
				{
					throw new EOFException();
				}
				s = 1;
			}
			n -= s;
		}
	}
}
