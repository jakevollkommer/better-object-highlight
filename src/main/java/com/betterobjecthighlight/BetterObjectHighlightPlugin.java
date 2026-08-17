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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
import net.runelite.client.util.Text;
import net.runelite.client.util.WildcardMatcher;
import static com.betterobjecthighlight.HighlightedObject.HF_CLICKBOX;
import static com.betterobjecthighlight.HighlightedObject.HF_HULL;
import static com.betterobjecthighlight.HighlightedObject.HF_OUTLINE;
import static com.betterobjecthighlight.HighlightedObject.HF_TILE;

@PluginDescriptor(
	name = "Better Object Highlight",
	description = "Highlight game objects by ID or name with per-style lists, and hide objects from rendering entirely",
	tags = {"object", "objects", "highlight", "hide", "entity", "hider", "indicator", "marker"}
)
@Slf4j
public class BetterObjectHighlightPlugin extends Plugin
{
	@Getter(AccessLevel.PACKAGE)
	private final List<HighlightedObject> objects = new ArrayList<>();

	private StyleMatcher hullMatcher = StyleMatcher.EMPTY;
	private StyleMatcher outlineMatcher = StyleMatcher.EMPTY;
	private StyleMatcher clickboxMatcher = StyleMatcher.EMPTY;
	private StyleMatcher tileMatcher = StyleMatcher.EMPTY;
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
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				rescanScene();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		objects.clear();
		// restore anything the entity hider removed from the scene
		boolean restoreNeeded = !hideMatcher.isEmpty();
		hullMatcher = outlineMatcher = clickboxMatcher = tileMatcher = hideMatcher = StyleMatcher.EMPTY;
		anyMatcherActive = false;
		if (restoreNeeded)
		{
			clientThread.invokeLater(this::reloadScene);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			objects.clear();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BetterObjectHighlightConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		StyleMatcher previousHide = hideMatcher;
		rebuildMatchers();
		// If something was removed from the hide list (or the hider turned off), the only way
		// to bring removed objects back is a scene reload; otherwise a rescan is enough.
		boolean needsReload = previousHide.matchesAnythingNotIn(hideMatcher);
		clientThread.invokeLater(() -> applyConfigChange(needsReload));
	}

