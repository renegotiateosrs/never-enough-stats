package com.neverenoughstats.ui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.neverenoughstats.data.Cat;
import com.neverenoughstats.data.Keys;
import com.neverenoughstats.data.StatBlock;
import com.neverenoughstats.data.StatFormat;
import com.neverenoughstats.data.StatRef;
import com.neverenoughstats.data.StatStore;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.runelite.api.Skill;

/**
 * Turns a {@link StatBlock} into the sections shown for each view.
 */
class ViewBuilder
{
	/**
	 * Pseudo-column meaning "sum of every column".
	 */
	static final String ALL_COLUMNS = "*";

	static final List<String> ITEM_COLUMNS = ImmutableList.of(
		Cat.ITEM_PRODUCED, Cat.ITEM_PICKED_UP, Cat.ITEM_LOOT, Cat.ITEM_SKILL_USED, Cat.ITEM_EATEN, Cat.ITEM_DRUNK,
		Cat.ITEM_DROPPED, Cat.ITEM_OPENED, Cat.ITEM_ALCHED, Cat.ITEM_FIRED, Cat.ITEM_BOUGHT, Cat.ITEM_SOLD,
		Cat.ITEM_GE_BOUGHT, Cat.ITEM_GE_SOLD, Cat.ITEM_TRADED_IN, Cat.ITEM_TRADED_OUT, Cat.ITEM_LOST_DEATH,
		Cat.ITEM_DESTROYED, Cat.ITEM_USED_UP, Cat.ITEM_OTHER_GAINED, Cat.ITEM_OTHER_LOST
	);

	static final List<String> NPC_COLUMNS = ImmutableList.of(
		Cat.NPC_KILLED, Cat.NPC_KILLED_BY, Cat.NPC_DAMAGE_DEALT, Cat.NPC_DAMAGE_TAKEN, Cat.NPC_LOOT_VALUE,
		Cat.NPC_LOOT_COUNT
	);

	/**
	 * Display order for keys in fixed categories. Unlisted keys follow, largest first.
	 */
	private static final Map<String, List<String>> KEY_ORDER = ImmutableMap.<String, List<String>>builder()
		.put(Cat.TIME, ImmutableList.of(Keys.TIME_LOGGED_IN, Keys.TIME_ACTIVE, Keys.TIME_AFK, Keys.TIME_COMBAT,
			Keys.TIME_RUNNING, Keys.TIME_WILDERNESS))
		.put(Cat.SESSION, ImmutableList.of(Keys.LOGINS, Keys.DAYS_PLAYED, Keys.WORLD_HOPS, Keys.LEVEL_UPS))
		.put(Cat.INPUT, ImmutableList.of(Keys.CLICKS, Keys.LEFT_CLICKS, Keys.RIGHT_CLICKS, Keys.MIDDLE_CLICKS,
			Keys.MENU_CLICKS, Keys.KEYS_PRESSED, Keys.SCROLLS, Keys.MOUSE_MOVED, Keys.CAMERA_ROTATED))
		.put(Cat.MOVEMENT, ImmutableList.of(Keys.TILES_WALKED, Keys.TILES_RUN, Keys.TELEPORTS, Keys.CLIMBS,
			Keys.DOORS, Keys.AREAS_VISITED, Keys.RUN_ENERGY_USED, Keys.RUN_ENERGY_RESTORED))
		.put(Cat.SOCIAL, ImmutableList.of(Keys.PUBLIC_MESSAGES, Keys.PRIVATE_SENT, Keys.PRIVATE_RECEIVED,
			Keys.CLAN_MESSAGES, Keys.TRADES, Keys.EMOTES))
		.put(Cat.ECONOMY, ImmutableList.of(Keys.GP_LOOT_NPC, Keys.GP_LOOT_OTHER, Keys.GP_LOOT_PVP, Keys.GP_ALCH,
			Keys.GP_GE_SPENT, Keys.GP_GE_EARNED, Keys.GP_SHOP_SPENT, Keys.GP_SHOP_EARNED))
		.put(Cat.ECONOMY_LATEST, ImmutableList.of(Keys.BANK_VALUE, Keys.CARRIED_VALUE))
		.put(Cat.ACCOUNT_LATEST, ImmutableList.of(Keys.TOTAL_LEVEL, Keys.TOTAL_XP, Keys.COMBAT_LEVEL))
		.put(Cat.COMBAT, ImmutableList.of(Keys.KILLS, Keys.SLAYER_KILLS, Keys.DEATHS, Keys.DAMAGE_DEALT,
			Keys.DAMAGE_TAKEN, Keys.HITS_LANDED, Keys.ZEROS_DEALT, Keys.HITS_TAKEN, Keys.ZEROS_TAKEN, Keys.SPLASHES,
			Keys.SPECS, Keys.SPEC_ENERGY_USED, Keys.PLAYER_KILLS, Keys.SUPERIORS))
		.put(Cat.COMBAT_RECORDS, ImmutableList.of(Keys.MAX_HIT_DEALT, Keys.MAX_HIT_TAKEN, Keys.MOST_DAMAGE_TICK))
		.put(Cat.HEAL, ImmutableList.of(Keys.HP_HEALED, Keys.FOOD_EATEN, Keys.DOSES_DRUNK))
		.put(Cat.PRAYER_POINTS, ImmutableList.of(Keys.PRAYER_DRAINED, Keys.PRAYER_RESTORED))
		.build();

