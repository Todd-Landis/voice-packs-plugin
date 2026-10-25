package com.voicepacks.pack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.runelite.client.util.Filepath;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PackImporterTest
{
	private static final String MANIFEST = "{\"format\": 1, \"id\": \"announcer\", \"name\": \"Announcer\", "
		+ "\"version\": \"0.1.0\", \"triggers\": []}";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final PackImporter importer = new PackImporter(new Gson());
	private Path packs;

	@Before
	public void setUp() throws IOException
	{
		packs = tmp.newFolder("packs").toPath();
	}

	private Filepath zip(Map<String, byte[]> entries) throws IOException
	{
		Path z = tmp.newFile().toPath();
		try (OutputStream os = Files.newOutputStream(z); ZipOutputStream out = new ZipOutputStream(os))
		{
			for (Map.Entry<String, byte[]> e : entries.entrySet())
			{
				out.putNextEntry(new ZipEntry(e.getKey()));
				out.write(e.getValue());
				out.closeEntry();
			}
		}
		return Filepath.Unchecked.getRooted(z);
	}

	private Map<String, byte[]> pack(String id)
	{
		Map<String, byte[]> m = new LinkedHashMap<>();
		m.put(id + "/voicepack.json", MANIFEST.replace("announcer", id).getBytes(StandardCharsets.UTF_8));
		m.put(id + "/audio/levelup-1-abc.wav", new byte[1000]);
		return m;
	}

	private PackManifest install(Map<String, byte[]> entries) throws IOException
	{
		return importer.importZip(zip(entries), Filepath.Unchecked.getRooted(packs));
	}

	@Test
	public void installsAVpackZip() throws IOException
	{
		PackManifest m = install(pack("announcer"));
		assertEquals("Announcer", m.name);
		assertTrue(Files.isRegularFile(packs.resolve("announcer/voicepack.json")));
		assertEquals(1000, Files.size(packs.resolve("announcer/audio/levelup-1-abc.wav")));
		try (var s = Files.list(packs))
		{
			assertEquals("no staging folder left behind", 1, s.count());
		}
	}

	@Test
	public void replacesAnInstalledPackWhole() throws IOException
	{
		install(pack("announcer"));
		Map<String, byte[]> v2 = pack("announcer");
		v2.remove("announcer/audio/levelup-1-abc.wav");
		v2.put("announcer/audio/death-1-def.wav", new byte[10]);
		install(v2);
		assertFalse(Files.exists(packs.resolve("announcer/audio/levelup-1-abc.wav")));
		assertTrue(Files.exists(packs.resolve("announcer/audio/death-1-def.wav")));
	}

	@Test
	public void ignoresAnythingButTheManifestAndClips() throws IOException
	{
		Map<String, byte[]> entries = pack("announcer");
		entries.put("../evil.wav", new byte[1]);
		entries.put("announcer/../../evil.wav", new byte[1]);
		entries.put("announcer/audio/run.sh", new byte[1]);
		entries.put("announcer/plugin.jar", new byte[1]);
		install(entries);
		assertFalse(Files.exists(tmp.getRoot().toPath().resolve("evil.wav")));
		assertFalse(Files.exists(packs.resolve("announcer/audio/run.sh")));
		assertFalse(Files.exists(packs.resolve("announcer/plugin.jar")));
	}

	@Test
	public void refusesBadZips() throws IOException
	{
		Map<String, byte[]> two = pack("announcer");
		two.putAll(pack("other"));
		expectFailure(two, "more than one pack");

		Map<String, byte[]> big = pack("announcer");
		big.put("announcer/audio/huge-1-abc.wav", new byte[(int) PackLoader.MAX_CLIP_BYTES + 1]);
		expectFailure(big, "too large");

		Map<String, byte[]> wrongId = new LinkedHashMap<>();
		wrongId.put("renamed/voicepack.json", MANIFEST.getBytes(StandardCharsets.UTF_8));
		expectFailure(wrongId, "id is announcer");

		expectFailure(Map.of("readme.txt", new byte[1]), "no voice pack");
		assertFalse("nothing installed by a failed import", Files.exists(packs.resolve("announcer")));
	}

	private void expectFailure(Map<String, byte[]> entries, String message) throws IOException
	{
		try
		{
			install(entries);
			fail("expected: " + message);
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains(message));
		}
	}
}
