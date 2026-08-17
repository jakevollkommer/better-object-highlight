/*
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
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

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Better Object Highlight",
	description = "Highlight game objects by ID or name with per-style lists, and hide objects from rendering entirely",
	tags = {"object", "objects", "highlight", "hide", "entity", "hider", "indicator", "marker"}
)
public class BetterObjectHighlightPlugin extends Plugin
{
	@Getter(AccessLevel.PACKAGE)
	private final List<HighlightedObject> highlightedObjects = new ArrayList<>();

	private final Map<HighlightStyle, StyleMatcher> styleMatchers = new EnumMap<>(HighlightStyle.class);
	private StyleMatcher hideMatcher = StyleMatcher.EMPTY;
	// cached so the per-spawn fast path stays allocation-free during scene loads
	private boolean anyMatcherActive;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BetterObjectHighlightOverlay overlay;

	@Inject
	private BetterObjectHighlightConfig config;

	@Provides
	BetterObjectHighlightConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BetterObjectHighlightConfig.class);
	}

	@Override
	protected void startUp()
	{
		rebuildMatchers();
		overlayManager.add(overlay);
		clientThread.invokeLater(this::rescanSceneIfLoggedIn);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		highlightedObjects.clear();

		boolean hiderWasActive = !hideMatcher.isEmpty();
		clearMatchers();
		if (hiderWasActive)
		{
			// restore anything the entity hider removed from the scene
			clientThread.invokeLater(this::reloadScene);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			highlightedObjects.clear();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BetterObjectHighlightConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		StyleMatcher previousHideMatcher = hideMatcher;
		rebuildMatchers();
		// If something was removed from the hide list (or the hider turned off), the only way
		// to bring removed objects back is a scene reload; otherwise a rescan is enough.
		boolean needsSceneReload = previousHideMatcher.matchesAnythingNotIn(hideMatcher);
		clientThread.invokeLater(() -> applyConfigChange(needsSceneReload));
	}

	private void applyConfigChange(boolean needsSceneReload)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (needsSceneReload)
		{
			reloadScene();
			return;
		}

		rescanScene();
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		handleSpawn(event.getGameObject(), event.getTile());
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		handleSpawn(event.getWallObject(), event.getTile());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		handleSpawn(event.getDecorativeObject(), event.getTile());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		handleSpawn(event.getGroundObject(), event.getTile());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		stopTracking(event.getGameObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		stopTracking(event.getWallObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		stopTracking(event.getDecorativeObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		stopTracking(event.getGroundObject());
	}

	private void stopTracking(TileObject despawnedObject)
	{
		highlightedObjects.removeIf(tracked -> tracked.getTileObject() == despawnedObject);
	}

	private void handleSpawn(TileObject spawnedObject, Tile tile)
	{
		if (!anyMatcherActive)
		{
			return;
		}

		ObjectComposition composition = client.getObjectDefinition(spawnedObject.getId());

		boolean shouldHide = !hideMatcher.isEmpty() && matches(hideMatcher, spawnedObject, composition);
		if (shouldHide)
		{
			removeFromScene(spawnedObject, tile);
			return;
		}

		Set<HighlightStyle> matchedStyles = matchingStyles(spawnedObject, composition);
		if (matchedStyles.isEmpty())
		{
			return;
		}

		boolean isMultiloc = composition.getImpostorIds() != null;
		highlightedObjects.add(new HighlightedObject(spawnedObject, composition, matchedStyles, isMultiloc));
	}

	private Set<HighlightStyle> matchingStyles(TileObject spawnedObject, ObjectComposition composition)
	{
		return Arrays.stream(HighlightStyle.values())
			.filter(style -> matches(matcherFor(style), spawnedObject, composition))
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(HighlightStyle.class)));
	}

	private StyleMatcher matcherFor(HighlightStyle style)
	{
		return styleMatchers.getOrDefault(style, StyleMatcher.EMPTY);
	}

	private void removeFromScene(TileObject hiddenObject, Tile tile)
	{
		WorldView worldView = hiddenObject.getWorldView();
		if (worldView == null)
		{
			return;
		}

		Scene scene = worldView.getScene();
		if (hiddenObject instanceof GameObject)
		{
			scene.removeGameObject((GameObject) hiddenObject);
			return;
		}

		// Wall, decorative and ground objects have no individual removal API;
		// removing the tile takes everything on it with it.
		scene.removeTile(tile);
	}

	private boolean matches(StyleMatcher matcher, TileObject spawnedObject, ObjectComposition composition)
	{
		if (matcher.isEmpty())
		{
			return false;
		}

		boolean matchesBaseObject = matcher.matchesId(spawnedObject.getId())
			|| matcher.matchesName(composition.getName());
		return matchesBaseObject || matchesAnyImpostor(matcher, composition);
	}

	private boolean matchesAnyImpostor(StyleMatcher matcher, ObjectComposition composition)
	{
		int[] impostorIds = composition.getImpostorIds();
		if (impostorIds == null)
		{
			return false;
		}

		return Arrays.stream(impostorIds)
			.filter(impostorId -> impostorId != -1)
			.anyMatch(impostorId -> matchesImpostor(matcher, impostorId));
	}

	private boolean matchesImpostor(StyleMatcher matcher, int impostorId)
	{
		// id check first: the name check costs an object definition lookup
		return matcher.matchesId(impostorId)
			|| (matcher.hasNamePatterns() && matcher.matchesName(client.getObjectDefinition(impostorId).getName()));
	}

	/**
	 * Styles to render right now for a tracked object. For multilocs the effective object
	 * varies with varbits, so re-resolve the impostor each frame. Must run on the client thread.
	 */
	Set<HighlightStyle> stylesToRender(HighlightedObject highlighted)
	{
		if (!highlighted.isMultiloc())
		{
			return highlighted.getStyles();
		}

		ObjectComposition impostor = highlighted.getBaseComposition().getImpostor();
		if (impostor == null)
		{
			return Collections.emptySet();
		}

		int baseObjectId = highlighted.getTileObject().getId();
		return Arrays.stream(HighlightStyle.values())
			.filter(style -> multilocMatches(matcherFor(style), baseObjectId, impostor))
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(HighlightStyle.class)));
	}

	private static boolean multilocMatches(StyleMatcher matcher, int baseObjectId, ObjectComposition impostor)
	{
		return matcher.matchesId(baseObjectId)
			|| matcher.matchesId(impostor.getId())
			|| matcher.matchesName(impostor.getName());
	}

	private void rescanSceneIfLoggedIn()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		rescanScene();
	}

	private void rescanScene()
	{
		highlightedObjects.clear();

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}

		Arrays.stream(worldView.getScene().getTiles())
			.flatMap(Arrays::stream)
			.flatMap(Arrays::stream)
			.filter(Objects::nonNull)
			.forEach(this::scanTile);
	}

	private void scanTile(Tile tile)
	{
		Stream.of(tile.getWallObject(), tile.getDecorativeObject(), tile.getGroundObject())
			.filter(Objects::nonNull)
			.forEach(tileObject -> handleSpawn(tileObject, tile));

		Arrays.stream(tile.getGameObjects())
			.filter(Objects::nonNull)
			.filter(gameObject -> isPrimaryTile(gameObject, tile))
			.forEach(gameObject -> handleSpawn(gameObject, tile));
	}

	/**
	 * Game objects can span multiple tiles; only handle them from their south-west tile.
	 */
	private static boolean isPrimaryTile(GameObject gameObject, Tile tile)
	{
		return gameObject.getSceneMinLocation().equals(tile.getSceneLocation());
	}

	private void reloadScene()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			client.setGameState(GameState.LOADING);
		}
	}

	private void rebuildMatchers()
	{
		styleMatchers.put(HighlightStyle.HULL,
			matcherFromConfig(config.hullHighlight(), config.hullIds(), config.hullNames()));
		styleMatchers.put(HighlightStyle.OUTLINE,
			matcherFromConfig(config.outlineHighlight(), config.outlineIds(), config.outlineNames()));
		styleMatchers.put(HighlightStyle.CLICKBOX,
			matcherFromConfig(config.clickboxHighlight(), config.clickboxIds(), config.clickboxNames()));
		styleMatchers.put(HighlightStyle.TILE,
			matcherFromConfig(config.tileHighlight(), config.tileIds(), config.tileNames()));
		hideMatcher = matcherFromConfig(config.entityHiderToggle(), config.entityHiderIds(), config.entityHiderNames());

		anyMatcherActive = !hideMatcher.isEmpty()
			|| styleMatchers.values().stream().anyMatch(matcher -> !matcher.isEmpty());
	}

	private static StyleMatcher matcherFromConfig(boolean enabled, String idList, String nameList)
	{
		return enabled ? StyleMatcher.fromConfigLists(idList, nameList) : StyleMatcher.EMPTY;
	}

	private void clearMatchers()
	{
		styleMatchers.clear();
		hideMatcher = StyleMatcher.EMPTY;
		anyMatcherActive = false;
	}
}