	private static final int MAX_ROWS = 300;

	private final StatBlock block;
	private final StatStore store;

	ViewBuilder(StatBlock block, StatStore store)
	{
		this.block = block;
		this.store = store;
	}

	List<Section> build(ViewType view, String itemSort, String npcSort)
	{
		List<Section> sections = new ArrayList<>();
		switch (view)
		{
			case GENERAL:
				sections.add(overview());
				addCategories(sections, Cat.TIME, Cat.TIME_RECORDS, Cat.SESSION, Cat.INPUT, Cat.MOVEMENT,
					Cat.SOCIAL, Cat.ECONOMY, Cat.ECONOMY_LATEST, Cat.ACCOUNT_LATEST);
				break;
			case COMBAT:
				addCategories(sections, Cat.COMBAT, Cat.COMBAT_RECORDS, Cat.COMBAT_STATUS, Cat.COMBAT_STYLE,
					Cat.COMBAT_WEAPON, Cat.COMBAT_TAKEN_TYPE, Cat.COMBAT_SPECS, Cat.COMBAT_SPELLS, Cat.COMBAT_DEATHS,
					Cat.PRAYER_POINTS, Cat.PRAYER_ACTIVATIONS, Cat.PRAYER_TIME);
				break;
			case HEALING:
				addCategories(sections, Cat.HEAL, Cat.HEAL_SOURCE, Cat.ITEM_EATEN, Cat.ITEM_DRUNK,
					Cat.HEAL_PRAYER_SOURCE, Cat.HEAL_ENERGY_SOURCE);
				break;
			case SKILLS:
				skills(sections);
				break;
			case ITEMS:
				entities(sections, "items", "Items", ITEM_COLUMNS, itemSort);
				break;
			case CREATURES:
				entities(sections, "npcs", "Creatures", NPC_COLUMNS, npcSort);
				break;
			case ACTIVITIES:
				addCategories(sections, Cat.ACTIVITY_KC, Cat.ACTIVITY_COMPLETED, Cat.ACTIVITY_PB, Cat.ACTIVITY_CLUES,
					Cat.ACTIVITY_LOOT_COUNT, Cat.ACTIVITY_LOOT_VALUE, Cat.ACTIVITY_DROPS, Cat.ACTIVITY_PETS,
					Cat.ACTIVITY_CLOG, Cat.ACTIVITY_QUESTS, Cat.ACTIVITY_DIARY, Cat.ACTIVITY_CA);
				break;
			case WORLD:
				addCategories(sections, Cat.WORLD_AREA_TIME, Cat.WORLD_TELEPORTS, Cat.INTERFACES, Cat.EMOTES,
					Cat.MENU_OPTIONS, Cat.MENU_ACTIONS);
				break;
		}
		sections.removeIf(s -> s.getRows().isEmpty());
		return sections;
	}

