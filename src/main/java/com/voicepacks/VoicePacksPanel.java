package com.voicepacks;

import com.voicepacks.pack.PackManifest;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/** Sidebar panel: the installed packs, an Import button for pack zips, and Remove per pack. Swing thread only. */
class VoicePacksPanel extends PluginPanel
{
	private final JPanel list = new JPanel();
	private final JLabel status = new JLabel();
	private final Consumer<String> onRemove;

	VoicePacksPanel(Runnable onImport, Consumer<String> onRemove)
	{
		this.onRemove = onRemove;
		setLayout(new BorderLayout(0, 8));
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel top = new JPanel(new GridLayout(0, 1, 0, 6));
		JLabel title = new JLabel("Voice Packs");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		top.add(title);
		JButton importButton = new JButton("Import pack (.zip)…");
		importButton.setToolTipText("Install a voice pack zip someone sent you");
		importButton.addActionListener(e -> onImport.run());
		top.add(importButton);
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		top.add(status);
		add(top, BorderLayout.NORTH);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		add(list, BorderLayout.CENTER);
		setPacks(List.of());
	}

	void setStatus(String text)
	{
		status.setText("<html>" + escape(text) + "</html>");
	}

	void setPacks(List<PackManifest> packs)
	{
		list.removeAll();
		if (packs.isEmpty())
		{
			JLabel none = new JLabel("<html>No packs installed. Import a pack zip to start.</html>");
			none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			list.add(none);
		}
		for (PackManifest m : packs)
		{
			list.add(row(m));
			list.add(javax.swing.Box.createVerticalStrut(6));
		}
		list.revalidate();
		list.repaint();
	}

	private JPanel row(PackManifest m)
	{
		JPanel row = new JPanel(new BorderLayout(6, 2));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		row.setMaximumSize(new Dimension(PANEL_WIDTH, 80));

		int lines = m.triggers.stream().mapToInt(t -> t.lines.size()).sum();
		boolean personal = m.voice != null && "private".equals(m.voice.distribution);
		JLabel name = new JLabel("<html><b>" + escape(m.name) + "</b> " + escape(m.version) + "<br>"
			+ lines + " lines" + (personal ? " · private, don't share" : "") + "</html>");
		name.setForeground(Color.WHITE);
		name.setToolTipText(m.description);
		row.add(name, BorderLayout.CENTER);

		JButton remove = new JButton("Remove");
		remove.addActionListener(e ->
		{
			int ok = JOptionPane.showConfirmDialog(this, "Remove the " + m.name + " voice pack?", "Voice Packs",
				JOptionPane.OK_CANCEL_OPTION);
			if (ok == JOptionPane.OK_OPTION)
			{
				onRemove.accept(m.id);
			}
		});
		row.add(remove, BorderLayout.EAST);
		return row;
	}

	private static String escape(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
