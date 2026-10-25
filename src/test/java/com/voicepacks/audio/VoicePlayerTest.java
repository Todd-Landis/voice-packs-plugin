package com.voicepacks.audio;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class VoicePlayerTest
{
	private static byte[] chunk(String id, byte[] body)
	{
		ByteBuffer b = ByteBuffer.allocate(8 + body.length + (body.length & 1)).order(ByteOrder.LITTLE_ENDIAN);
		b.put(id.getBytes(StandardCharsets.US_ASCII)).putInt(body.length).put(body);
		return b.array();
	}

	private static byte[] wav(int rate, int channels, int samples, byte[]... extraChunks) throws IOException
	{
		ByteBuffer fmt = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
		fmt.putShort((short) 1).putShort((short) channels).putInt(rate).putInt(rate * channels * 2)
			.putShort((short) (channels * 2)).putShort((short) 16);
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write("WAVE".getBytes(StandardCharsets.US_ASCII));
		body.write(chunk("fmt ", fmt.array()));
		for (byte[] c : extraChunks)
		{
			body.write(c);
		}
		body.write(chunk("data", new byte[samples * channels * 2]));
		return chunk("RIFF", body.toByteArray());
	}

	private static long length(byte[] wav) throws IOException
	{
		return VoicePlayer.wavLengthMs(new ByteArrayInputStream(wav));
	}

	@Test
	public void readsTheLengthFromTheHeader() throws IOException
	{
		assertEquals(2000, length(wav(24000, 1, 48000)));
		assertEquals(1500, length(wav(44100, 1, 66150)));
		assertEquals(1000, length(wav(44100, 2, 44100)));
	}

	@Test
	public void skipsOtherChunksIncludingOddSizedOnes() throws IOException
	{
		assertEquals(500, length(wav(24000, 1, 12000, chunk("LIST", new byte[27]), chunk("fact", new byte[4]))));
	}

	@Test
	public void matchesAPackerClip() throws IOException
	{
		Path built = Path.of("../out/system-ai/audio");
		if (!Files.isDirectory(built))
		{
			return;
		}
		try (var clips = Files.list(built))
		{
			Path clip = clips.findFirst().orElseThrow();
			long ms = VoicePlayer.wavLengthMs(Files.newInputStream(clip));
			long fromSize = (Files.size(clip) - 44) * 1000 / (44100 * 2);
			assertEquals(fromSize, ms, 5);
		}
	}
}