	private Section overview()
	{
		Section s = new Section("overview", "Overview", false);
		if (store != null)
		{
			String first = store.getMeta(StatStore.META_FIRST_TRACKED);
			if (first != null)
			{
				s.add(new Section.Row("Tracking since", formatDate(first), 0, null));
			}
			s.add(new Section.Row("Days with stats", StatFormat.formatNumber(store.getDaysTracked()), store.getDaysTracked(), null));
			String lastDeath = store.getMeta(StatStore.META_LAST_DEATH);
			if (lastDeath != null)
			{
				s.add(new Section.Row("Time since last death", since(lastDeath), 0, null));
			}
		}
		long walked = block.get(Cat.MOVEMENT, Keys.TILES_WALKED);
		long run = block.get(Cat.MOVEMENT, Keys.TILES_RUN);
		if (walked + run > 0)
		{
			s.add(new Section.Row("Tiles travelled on foot", StatFormat.formatNumber(walked + run), walked + run, null));
		}
		long kills = block.get(Cat.COMBAT, Keys.KILLS);
		long deaths = block.get(Cat.COMBAT, Keys.DEATHS);
		if (kills > 0)
		{
			String kd = deaths > 0 ? String.format("%.1f", (double) kills / deaths) : "∞";
			s.add(new Section.Row("Kills per death", kd, 0, null));
		}
		long dealt = block.get(Cat.COMBAT, Keys.DAMAGE_DEALT);
		long hits = block.get(Cat.COMBAT, Keys.HITS_LANDED) + block.get(Cat.COMBAT, Keys.ZEROS_DEALT);
		if (hits > 0)
		{
			s.add(new Section.Row("Average hit", String.format("%.2f", (double) dealt / hits), 0, null));
			long landed = block.get(Cat.COMBAT, Keys.HITS_LANDED);
			s.add(new Section.Row("Hits that did damage", String.format("%.1f%%", 100.0 * landed / hits), 0, null));
		}
		long xp = 0;
		for (long v : block.getCategory(Cat.SKILL_XP).values())
		{
			xp += v;
		}
		if (xp > 0)
		{
			s.add(new Section.Row("Total XP gained", StatFormat.formatNumber(xp), xp, null));
			long ticks = block.get(Cat.TIME, Keys.TIME_LOGGED_IN);
			if (ticks > 0)
			{
				s.add(new Section.Row("XP per hour played", StatFormat.formatNumber(xp * 6000 / ticks), 0, null));
			}
		}
		return s;
	}

	private void addCategories(List<Section> sections, String... categories)
	{
		for (String cat : categories)
		{
			sections.add(category(cat, Cat.info(cat).getTitle(), false));
		}
	}

	private Section category(String cat, String title, boolean collapsed)
	{
		Section s = new Section(cat, title, collapsed);
		for (Section.Row row : categoryRows(cat))
		{
			s.add(row);
		}
		return s;
	}

	private List<Section.Row> categoryRows(String cat)
	{
		Map<String, Long> values = block.getCategory(cat);
		List<String> keys = new ArrayList<>();
		List<String> order = KEY_ORDER.get(cat);
		if (order != null)
		{
			for (String k : order)
			{
				if (values.containsKey(k))
				{
					keys.add(k);
				}
			}
		}
		List<String> rest = new ArrayList<>(values.keySet());
		rest.removeAll(keys);
		rest.sort(Comparator.<String>comparingLong(k -> values.get(k)).reversed().thenComparing(k -> k));
		keys.addAll(rest);

		List<Section.Row> rows = new ArrayList<>();
		for (String key : keys)
		{
			if (rows.size() >= MAX_ROWS)
			{
				rows.add(new Section.Row("… " + (keys.size() - MAX_ROWS) + " more (use search)", "", 0, null));
				break;
			}
			long v = values.get(key);
			if (v == 0)
			{
				continue;
			}
			rows.add(new Section.Row(key, Cat.format(cat, key).format(v), v, new StatRef(cat, key)));
		}
		return rows;
	}

