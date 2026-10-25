package com.voicepacks.pack;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.util.Filepath;

/**
 * Installs a pack zip (as made by `vpack zip`: one top folder named after the pack id, holding voicepack.json and
 * audio/*.wav) into the packs folder. Anything else in the zip is ignored; sizes are counted while unpacking, not
 * taken from the zip's headers. The pack is unpacked into a hidden folder first and only replaces an installed
 * pack of the same id once its manifest reads.
 */
@Singleton
public class PackImporter
{
	private static final Pattern ENTRY = Pattern.compile("([a-z0-9][a-z0-9-]{0,63})/(voicepack\\.json|audio/[A-Za-z0-9._-]+\\.wav)");
	private static final long MAX_MANIFEST_BYTES = 4L << 20;
	private static final long MAX_PACK_BYTES = 600L << 20;
	private static final int MAX_FILES = 5000;

	@Inject
	private Gson gson;

	public PackImporter()
	{
	}

	PackImporter(Gson gson)
	{
		this.gson = gson;
	}

	/** @return the installed pack's manifest */
	public PackManifest importZip(Filepath zip, Filepath packsDir) throws IOException
	{
		if (!packsDir.exists())
		{
			packsDir.createDirectories();
		}
		Filepath staging = packsDir.createTempDir(".import-");
		try
		{
			String id = unpack(zip, staging);
			Filepath unpacked = staging.joinSegment(id);
			PackManifest m = readManifest(unpacked);
			if (!id.equals(m.id))
			{
				throw new IOException("the zip's folder is " + id + " but the pack's id is " + m.id);
			}
			Filepath dest = packsDir.joinSegment(id);
			if (dest.exists())
			{
				dest.deleteRecursively();
			}
			unpacked.moveTo(dest);
			return m;
		}
		finally
		{
			staging.deleteRecursively();
		}
	}

	private String unpack(Filepath zip, Filepath staging) throws IOException
	{
		String id = null;
		long total = 0;
		int files = 0;
		try (InputStream in = zip.openInputStream(); ZipInputStream z = new ZipInputStream(in))
		{
			for (ZipEntry e; (e = z.getNextEntry()) != null; )
			{
				Matcher match = ENTRY.matcher(e.getName());
				if (e.isDirectory() || !match.matches())
				{
					continue;
				}
				if (id == null)
				{
					id = match.group(1);
				}
				else if (!id.equals(match.group(1)))
				{
					throw new IOException("the zip holds more than one pack");
				}
				if (++files > MAX_FILES)
				{
					throw new IOException("too many files");
				}
				String rel = match.group(2);
				long limit = rel.equals("voicepack.json") ? MAX_MANIFEST_BYTES : PackLoader.MAX_CLIP_BYTES;
				Filepath out = staging.join(id, rel.split("/"));
				out.getParent().createDirectories();
				total += copy(z, out, limit, rel);
				if (total > MAX_PACK_BYTES)
				{
					throw new IOException("the pack is larger than " + (MAX_PACK_BYTES >> 20) + " MB");
				}
			}
		}
		if (id == null)
		{
			throw new IOException("no voice pack in this zip (expected <pack>/voicepack.json)");
		}
		return id;
	}

	private static long copy(InputStream in, Filepath out, long limit, String name) throws IOException
	{
		byte[] buf = new byte[64 * 1024];
		long n = 0;
		try (OutputStream os = out.openOutputStream())
		{
			for (int r; (r = in.read(buf)) > 0; )
			{
				n += r;
				if (n > limit)
				{
					throw new IOException(name + " is too large");
				}
				os.write(buf, 0, r);
			}
		}
		return n;
	}

	private PackManifest readManifest(Filepath dir) throws IOException
	{
		Filepath f = dir.joinSegment("voicepack.json");
		if (!f.isFile())
		{
			throw new IOException("the zip has no voicepack.json");
		}
		try (Reader r = f.openBufferedReader())
		{
			PackManifest m = gson.fromJson(r, PackManifest.class);
			if (m == null || m.format != PackManifest.FORMAT || m.id == null)
			{
				throw new IOException("not a voice pack this plugin can read");
			}
			return m;
		}
		catch (JsonParseException e)
		{
			throw new IOException("voicepack.json is unreadable: " + e.getMessage());
		}
	}
}
