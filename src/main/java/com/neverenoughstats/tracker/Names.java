package com.neverenoughstats.tracker;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * Turns ids and menu text into the names stats are stored under.
 */
@Singleton
class Names
{
	private static final Pattern LEVEL_SUFFIX = Pattern.compile("\\s*\\((?:level|skill)-\\d+\\)$");
	private static final Pattern DOSE_SUFFIX = Pattern.compile("\\s*\\(\\d+\\)$");

	private final ItemManager itemManager;
	private final Map<Integer, String> itemNames = new HashMap<>();

	@Inject
	Names(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	/**
	 * Item name, with noted and placeholder variants merged. Client thread only.
	 *
	 * @return the name, or null for invalid items
	 */
	String item(int itemId)
	{
		if (itemId <= 0)
		{
			return null;
		}
		int canonical = itemManager.canonicalize(itemId);
		String name = itemNames.get(canonical);
		if (name == null)
		{
			ItemComposition comp = itemManager.getItemComposition(canonical);
			name = comp.getName();
			if (name == null || name.isEmpty() || "null".equalsIgnoreCase(name))
			{
				return null;
			}
			itemNames.put(canonical, name);
		}
		return name;
	}

	long price(int itemId)
	{
		return itemManager.getItemPrice(itemManager.canonicalize(itemId));
	}

	int canonicalize(int itemId)
	{
		return itemManager.canonicalize(itemId);
	}

	static String clean(String text)
	{
		if (text == null)
		{
			return "";
		}
		return Text.removeTags(text).replace(' ', ' ').trim();
	}

	/**
	 * Menu target without colour tags or combat/skill level.
	 */
	static String target(String target)
	{
		return LEVEL_SUFFIX.matcher(clean(target)).replaceAll("");
	}

	/**
	 * "Prayer potion(3)" -> "Prayer potion", "Amulet of glory(4)" -> "Amulet of glory".
	 */
	static String stripDose(String name)
	{
		return name == null ? null : DOSE_SUFFIX.matcher(name).replaceAll("");
	}
}