	private void skills(List<Section> sections)
	{
		Map<String, Long> xp = block.getCategory(Cat.SKILL_XP);

		Section summary = new Section("skills.summary", "XP gained per skill", false);
		List<String> skillNames = new ArrayList<>();
		for (Skill skill : Skill.values())
		{
			skillNames.add(skill.getName());
		}
		List<String> byXp = new ArrayList<>(skillNames);
		byXp.sort(Comparator.<String>comparingLong(n -> xp.getOrDefault(n, 0L)).reversed());
		for (String name : byXp)
		{
			long v = xp.getOrDefault(name, 0L);
			if (v > 0)
			{
				summary.add(new Section.Row(name, StatFormat.formatNumber(v), v, new StatRef(Cat.SKILL_XP, name)));
			}
		}
		sections.add(summary);
		sections.add(category(Cat.SKILL_LEVELS, Cat.info(Cat.SKILL_LEVELS).getTitle(), false));

		// Group the per-skill lists ("skill.<Skill>.<List>") by skill
		Map<String, List<String>> lists = new HashMap<>();
		for (String cat : block.categories())
		{
			if (!cat.startsWith(Cat.SKILL_PREFIX))
			{
				continue;
			}
			int dot = cat.indexOf('.', Cat.SKILL_PREFIX.length());
			if (dot < 0)
			{
				continue;
			}
			String skill = cat.substring(Cat.SKILL_PREFIX.length(), dot);
			lists.computeIfAbsent(skill, k -> new ArrayList<>()).add(cat);
		}

		Set<String> ordered = new LinkedHashSet<>(skillNames);
		ordered.addAll(lists.keySet());
		for (String name : ordered)
		{
			long skillXp = xp.getOrDefault(name, 0L);
			List<String> skillLists = lists.getOrDefault(name, new ArrayList<>());
			if (skillXp == 0 && skillLists.isEmpty())
			{
				continue;
			}

			Section s = new Section("skill." + name, name, true);
			addStat(s, "XP gained", Cat.SKILL_XP, name);
			addStat(s, "Levels gained", Cat.SKILL_LEVELS, name);
			addStat(s, "Actions (XP drops)", Cat.SKILL_ACTIONS, name);
			addStat(s, "Time training", Cat.SKILL_TIME, name);
			long ticks = block.get(Cat.SKILL_TIME, name);
			if (ticks > 0 && skillXp > 0)
			{
				s.add(new Section.Row("XP per hour training", StatFormat.formatNumber(skillXp * 6000 / ticks), 0, null));
			}
			long actions = block.get(Cat.SKILL_ACTIONS, name);
			if (actions > 0 && skillXp > 0)
			{
				s.add(new Section.Row("Average XP per action", String.format("%.1f", (double) skillXp / actions), 0, null));
			}

			skillLists.sort(Comparator.naturalOrder());
			for (String cat : skillLists)
			{
				Map<String, Long> values = block.getCategory(cat);
				long total = 0;
				for (long v : values.values())
				{
					total += v;
				}
				if (total == 0)
				{
					continue;
				}
				s.add(new Section.Row(Cat.info(cat).getTitle() + " (" + values.size() + ")",
					StatFormat.formatNumber(total), total, null, categoryRows(cat)));
			}
			sections.add(s);
		}
	}

