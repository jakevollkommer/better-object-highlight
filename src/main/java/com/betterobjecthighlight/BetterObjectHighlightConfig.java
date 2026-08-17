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

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(BetterObjectHighlightConfig.GROUP)
public interface BetterObjectHighlightConfig extends Config
{
	String GROUP = "betterobjecthighlight";

	@ConfigSection(
		name = "Hull",
		description = "Highlight the model hull of matching objects",
		position = 0
	)
	String hullSection = "hullSection";

	@ConfigItem(keyName = "hullHighlight", name = "Enable hull", description = "Draw the convex hull of matching objects", section = hullSection, position = 0)
	default boolean hullHighlight()
	{
		return true;
	}

	@ConfigItem(keyName = "hullIds", name = "IDs to highlight", description = "Object IDs to hull-highlight, separated by commas or newlines", section = hullSection, position = 1)
	default String hullIds()
	{
		return "";
	}

	@ConfigItem(keyName = "hullNames", name = "Names to highlight", description = "Object names to hull-highlight, separated by commas or newlines. Supports * wildcards", section = hullSection, position = 2)
	default String hullNames()
	{
		return "";
	}

	@Alpha
	@ConfigItem(keyName = "hullColor", name = "Hull color", description = "Border color for hull highlights", section = hullSection, position = 3)
	default Color hullColor()
	{
		return Color.YELLOW;
	}

	@Alpha
	@ConfigItem(keyName = "hullFillColor", name = "Hull fill color", description = "Fill color for hull highlights", section = hullSection, position = 4)
	default Color hullFillColor()
	{
		return new Color(0, 0, 0, 50);
	}

	@ConfigSection(
		name = "Outline",
		description = "Highlight the model outline of matching objects",
		position = 1
	)
	String outlineSection = "outlineSection";

	@ConfigItem(keyName = "outlineHighlight", name = "Enable outline", description = "Draw the model outline of matching objects", section = outlineSection, position = 0)
	default boolean outlineHighlight()
	{
		return true;
	}

	@ConfigItem(keyName = "outlineIds", name = "IDs to highlight", description = "Object IDs to outline, separated by commas or newlines", section = outlineSection, position = 1)
	default String outlineIds()
	{
		return "";
	}

	@ConfigItem(keyName = "outlineNames", name = "Names to highlight", description = "Object names to outline, separated by commas or newlines. Supports * wildcards", section = outlineSection, position = 2)
	default String outlineNames()
	{
		return "";
	}

	@Alpha
	@ConfigItem(keyName = "outlineColor", name = "Outline color", description = "Color for model outlines", section = outlineSection, position = 3)
	default Color outlineColor()
	{
		return Color.CYAN;
	}

	@Range(min = 1, max = 16)
	@ConfigItem(keyName = "outlineWidth", name = "Outline width", description = "Width of the model outline", section = outlineSection, position = 4)
	default int outlineWidth()
	{
		return 4;
	}

	@Range(max = 4)
	@ConfigItem(keyName = "outlineFeather", name = "Outline feather", description = "Softness of the outline edge", section = outlineSection, position = 5)
	default int outlineFeather()
	{
		return 0;
	}

	@ConfigSection(
		name = "Clickbox",
		description = "Highlight the clickbox of matching objects",
		position = 2
	)
	String clickboxSection = "clickboxSection";

	@ConfigItem(keyName = "clickboxHighlight", name = "Enable clickbox", description = "Draw the clickbox of matching objects", section = clickboxSection, position = 0)
	default boolean clickboxHighlight()
	{
		return true;
	}

	@ConfigItem(keyName = "clickboxIds", name = "IDs to highlight", description = "Object IDs to clickbox-highlight, separated by commas or newlines", section = clickboxSection, position = 1)
	default String clickboxIds()
	{
		return "";
	}

	@ConfigItem(keyName = "clickboxNames", name = "Names to highlight", description = "Object names to clickbox-highlight, separated by commas or newlines. Supports * wildcards", section = clickboxSection, position = 2)
	default String clickboxNames()
	{
		return "";
	}

	@Alpha
	@ConfigItem(keyName = "clickboxColor", name = "Clickbox color", description = "Border color for clickbox highlights", section = clickboxSection, position = 3)
	default Color clickboxColor()
	{
		return Color.GREEN;
	}

	@Alpha
	@ConfigItem(keyName = "clickboxFillColor", name = "Clickbox fill color", description = "Fill color for clickbox highlights", section = clickboxSection, position = 4)
	default Color clickboxFillColor()
	{
		return new Color(0, 255, 0, 20);
	}

	@ConfigSection(
		name = "Tile",
		description = "Highlight the tile of matching objects",
		position = 3
	)
	String tileSection = "tileSection";

	@ConfigItem(keyName = "tileHighlight", name = "Enable tile", description = "Draw the tile under matching objects", section = tileSection, position = 0)
	default boolean tileHighlight()
	{
		return true;
	}

	@ConfigItem(keyName = "tileIds", name = "IDs to highlight", description = "Object IDs to tile-highlight, separated by commas or newlines", section = tileSection, position = 1)
	default String tileIds()
	{
		return "";
	}

	@ConfigItem(keyName = "tileNames", name = "Names to highlight", description = "Object names to tile-highlight, separated by commas or newlines. Supports * wildcards", section = tileSection, position = 2)
	default String tileNames()
	{
		return "";
	}

	@Alpha
	@ConfigItem(keyName = "tileColor", name = "Tile color", description = "Border color for tile highlights", section = tileSection, position = 3)
	default Color tileColor()
	{
		return Color.MAGENTA;
	}

	@Alpha
	@ConfigItem(keyName = "tileFillColor", name = "Tile fill color", description = "Fill color for tile highlights", section = tileSection, position = 4)
	default Color tileFillColor()
	{
		return new Color(255, 0, 255, 20);
	}

	@ConfigSection(
		name = "Entity hider",
		description = "Remove matching objects from the scene entirely",
		position = 4
	)
	String hiderSection = "hiderSection";

	@ConfigItem(keyName = "entityHiderToggle", name = "Enable entity hider", description = "Remove matching objects from the scene so they are not rendered", section = hiderSection, position = 0)
	default boolean entityHiderToggle()
	{
		return false;
	}

	@ConfigItem(keyName = "entityHiderIds", name = "IDs to hide", description = "Object IDs to hide, separated by commas or newlines", section = hiderSection, position = 1)
	default String entityHiderIds()
	{
		return "";
	}

	@ConfigItem(keyName = "entityHiderNames", name = "Names to hide", description = "Object names to hide, separated by commas or newlines. Supports * wildcards.<br>Note: hiding a wall, decoration or ground object removes everything on its tile", section = hiderSection, position = 2)
	default String entityHiderNames()
	{
		return "";
	}

	@Range(min = 1, max = 8)
	@ConfigItem(keyName = "borderWidth", name = "Border width", description = "Stroke width for hull, clickbox and tile highlights", position = 5)
	default int borderWidth()
	{
		return 2;
	}
}
