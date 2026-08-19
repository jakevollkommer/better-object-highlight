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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;
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
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "Better Object Highlight",
	description = "Highlight game objects by ID or name with per-style lists, and hide objects from rendering entirely",
	tags = {"object", "objects", "highlight", "hide", "entity", "hider", "indicator", "marker"}
)
public class BetterObjectHighlightPlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;

	private NavigationButton navigationButton;

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

		navigationButton = NavigationButton.builder()
			.tooltip("Better Object Highlight")
			.icon(ImageUtil.loadImageResource(BetterObjectHighlightPlugin.class, "panel_icon.png"))
			.priority(9)
			.panel(new BetterObjectHighlightPanel())
			.build();
		clientToolbar.addNavigation(navigationButton);
		clientThread.invokeLater(this::rescanSceneIfLoggedIn);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navigationButton);
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

		boolean shouldHide = presetFor(hideMatcher, spawnedObject, composition) != null;
		if (shouldHide)
		{
			removeFromScene(spawnedObject, tile);
			return;
		}

		Map<HighlightStyle, Integer> matchedStyles = matchingStyles(spawnedObject, composition);
		if (matchedStyles.isEmpty())
		{
			return;
		}

		boolean isMultiloc = composition.getImpostorIds() != null;
		highlightedObjects.add(new HighlightedObject(spawnedObject, composition, matchedStyles, isMultiloc));
	}

	private Map<HighlightStyle, Integer> matchingStyles(TileObject spawnedObject, ObjectComposition composition)
	{
		Map<HighlightStyle, Integer> presetByStyle = new EnumMap<>(HighlightStyle.class);
		Arrays.stream(HighlightStyle.values())
			.forEach(style -> putStyleIfMatched(presetByStyle, style, spawnedObject, composition));
		return presetByStyle;
	}

	private void putStyleIfMatched(Map<HighlightStyle, Integer> presetByStyle, HighlightStyle style,
		TileObject spawnedObject, ObjectComposition composition)
	{
		Integer preset = presetFor(matcherFor(style), spawnedObject, composition);
		if (preset == null)
		{
			return;
		}

		presetByStyle.put(style, preset);
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

	/**
	 * The preset color index of the entry matching this object (0 = the style's default
	 * colors), or null if no entry in the matcher matches it.
	 */
	@Nullable
	private Integer presetFor(StyleMatcher matcher, TileObject spawnedObject, ObjectComposition composition)
	{
		if (matcher.isEmpty())
		{
			return null;
		}

		Integer idPreset = matcher.presetForId(spawnedObject.getId());
		if (idPreset != null)
		{
			return idPreset;
		}

		Integer namePreset = matcher.presetForName(composition.getName());
		if (namePreset != null)
		{
			return namePreset;
		}

		return presetForAnyImpostor(matcher, composition);
	}

	@Nullable
	private Integer presetForAnyImpostor(StyleMatcher matcher, ObjectComposition composition)
	{
		int[] impostorIds = composition.getImpostorIds();
		if (impostorIds == null)
		{
			return null;
		}

		return Arrays.stream(impostorIds)
			.filter(impostorId -> impostorId != -1)
			.mapToObj(impostorId -> presetForImpostor(matcher, impostorId))
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null);
	}

	@Nullable
	private Integer presetForImpostor(StyleMatcher matcher, int impostorId)
	{
		Integer idPreset = matcher.presetForId(impostorId);
		if (idPreset != null)
		{
			return idPreset;
		}

		// id check first: the name check costs an object definition lookup
		if (!matcher.hasNamePatterns())
		{
			return null;
		}

		return matcher.presetForName(client.getObjectDefinition(impostorId).getName());
	}

	/**
	 * Styles to render right now for a tracked object, each mapped to its preset color index.
	 * For multilocs the effective object varies with varbits, so re-resolve the impostor each
	 * frame. Must run on the client thread.
	 */
	Map<HighlightStyle, Integer> stylesToRender(HighlightedObject highlighted)
	{
		if (!highlighted.isMultiloc())
		{
			return highlighted.getPresetByStyle();
		}

		ObjectComposition impostor = highlighted.getBaseComposition().getImpostor();
		if (impostor == null)
		{
			return Collections.emptyMap();
		}

		int baseObjectId = highlighted.getTileObject().getId();
		Map<HighlightStyle, Integer> presetByStyle = new EnumMap<>(HighlightStyle.class);
		Arrays.stream(HighlightStyle.values())
			.forEach(style -> putMultilocStyleIfMatched(presetByStyle, style, baseObjectId, impostor));
		return presetByStyle;
	}

	private void putMultilocStyleIfMatched(Map<HighlightStyle, Integer> presetByStyle, HighlightStyle style,
		int baseObjectId, ObjectComposition impostor)
	{
		Integer preset = multilocPreset(matcherFor(style), baseObjectId, impostor);
		if (preset == null)
		{
			return;
		}

		presetByStyle.put(style, preset);
	}

	@Nullable
	private static Integer multilocPreset(StyleMatcher matcher, int baseObjectId, ObjectComposition impostor)
	{
		Integer basePreset = matcher.presetForId(baseObjectId);
		if (basePreset != null)
		{
			return basePreset;
		}

		Integer impostorPreset = matcher.presetForId(impostor.getId());
		if (impostorPreset != null)
		{
			return impostorPreset;
		}

		return matcher.presetForName(impostor.getName());
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
