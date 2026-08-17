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
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.Stroke;
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
import static com.betterobjecthighlight.HighlightedObject.HF_CLICKBOX;
import static com.betterobjecthighlight.HighlightedObject.HF_HULL;
import static com.betterobjecthighlight.HighlightedObject.HF_OUTLINE;
import static com.betterobjecthighlight.HighlightedObject.HF_TILE;

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
		var objects = plugin.getObjects();
		if (objects.isEmpty())
		{
			return null;
		}

		WorldView toplevel = client.getTopLevelWorldView();
		Stroke stroke = borderStroke();
		// enhanced for rather than a stream: this is the per-frame hot path
		for (HighlightedObject highlighted : objects)
		{
			TileObject object = highlighted.getTileObject();
			if (!isRenderable(object, toplevel))
			{
				continue;
			}

			int flags = plugin.renderFlags(highlighted);
			if (flags == 0)
			{
				continue;
			}

			drawHighlights(graphics, object, flags, stroke);
		}

		return null;
	}

	private static boolean isRenderable(TileObject object, WorldView toplevel)
	{
		WorldView worldView = object.getWorldView();
		boolean isOnActivePlane = worldView != null && object.getPlane() == worldView.getPlane();
		if (!isOnActivePlane)
		{
			return false;
		}

		WorldEntity worldEntity = toplevel.worldEntities().byIndex(worldView.getId());
		boolean isHiddenForOverlap = worldEntity != null && worldEntity.isHiddenForOverlap();
		return !isHiddenForOverlap;
	}

	private void drawHighlights(Graphics2D graphics, TileObject object, int flags, Stroke stroke)
	{
		if ((flags & HF_HULL) != 0)
		{
			renderConvexHull(graphics, object, stroke);
		}

		if ((flags & HF_OUTLINE) != 0)
		{
			modelOutlineRenderer.drawOutline(object, config.outlineWidth(), config.outlineColor(), config.outlineFeather());
		}

		if ((flags & HF_CLICKBOX) != 0)
		{
			renderClickbox(graphics, object, stroke);
		}

		if ((flags & HF_TILE) != 0)
		{
			renderTile(graphics, object, stroke);
		}
	}

	private void renderConvexHull(Graphics2D graphics, TileObject object, Stroke stroke)
	{
		hullShapes(object)
			.filter(Objects::nonNull)
			.forEach(hull -> OverlayUtil.renderPolygon(graphics, hull, config.hullColor(), config.hullFillColor(), stroke));
	}

	private static Stream<Shape> hullShapes(TileObject object)
	{
		if (object instanceof GameObject)
		{
			return Stream.of(((GameObject) object).getConvexHull());
		}
		if (object instanceof WallObject)
		{
			return Stream.of(((WallObject) object).getConvexHull(), ((WallObject) object).getConvexHull2());
		}
		if (object instanceof DecorativeObject)
		{
			return Stream.of(((DecorativeObject) object).getConvexHull(), ((DecorativeObject) object).getConvexHull2());
		}
		if (object instanceof GroundObject)
		{
			return Stream.of(((GroundObject) object).getConvexHull());
		}
		return Stream.of(object.getCanvasTilePoly());
	}

	private void renderClickbox(Graphics2D graphics, TileObject object, Stroke stroke)
	{
		Shape clickbox = object.getClickbox();
		if (clickbox == null)
		{
			return;
		}

		OverlayUtil.renderPolygon(graphics, clickbox, config.clickboxColor(), config.clickboxFillColor(), stroke);
	}

	private void renderTile(Graphics2D graphics, TileObject object, Stroke stroke)
	{
		Polygon tilePoly = object.getCanvasTilePoly();
		if (tilePoly == null)
		{
			return;
		}

		OverlayUtil.renderPolygon(graphics, tilePoly, config.tileColor(), config.tileFillColor(), stroke);
	}

	private Stroke borderStroke()
	{
		float width = (float) config.borderWidth();
		boolean strokeIsCurrent = cachedStroke != null && cachedStrokeWidth == width;
		if (!strokeIsCurrent)
		{
			cachedStrokeWidth = width;
			cachedStroke = new BasicStroke(width);
		}

		return cachedStroke;
	}
}
