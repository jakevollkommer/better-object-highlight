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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.client.util.Text;
import net.runelite.client.util.WildcardMatcher;

/**
 * The parsed form of one config section's ID and name lists. Each entry may carry a
 * preset color index as an {@code :n} suffix (e.g. {@code 1234:2} or {@code Guardian*:1});
 * entries without a suffix use the section's default colors.
 */
class StyleMatcher
{
	static final StyleMatcher EMPTY = new StyleMatcher(Collections.emptyMap(), Collections.emptyList());
	static final int DEFAULT_COLORS = 0;
	private static final int PRESET_COUNT = 5;

	private final Map<Integer, Integer> presetByObjectId;
	private final List<NamePattern> namePatterns;

	private StyleMatcher(Map<Integer, Integer> presetByObjectId, List<NamePattern> namePatterns)
	{
		this.presetByObjectId = presetByObjectId;
		this.namePatterns = namePatterns;
	}

	static StyleMatcher fromConfigLists(String idList, String nameList)
	{
		Map<Integer, Integer> presetByObjectId = new HashMap<>();
		tokens(idList).forEach(token -> parseIdEntry(token, presetByObjectId));

		List<NamePattern> namePatterns = tokens(nameList)
			.filter(token -> !token.isBlank())
			.map(StyleMatcher::parseNameEntry)
			.collect(Collectors.toList());

		boolean hasNoEntries = presetByObjectId.isEmpty() && namePatterns.isEmpty();
		return hasNoEntries ? EMPTY : new StyleMatcher(presetByObjectId, namePatterns);
	}

	private static Stream<String> tokens(String rawConfigList)
	{
		String commaSeparated = rawConfigList.replace('\n', ',');
		return Text.fromCSV(commaSeparated).stream();
	}

	private static void parseIdEntry(String token, Map<Integer, Integer> presetByObjectId)
	{
		String entry = token.trim();
		int preset = presetSuffix(entry);
		String idPart = withoutPresetSuffix(entry);
		try
		{
			presetByObjectId.put(Integer.parseInt(idPart), preset);
		}
		catch (NumberFormatException invalidNumber)
		{
			// skip entries that are not numeric ids
		}
	}

	private static NamePattern parseNameEntry(String token)
	{
		String entry = token.trim();
		return new NamePattern(withoutPresetSuffix(entry), presetSuffix(entry));
	}

	/**
	 * The preset index of an entry's {@code :n} suffix, or {@link #DEFAULT_COLORS} if absent.
	 */
	private static int presetSuffix(String entry)
	{
		int separator = entry.lastIndexOf(':');
		if (separator < 0)
		{
			return DEFAULT_COLORS;
		}

		try
		{
			int preset = Integer.parseInt(entry.substring(separator + 1));
			boolean isValidPreset = preset >= 1 && preset <= PRESET_COUNT;
			return isValidPreset ? preset : DEFAULT_COLORS;
		}
		catch (NumberFormatException notAPreset)
		{
			return DEFAULT_COLORS;
		}
	}

	private static String withoutPresetSuffix(String entry)
	{
		int separator = entry.lastIndexOf(':');
		boolean hasSuffix = separator >= 0 && presetSuffix(entry) != DEFAULT_COLORS;
		return hasSuffix ? entry.substring(0, separator) : entry;
	}

	boolean isEmpty()
	{
		return presetByObjectId.isEmpty() && namePatterns.isEmpty();
	}

	boolean hasNamePatterns()
	{
		return !namePatterns.isEmpty();
	}

	/**
	 * The matched entry's preset index ({@link #DEFAULT_COLORS} for none), or null if no entry
	 * matches this object id.
	 */
	@Nullable
	Integer presetForId(int objectId)
	{
		return presetByObjectId.get(objectId);
	}

	/**
	 * The matched entry's preset index ({@link #DEFAULT_COLORS} for none), or null if no entry
	 * matches this object name.
	 */
	@Nullable
	Integer presetForName(@Nullable String objectName)
	{
		boolean isMatchableName = hasNamePatterns()
			&& objectName != null
			&& !objectName.isEmpty()
			&& !"null".equals(objectName);
		if (!isMatchableName)
		{
			return null;
		}

		return namePatterns.stream()
			.filter(namePattern -> WildcardMatcher.matches(namePattern.getPattern(), objectName))
			.map(NamePattern::getPreset)
			.findFirst()
			.orElse(null);
	}

	/**
	 * True if this matcher matches anything the other matcher does not, i.e. an entry
	 * was removed. Used to decide whether previously hidden objects need restoring.
	 */
	boolean matchesAnythingNotIn(StyleMatcher other)
	{
		boolean idWasRemoved = !other.presetByObjectId.keySet().containsAll(presetByObjectId.keySet());
		List<String> otherPatterns = other.namePatterns.stream().map(NamePattern::getPattern).collect(Collectors.toList());
		boolean nameWasRemoved = !namePatterns.stream().map(NamePattern::getPattern).allMatch(otherPatterns::contains);
		return idWasRemoved || nameWasRemoved;
	}

	@Value
	private static class NamePattern
	{
		String pattern;
		int preset;
	}
}