	private void addStat(Section s, String label, String cat, String key)
	{
		long v = block.get(cat, key);
		if (v != 0)
		{
			s.add(new Section.Row(label, Cat.format(cat, key).format(v), v, new StatRef(cat, key)));
		}
	}

	/**
	 * Items or creatures: one row per name, valued by the chosen column,
	 * expanding to show every column.
	 */
	private void entities(List<Section> sections, String id, String title, List<String> columns, String sortColumn)
	{
		// Totals across everything, per column
		Section totals = new Section(id + ".totals", title + " - totals", false);
		Map<String, Map<String, Long>> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (String col : columns)
		{
			long sum = 0;
			for (Map.Entry<String, Long> e : block.getCategory(col).entrySet())
			{
				byName.computeIfAbsent(e.getKey(), k -> new HashMap<>()).put(col, e.getValue());
				sum += e.getValue();
			}
			if (sum != 0)
			{
				Cat.Info info = Cat.info(col);
				totals.add(new Section.Row(info.getTitle(), info.getFormat().format(sum), sum, null));
			}
		}
		totals.add(new Section.Row("Different " + title.toLowerCase(), StatFormat.formatNumber(byName.size()), byName.size(), null));
		sections.add(totals);

		boolean sumAll = ALL_COLUMNS.equals(sortColumn) || !columns.contains(sortColumn);
		String listTitle = title + " by " + (sumAll ? "all activity" : Cat.info(sortColumn).getTitle().toLowerCase());
		Section list = new Section(id + ".list", listTitle, false);

		List<Map.Entry<String, Map<String, Long>>> entries = new ArrayList<>(byName.entrySet());
		Map<String, Long> sortValues = new HashMap<>();
		for (Map.Entry<String, Map<String, Long>> e : entries)
		{
			long v = 0;
			if (sumAll)
			{
				for (Map.Entry<String, Long> c : e.getValue().entrySet())
				{
					// Don't let coin values swamp counts
					if (Cat.info(c.getKey()).getFormat() != StatFormat.GP)
					{
						v += c.getValue();
					}
				}
			}
			else
			{
				v = e.getValue().getOrDefault(sortColumn, 0L);
			}
			sortValues.put(e.getKey(), v);
		}
		entries.removeIf(e -> sortValues.get(e.getKey()) == 0);
		entries.sort(Comparator.<Map.Entry<String, Map<String, Long>>>comparingLong(e -> sortValues.get(e.getKey())).reversed());

		int shown = 0;
		for (Map.Entry<String, Map<String, Long>> e : entries)
		{
			if (shown++ >= MAX_ROWS)
			{
				list.add(new Section.Row("… " + (entries.size() - MAX_ROWS) + " more (use search)", "", 0, null));
				break;
			}
			String name = e.getKey();
			List<Section.Row> details = new ArrayList<>();
			for (String col : columns)
			{
				Long v = e.getValue().get(col);
				if (v != null && v != 0)
				{
					details.add(new Section.Row(Cat.info(col).getTitle(), Cat.format(col, name).format(v), v,
						new StatRef(col, name)));
				}
			}
			long v = sortValues.get(name);
			String value = sumAll ? StatFormat.formatNumber(v) : Cat.format(sortColumn, name).format(v);
			StatRef ref = sumAll ? null : new StatRef(sortColumn, name);
			list.add(new Section.Row(name, value, v, ref, details));
		}
		sections.add(list);
	}

	private static String formatDate(String epochMillis)
	{
		try
		{
			return LocalDate.ofInstant(Instant.ofEpochMilli(Long.parseLong(epochMillis)), ZoneId.systemDefault()).toString();
		}
		catch (NumberFormatException e)
		{
			return "?";
		}
	}

	private static String since(String epochMillis)
	{
		try
		{
			long ms = System.currentTimeMillis() - Long.parseLong(epochMillis);
			return StatFormat.TICKS.format(Math.max(0, ms / 600));
		}
		catch (NumberFormatException e)
		{
			return "?";
		}
	}
}
