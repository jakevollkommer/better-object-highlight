/*
 * Copyright (c) 2026, Jake Vollkommer
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.betterobjecthighlight;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

/**
 * A small About panel: what the plugin does, where to suggest improvements, and a
 * support button — the config panel cannot host clickable buttons, so they live here.
 */
class BetterObjectHighlightPanel extends PluginPanel
{
	private static final String ISSUES_URL = "https://github.com/jakevollkommer/better-object-highlight/issues";
	private static final String SUPPORT_URL = "https://ko-fi.com/jakevollkommer";

	BetterObjectHighlightPanel()
	{
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		setLayout(new BorderLayout(0, 12));

		JLabel title = new JLabel("Better Object Highlight");
		title.setForeground(ColorScheme.BRAND_ORANGE);

		JLabel about = new JLabel("<html>Highlights game objects by ID or name with per-style lists (hull, outline, clickbox, tile), and can hide objects from rendering entirely."
			+ "<br><br>Ideas and bug reports are encouraged!</html>");

		JButton suggestButton = new JButton("Suggest a feature");
		suggestButton.setToolTipText("Open a GitHub issue with your idea or bug report");
		suggestButton.addActionListener(event -> LinkBrowser.browse(ISSUES_URL));

		JButton supportButton = new JButton("Buy me a coffee",
			new ImageIcon(ImageUtil.loadImageResource(BetterObjectHighlightPlugin.class, "heart.png")));
		supportButton.setToolTipText("Enjoying Better Object Highlight? Support development :)");
		supportButton.addActionListener(event -> LinkBrowser.browse(SUPPORT_URL));

		JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 8));
		buttons.setOpaque(false);
		buttons.add(suggestButton);
		buttons.add(supportButton);

		JPanel content = new JPanel(new BorderLayout(0, 12));
		content.setOpaque(false);
		content.add(title, BorderLayout.NORTH);
		content.add(about, BorderLayout.CENTER);
		content.add(buttons, BorderLayout.SOUTH);
		content.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, 200));

		add(content, BorderLayout.NORTH);
	}
}
