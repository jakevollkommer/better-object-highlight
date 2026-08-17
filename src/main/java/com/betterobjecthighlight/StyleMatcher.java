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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.client.util.Text;
import net.runelite.client.util.WildcardMatcher;

/**
 * The parsed form of one config section's ID and name lists.
 */
@Getter(AccessLevel.PACKAGE)
class StyleMatcher
{
	static final StyleMatcher EMPTY = new StyleMatcher(Collections.emptySet(), Collections.emptyList());

	private final Set<Integer> objectIds;
	private final List<String> namePatterns;

	private StyleMatcher(Set<Integer> objectIds, List<String> namePatterns)
	{
		this.objectIds = objectIds;
		this.namePatterns = namePatterns;
	}

	static StyleMatcher fromConfigLists(String idList, String nameList)
	{
		Set<Integer> objectIds = tokens(idList)
			.map(StyleMatcher::parseObjectId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		List<String> namePatterns = tokens(nameList)
			.filter(token -> !token.isBlank())
			.map(String::trim)
			.collect(Collectors.toList());

		boolean hasNoEntries = objectIds.isEmpty() && namePatterns.isEmpty();
		return hasNoEntries ? EMPTY : new StyleMatcher(objectIds, namePatterns);
	}

	private static Stream<String> tokens(String rawConfigList)
	{
		String commaSeparated = rawConfigList.replace('\n', ',');
		return Text.fromCSV(commaSeparated).stream();
	}

	@Nullable
	private static Integer parseObjectId(String token)
	{
		try
		{
			return Integer.parseInt(token.trim());
		}
		catch (NumberFormatException invalidNumber)
		{
			return null;
		}
	}

	boolean isEmpty()
	{
		return objectIds.isEmpty() && namePatterns.isEmpty();
	}

	boolean hasNamePatterns()
	{
		return !namePatterns.isEmpty();
	}

	boolean matchesId(int objectId)
	{
		return objectIds.contains(objectId);
	}

	boolean matchesName(@Nullable String objectName)
	{
		boolean isMatchableName = hasNamePatterns()
			&& objectName != null
			&& !objectName.isEmpty()
			&& !"null".equals(objectName);
		if (!isMatchableName)
		{
			return false;
		}

		return namePatterns.stream().anyMatch(pattern -> WildcardMatcher.matches(pattern, objectName));
	}

	/**
	 * True if this matcher matches anything the other matcher does not, i.e. an entry
	 * was removed. Used to decide whether previously hidden objects need restoring.
	 */
	boolean matchesAnythingNotIn(StyleMatcher other)
	{
		return !other.objectIds.containsAll(objectIds) || !other.namePatterns.containsAll(namePatterns);
	}
}