	private void applyConfigChange(boolean needsReload)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (needsReload)
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
		objects.removeIf(o -> o.getTileObject() == event.getGameObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		objects.removeIf(o -> o.getTileObject() == event.getWallObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		objects.removeIf(o -> o.getTileObject() == event.getDecorativeObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		objects.removeIf(o -> o.getTileObject() == event.getGroundObject());
	}

	private void handleSpawn(TileObject object, Tile tile)
	{
		if (!anyMatcherActive)
		{
			return;
		}

		ObjectComposition composition = client.getObjectDefinition(object.getId());

		boolean shouldHide = !hideMatcher.isEmpty() && matches(hideMatcher, object, composition);
		if (shouldHide)
		{
			hideObject(object, tile);
			return;
		}

		int flags =
			(matches(hullMatcher, object, composition) ? HF_HULL : 0) |
			(matches(outlineMatcher, object, composition) ? HF_OUTLINE : 0) |
			(matches(clickboxMatcher, object, composition) ? HF_CLICKBOX : 0) |
			(matches(tileMatcher, object, composition) ? HF_TILE : 0);
		if (flags == 0)
		{
			return;
		}

		boolean isMultiloc = composition.getImpostorIds() != null;
		objects.add(new HighlightedObject(object, composition, flags, isMultiloc));
	}

	private void hideObject(TileObject object, Tile tile)
	{
		WorldView worldView = object.getWorldView();
		if (worldView == null)
		{
			return;
		}

		Scene scene = worldView.getScene();
		if (object instanceof GameObject)
		{
			scene.removeGameObject((GameObject) object);
			return;
		}

		// Wall, decorative and ground objects have no individual removal API;
		// removing the tile takes everything on it with it.
		scene.removeTile(tile);
	}

	private boolean matches(StyleMatcher matcher, TileObject object, ObjectComposition composition)
	{
		if (matcher.isEmpty())
		{
			return false;
		}

		boolean matchesBase = matcher.getIds().contains(object.getId())
			|| matcher.matchesName(composition.getName());
		return matchesBase || matchesAnyImpostor(matcher, composition);
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
		return matcher.getIds().contains(impostorId)
			|| (matcher.hasNames() && matcher.matchesName(client.getObjectDefinition(impostorId).getName()));
	}

	/**
	 * Flags to render right now for a tracked object. For multilocs the effective object
	 * varies with varbits, so re-resolve the impostor each frame. Must run on the client thread.
	 */
	int renderFlags(HighlightedObject object)
	{
		if (!object.isMultiloc())
		{
			return object.getFlags();
		}

		ObjectComposition impostor = object.getBaseComposition().getImpostor();
		if (impostor == null)
		{
			return 0;
		}

		int baseId = object.getTileObject().getId();
		return
			(multilocMatches(hullMatcher, baseId, impostor) ? HF_HULL : 0) |
			(multilocMatches(outlineMatcher, baseId, impostor) ? HF_OUTLINE : 0) |
			(multilocMatches(clickboxMatcher, baseId, impostor) ? HF_CLICKBOX : 0) |
			(multilocMatches(tileMatcher, baseId, impostor) ? HF_TILE : 0);
	}

	private static boolean multilocMatches(StyleMatcher matcher, int baseId, ObjectComposition impostor)
	{
		return matcher.getIds().contains(baseId)
			|| matcher.getIds().contains(impostor.getId())
			|| matcher.matchesName(impostor.getName());
	}

	private void rescanScene()
	{
		objects.clear();

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
			.forEach(object -> handleSpawn(object, tile));

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
		hullMatcher = config.hullHighlight()
			? StyleMatcher.of(config.hullIds(), config.hullNames()) : StyleMatcher.EMPTY;
		outlineMatcher = config.outlineHighlight()
			? StyleMatcher.of(config.outlineIds(), config.outlineNames()) : StyleMatcher.EMPTY;
		clickboxMatcher = config.clickboxHighlight()
			? StyleMatcher.of(config.clickboxIds(), config.clickboxNames()) : StyleMatcher.EMPTY;
		tileMatcher = config.tileHighlight()
			? StyleMatcher.of(config.tileIds(), config.tileNames()) : StyleMatcher.EMPTY;
		hideMatcher = config.entityHiderToggle()
			? StyleMatcher.of(config.entityHiderIds(), config.entityHiderNames()) : StyleMatcher.EMPTY;
		anyMatcherActive = Stream.of(hullMatcher, outlineMatcher, clickboxMatcher, tileMatcher, hideMatcher)
			.anyMatch(matcher -> !matcher.isEmpty());
	}

	@Getter(AccessLevel.PACKAGE)
	static class StyleMatcher
	{
		static final StyleMatcher EMPTY = new StyleMatcher(Collections.emptySet(), Collections.emptyList());

		private final Set<Integer> ids;
		private final List<String> names;

		private StyleMatcher(Set<Integer> ids, List<String> names)
		{
			this.ids = ids;
			this.names = names;
		}

		static StyleMatcher of(String idList, String nameList)
		{
			Set<Integer> ids = tokens(idList)
				.map(StyleMatcher::parseId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
			List<String> names = tokens(nameList)
				.filter(token -> !token.isBlank())
				.map(String::trim)
				.collect(Collectors.toList());
			return ids.isEmpty() && names.isEmpty() ? EMPTY : new StyleMatcher(ids, names);
		}

		private static Stream<String> tokens(String raw)
		{
			return Text.fromCSV(raw.replace('\n', ',')).stream();
		}

		private static Integer parseId(String token)
		{
			try
			{
				return Integer.parseInt(token.trim());
			}
			catch (NumberFormatException ex)
			{
				return null;
			}
		}

		boolean isEmpty()
		{
			return ids.isEmpty() && names.isEmpty();
		}

		boolean hasNames()
		{
			return !names.isEmpty();
		}

		boolean matchesName(String name)
		{
			boolean isMatchableName = !names.isEmpty() && name != null && !name.isEmpty() && !"null".equals(name);
			if (!isMatchableName)
			{
				return false;
			}

			return names.stream().anyMatch(pattern -> WildcardMatcher.matches(pattern, name));
		}

		/**
		 * True if this matcher matches anything the other matcher does not, i.e. an entry
		 * was removed. Used to decide whether previously hidden objects need restoring.
		 */
		boolean matchesAnythingNotIn(StyleMatcher other)
		{
			return !other.ids.containsAll(ids) || !other.names.containsAll(names);
		}
	}
}
