package com.neverenoughstats.data;

import lombok.Value;

/**
 * Points at one stat: a category and a key.
 */
@Value
public class StatRef
{
	private static final char SEPARATOR = '\t';

	String category;
	String key;

	public String encode()
	{
		return category + SEPARATOR + key;
	}

	public static StatRef decode(String s)
	{
		int idx = s.indexOf(SEPARATOR);
		if (idx <= 0 || idx == s.length() - 1)
		{
			return null;
		}
		return new StatRef(s.substring(0, idx), s.substring(idx + 1));
	}

	/**
	 * A short label for the overlay, e.g. "Goblin (Killed)".
	 */
	public String label()
	{
		Cat.Info info = Cat.info(category);
		if (category.startsWith(Cat.ITEM_PREFIX) || category.startsWith(Cat.NPC_PREFIX)
			|| category.startsWith(Cat.SKILL_PREFIX) || category.startsWith("heal.")
			|| category.startsWith("combat.") || category.startsWith("prayer.")
			|| category.startsWith("world.") || category.startsWith("menu."))
		{
			return key + " (" + info.getTitle() + ")";
		}
		return key;
	}
}
