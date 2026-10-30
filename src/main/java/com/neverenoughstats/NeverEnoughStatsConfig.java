package com.neverenoughstats;

import com.neverenoughstats.data.Period;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(NeverEnoughStatsConfig.GROUP)
public interface NeverEnoughStatsConfig extends Config
{
	String GROUP = "neverenoughstats";
	String PINNED_KEY = "pinnedStats";

	@ConfigSection(
		name = "Tracking",
		description = "What is tracked and how",
		position = 0
	)
	String trackingSection = "tracking";

	@ConfigSection(
		name = "Overlay",
		description = "Pinned stats overlay",
		position = 1
	)
	String overlaySection = "overlay";

	@ConfigSection(
		name = "Storage",
		description = "Saving and history",
		position = 2
	)
	String storageSection = "storage";

	@ConfigItem(
		keyName = "countKeys",
		name = "Count key presses",
		description = "Count how many keys you press. Only the total is stored, never which keys.",
		section = trackingSection,
		position = 0
	)
	default boolean countKeys()
	{
		return true;
	}

	@Range(min = 10, max = 3600)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "afkSeconds",
		name = "AFK after",
		description = "Time without mouse or keyboard input before time counts as AFK instead of active",
		section = trackingSection,
		position = 1
	)
	default int afkSeconds()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show pinned stats overlay",
		description = "Show stats you pin from the side panel (right-click a stat) in an overlay",
		section = overlaySection,
		position = 0
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayPeriod",
		name = "Overlay period",
		description = "Which time period the overlay shows",
		section = overlaySection,
		position = 1
	)
	default Period overlayPeriod()
	{
		return Period.SESSION;
	}

	@Range(min = 1, max = 60)
	@Units(Units.MINUTES)
	@ConfigItem(
		keyName = "autosaveMinutes",
		name = "Autosave every",
		description = "How often stats are saved to disk. They are also saved when you log out.",
		section = storageSection,
		position = 0
	)
	default int autosaveMinutes()
	{
		return 2;
	}

	@Range(min = 0, max = 3650)
	@ConfigItem(
		keyName = "historyDays",
		name = "Days of history",
		description = "How many days of per-day stats to keep for the Today/7/30/365 day views. "
			+ "All-time totals are always kept. 0 keeps every day forever.",
		section = storageSection,
		position = 1
	)
	default int historyDays()
	{
		return 400;
	}

	@ConfigItem(
		keyName = PINNED_KEY,
		name = "",
		description = "",
		hidden = true
	)
	default String pinnedStats()
	{
		return "";
	}
}
