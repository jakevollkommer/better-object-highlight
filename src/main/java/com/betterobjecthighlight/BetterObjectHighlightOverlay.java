/*
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.Stroke;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

class BetterObjectHighlightOverlay extends Overlay
{
	private final Client client;
	private final BetterObjectHighlightConfig config;
	private final BetterObjectHighlightPlugin plugin;
	private final ModelOutlineRenderer modelOutlineRenderer;

	// render() runs every frame, so reuse the stroke until the configured width changes
	private Stroke cachedStroke;
	private float cachedStrokeWidth;

	@Inject
	private BetterObjectHighlightOverlay(Client client, BetterObjectHighlightConfig config,
		BetterObjectHighlightPlugin plugin, ModelOutlineRenderer modelOutlineRenderer)
	{
		this.client = client;
		this.config = config;
		this.plugin = plugin;
		this.modelOutlineRenderer = modelOutlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<HighlightedObject> highlightedObjects = plugin.getHighlightedObjects();
		if (highlightedObjects.isEmpty())
		{
			return null;
		}

		WorldView topLevelWorldView = client.getTopLevelWorldView();
		Stroke stroke = borderStroke();
		// enhanced for rather than a stream: this is the per-frame hot path
		for (HighlightedObject highlighted : highlightedObjects)
		{
			renderHighlightedObject(graphics, highlighted, topLevelWorldView, stroke);
		}

		return null;
	}

	private void renderHighlightedObject(Graphics2D graphics, HighlightedObject highlighted,
		WorldView topLevelWorldView, Stroke stroke)
	{
		TileObject tileObject = highlighted.getTileObject();
		if (!isRenderable(tileObject, topLevelWorldView))
		{
			return;
		}

		Map<HighlightStyle, Integer> presetByStyle = plugin.stylesToRender(highlighted);
		if (presetByStyle.isEmpty())
		{
			return;
		}

		Integer hullPreset = presetByStyle.get(HighlightStyle.HULL);
		if (hullPreset != null)
		{
			renderConvexHull(graphics, tileObject, stroke, hullPreset);
		}

		Integer outlinePreset = presetByStyle.get(HighlightStyle.OUTLINE);
		if (outlinePreset != null)
		{
			Color outlineColor = borderColor(outlinePreset, config.outlineColor());
			modelOutlineRenderer.drawOutline(tileObject, config.outlineWidth(), outlineColor, config.outlineFeather());
		}

		Integer clickboxPreset = presetByStyle.get(HighlightStyle.CLICKBOX);
		if (clickboxPreset != null)
		{
			renderClickbox(graphics, tileObject, stroke, clickboxPreset);
		}

		Integer tilePreset = presetByStyle.get(HighlightStyle.TILE);
		if (tilePreset != null)
		{
			renderTile(graphics, tileObject, stroke, tilePreset);
		}
	}

	/**
	 * An entry's preset border color, or the style's default when the entry has no preset.
	 */
	private Color borderColor(int preset, Color styleDefault)
	{
		switch (preset)
		{
			case 1: return config.presetColor1();
			case 2: return config.presetColor2();
			case 3: return config.presetColor3();
			case 4: return config.presetColor4();
			case 5: return config.presetColor5();
			default: return styleDefault;
		}
	}

	/**
	 * An entry's preset fill color, or the style's default when the entry has no preset.
	 */
	private Color fillColor(int preset, Color styleDefault)
	{
		switch (preset)
		{
			case 1: return config.presetFillColor1();
			case 2: return config.presetFillColor2();
			case 3: return config.presetFillColor3();
			case 4: return config.presetFillColor4();
			case 5: return config.presetFillColor5();
			default: return styleDefault;
		}
	}

	private static boolean isRenderable(TileObject tileObject, WorldView topLevelWorldView)
	{
		WorldView worldView = tileObject.getWorldView();
		boolean isOnActivePlane = worldView != null && tileObject.getPlane() == worldView.getPlane();
		if (!isOnActivePlane)
		{
			return false;
		}

		WorldEntity worldEntity = topLevelWorldView.worldEntities().byIndex(worldView.getId());
		boolean isHiddenForOverlap = worldEntity != null && worldEntity.isHiddenForOverlap();
		return !isHiddenForOverlap;
	}

	private void renderConvexHull(Graphics2D graphics, TileObject tileObject, Stroke stroke, int preset)
	{
		Color border = borderColor(preset, config.hullColor());
		Color fill = fillColor(preset, config.hullFillColor());
		hullShapes(tileObject)
			.filter(Objects::nonNull)
			.forEach(hull -> OverlayUtil.renderPolygon(graphics, hull, border, fill, stroke));
	}

	private static Stream<Shape> hullShapes(TileObject tileObject)
	{
		if (tileObject instanceof GameObject)
		{
			return Stream.of(((GameObject) tileObject).getConvexHull());
		}
		if (tileObject instanceof WallObject)
		{
			return Stream.of(((WallObject) tileObject).getConvexHull(), ((WallObject) tileObject).getConvexHull2());
		}
		if (tileObject instanceof DecorativeObject)
		{
			return Stream.of(((DecorativeObject) tileObject).getConvexHull(), ((DecorativeObject) tileObject).getConvexHull2());
		}
		if (tileObject instanceof GroundObject)
		{
			return Stream.of(((GroundObject) tileObject).getConvexHull());
		}
		return Stream.of(tileObject.getCanvasTilePoly());
	}

	private void renderClickbox(Graphics2D graphics, TileObject tileObject, Stroke stroke, int preset)
	{
		Shape clickbox = tileObject.getClickbox();
		if (clickbox == null)
		{
			return;
		}

		Color border = borderColor(preset, config.clickboxColor());
		Color fill = fillColor(preset, config.clickboxFillColor());
		OverlayUtil.renderPolygon(graphics, clickbox, border, fill, stroke);
	}

	private void renderTile(Graphics2D graphics, TileObject tileObject, Stroke stroke, int preset)
	{
		Polygon tilePolygon = tileObject.getCanvasTilePoly();
		if (tilePolygon == null)
		{
			return;
		}

		Color border = borderColor(preset, config.tileColor());
		Color fill = fillColor(preset, config.tileFillColor());
		OverlayUtil.renderPolygon(graphics, tilePolygon, border, fill, stroke);
	}

	private Stroke borderStroke()
	{
		float configuredWidth = (float) config.borderWidth();
		boolean strokeIsCurrent = cachedStroke != null && cachedStrokeWidth == configuredWidth;
		if (!strokeIsCurrent)
		{
			cachedStrokeWidth = configuredWidth;
			cachedStroke = new BasicStroke(configuredWidth);
		}

		return cachedStroke;
	}
}
