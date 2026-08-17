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
		Stroke stroke = new BasicStroke((float) config.borderWidth());
		for (HighlightedObject obj : objects)
		{
			TileObject object = obj.getTileObject();
			WorldView wv = object.getWorldView();

			if (wv == null || object.getPlane() != wv.getPlane())
			{
				continue;
			}

			WorldEntity we = toplevel.worldEntities().byIndex(wv.getId());
			if (we != null && we.isHiddenForOverlap())
			{
				continue;
			}

			final int flags = plugin.renderFlags(obj);
			if (flags == 0)
			{
				continue;
			}

			if ((flags & HF_HULL) != 0)
			{
				renderConvexHull(graphics, object, config.hullColor(), config.hullFillColor(), stroke);
			}

			if ((flags & HF_OUTLINE) != 0)
			{
				modelOutlineRenderer.drawOutline(object, config.outlineWidth(), config.outlineColor(), config.outlineFeather());
			}

			if ((flags & HF_CLICKBOX) != 0)
			{
				Shape clickbox = object.getClickbox();
				if (clickbox != null)
				{
					OverlayUtil.renderPolygon(graphics, clickbox, config.clickboxColor(), config.clickboxFillColor(), stroke);
				}
			}

			if ((flags & HF_TILE) != 0)
			{
				Polygon tilePoly = object.getCanvasTilePoly();
				if (tilePoly != null)
				{
					OverlayUtil.renderPolygon(graphics, tilePoly, config.tileColor(), config.tileFillColor(), stroke);
				}
			}
		}

		return null;
	}

	private static void renderConvexHull(Graphics2D graphics, TileObject object, Color color, Color fillColor, Stroke stroke)
	{
		final Shape polygon;
		Shape polygon2 = null;

		if (object instanceof GameObject)
		{
			polygon = ((GameObject) object).getConvexHull();
		}
		else if (object instanceof WallObject)
		{
			polygon = ((WallObject) object).getConvexHull();
			polygon2 = ((WallObject) object).getConvexHull2();
		}
		else if (object instanceof DecorativeObject)
		{
			polygon = ((DecorativeObject) object).getConvexHull();
			polygon2 = ((DecorativeObject) object).getConvexHull2();
		}
		else if (object instanceof GroundObject)
		{
			polygon = ((GroundObject) object).getConvexHull();
		}
		else
		{
			polygon = object.getCanvasTilePoly();
		}

		if (polygon != null)
		{
			OverlayUtil.renderPolygon(graphics, polygon, color, fillColor, stroke);
		}

		if (polygon2 != null)
		{
			OverlayUtil.renderPolygon(graphics, polygon2, color, fillColor, stroke);
		}
	}
}
