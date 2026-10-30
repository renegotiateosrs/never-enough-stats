package com.neverenoughstats.data;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import lombok.Value;

/**
 * Every stat is stored as {@code category -> key -> value}. This class holds
 * the category ids used by the trackers, along with how each one is titled,
 * formatted and merged.
 */
public final class Cat
{
	// General
	public static final String TIME = "time";
	public static final String TIME_RECORDS = "time.records";
	public static final String SESSION = "session";
	public static final String INPUT = "input";
	public static final String MOVEMENT = "movement";
	public static final String SOCIAL = "social";
	public static final String ECONOMY = "economy";
	public static final String ECONOMY_LATEST = "economy.latest";
	public static final String ACCOUNT_LATEST = "account.latest";

	// Combat
	public static final String COMBAT = "combat";
	public static final String COMBAT_RECORDS = "combat.records";
	public static final String COMBAT_STYLE = "combat.style";
	public static final String COMBAT_WEAPON = "combat.weapon";
	public static final String COMBAT_TAKEN_TYPE = "combat.taken_type";
	public static final String COMBAT_SPECS = "combat.specs";
	public static final String COMBAT_SPELLS = "combat.spells";
	public static final String COMBAT_DEATHS = "combat.deaths";
	public static final String COMBAT_STATUS = "combat.status";
	public static final String PRAYER_POINTS = "prayer.points";
	public static final String PRAYER_ACTIVATIONS = "prayer.activations";
	public static final String PRAYER_TIME = "prayer.time";

	// Healing
	public static final String HEAL = "heal";
	public static final String HEAL_SOURCE = "heal.source";
	public static final String HEAL_PRAYER_SOURCE = "heal.prayer_source";
	public static final String HEAL_ENERGY_SOURCE = "heal.energy_source";

	// Skills. Per-skill lists are stored as "skill.<Skill name>.<List name>"
	public static final String SKILL_XP = "skill.xp";
	public static final String SKILL_LEVELS = "skill.levels";
	public static final String SKILL_ACTIONS = "skill.actions";
	public static final String SKILL_TIME = "skill.time";
	public static final String SKILL_PREFIX = "skill.";

	// Items, keyed by item name
	public static final String ITEM_PREFIX = "item.";
	public static final String ITEM_PICKED_UP = "item.picked_up";
	public static final String ITEM_DROPPED = "item.dropped";
	public static final String ITEM_LOOT = "item.loot";
	public static final String ITEM_PRODUCED = "item.produced";
	public static final String ITEM_SKILL_USED = "item.skill_used";
	public static final String ITEM_EATEN = "item.eaten";
	public static final String ITEM_DRUNK = "item.drunk";
	public static final String ITEM_OPENED = "item.opened";
	public static final String ITEM_ALCHED = "item.alched";
	public static final String ITEM_FIRED = "item.fired";
	public static final String ITEM_BOUGHT = "item.bought";
	public static final String ITEM_SOLD = "item.sold";
	public static final String ITEM_GE_BOUGHT = "item.ge_bought";
	public static final String ITEM_GE_SOLD = "item.ge_sold";
	public static final String ITEM_TRADED_IN = "item.traded_in";
	public static final String ITEM_TRADED_OUT = "item.traded_out";
	public static final String ITEM_LOST_DEATH = "item.lost_death";
	public static final String ITEM_DESTROYED = "item.destroyed";
	public static final String ITEM_USED_UP = "item.used_up";
	public static final String ITEM_OTHER_GAINED = "item.other_gained";
	public static final String ITEM_OTHER_LOST = "item.other_lost";

	// Creatures, keyed by NPC name
	public static final String NPC_PREFIX = "npc.";
	public static final String NPC_KILLED = "npc.killed";
	public static final String NPC_KILLED_BY = "npc.killed_by";
	public static final String NPC_DAMAGE_DEALT = "npc.damage_dealt";
	public static final String NPC_DAMAGE_TAKEN = "npc.damage_taken";
	public static final String NPC_LOOT_VALUE = "npc.loot_value";
	public static final String NPC_LOOT_COUNT = "npc.loot_count";

	// Activities
	public static final String ACTIVITY_KC = "activity.kc";
	public static final String ACTIVITY_COMPLETED = "activity.completed";
	public static final String ACTIVITY_PB = "activity.pb";
	public static final String ACTIVITY_CLUES = "activity.clues";
	public static final String ACTIVITY_QUESTS = "activity.quests";
	public static final String ACTIVITY_DIARY = "activity.diary";
	public static final String ACTIVITY_CA = "activity.ca";
	public static final String ACTIVITY_CLOG = "activity.clog";
	public static final String ACTIVITY_PETS = "activity.pets";
	public static final String ACTIVITY_DROPS = "activity.drops";
	public static final String ACTIVITY_LOOT_VALUE = "activity.loot_value";
	public static final String ACTIVITY_LOOT_COUNT = "activity.loot_count";

	// World & interface
	public static final String WORLD_AREA_TIME = "world.area_time";
	public static final String WORLD_TELEPORTS = "world.teleports";
	public static final String MENU_OPTIONS = "menu.options";
	public static final String MENU_ACTIONS = "menu.actions";
	public static final String INTERFACES = "interface.opened";
	public static final String EMOTES = "social.emotes";

	/**
	 * Display info for a category.
	 */
	@Value
	public static class Info
	{
		String title;
		StatFormat format;
		StatMode mode;
	}

	private static final Map<String, Info> INFO = ImmutableMap.<String, Info>builder()
		.put(TIME, new Info("Time", StatFormat.TICKS, StatMode.SUM))
		.put(TIME_RECORDS, new Info("Records", StatFormat.TICKS, StatMode.MAX))
		.put(SESSION, new Info("Sessions", StatFormat.NUMBER, StatMode.SUM))
		.put(INPUT, new Info("Mouse & keyboard", StatFormat.NUMBER, StatMode.SUM))
		.put(MOVEMENT, new Info("Movement", StatFormat.NUMBER, StatMode.SUM))
		.put(SOCIAL, new Info("Social", StatFormat.NUMBER, StatMode.SUM))
		.put(ECONOMY, new Info("Money", StatFormat.GP, StatMode.SUM))
		.put(ECONOMY_LATEST, new Info("Wealth (last seen)", StatFormat.GP, StatMode.LATEST))
		.put(ACCOUNT_LATEST, new Info("Account", StatFormat.NUMBER, StatMode.LATEST))

		.put(COMBAT, new Info("Combat totals", StatFormat.NUMBER, StatMode.SUM))
		.put(COMBAT_RECORDS, new Info("Records", StatFormat.NUMBER, StatMode.MAX))
		.put(COMBAT_STYLE, new Info("Damage by style", StatFormat.NUMBER, StatMode.SUM))
		.put(COMBAT_WEAPON, new Info("Damage by weapon", StatFormat.NUMBER, StatMode.SUM))
		.put(COMBAT_TAKEN_TYPE, new Info("Damage taken by type", StatFormat.NUMBER, StatMode.SUM))
		.put(COMBAT_SPECS, new Info("Special attacks by weapon", StatFormat.NUMBER, StatMode.SUM))
		.put(COMBAT_SPELLS, new Info("Spells cast", StatFormat.NUMBER, StatMode.SUM))
		.put(COMBAT_DEATHS, new Info("Deaths by cause", StatFormat.NUMBER, StatMode.SUM))
		.put(COMBAT_STATUS, new Info("Status effects", StatFormat.NUMBER, StatMode.SUM))
		.put(PRAYER_POINTS, new Info("Prayer points", StatFormat.NUMBER, StatMode.SUM))
		.put(PRAYER_ACTIVATIONS, new Info("Prayers activated", StatFormat.NUMBER, StatMode.SUM))
		.put(PRAYER_TIME, new Info("Prayer active time", StatFormat.TICKS, StatMode.SUM))

		.put(HEAL, new Info("Healing totals", StatFormat.NUMBER, StatMode.SUM))
		.put(HEAL_SOURCE, new Info("Hitpoints healed by source", StatFormat.NUMBER, StatMode.SUM))
		.put(HEAL_PRAYER_SOURCE, new Info("Prayer restored by source", StatFormat.NUMBER, StatMode.SUM))
		.put(HEAL_ENERGY_SOURCE, new Info("Run energy restored by source", StatFormat.PERCENT, StatMode.SUM))

		.put(SKILL_XP, new Info("XP gained", StatFormat.NUMBER, StatMode.SUM))
		.put(SKILL_LEVELS, new Info("Levels gained", StatFormat.NUMBER, StatMode.SUM))
		.put(SKILL_ACTIONS, new Info("Actions (XP drops)", StatFormat.NUMBER, StatMode.SUM))
		.put(SKILL_TIME, new Info("Time training", StatFormat.TICKS, StatMode.SUM))

		.put(ITEM_PICKED_UP, new Info("Picked up", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_DROPPED, new Info("Dropped", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_LOOT, new Info("Dropped by monsters", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_PRODUCED, new Info("Gathered / made", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_SKILL_USED, new Info("Used in skilling", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_EATEN, new Info("Eaten", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_DRUNK, new Info("Doses drunk", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_OPENED, new Info("Opened", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_ALCHED, new Info("Alched", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_FIRED, new Info("Fired / thrown", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_BOUGHT, new Info("Bought (shops)", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_SOLD, new Info("Sold (shops)", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_GE_BOUGHT, new Info("Bought (GE)", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_GE_SOLD, new Info("Sold (GE)", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_TRADED_IN, new Info("Received in trades", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_TRADED_OUT, new Info("Given in trades", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_LOST_DEATH, new Info("Lost on death", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_DESTROYED, new Info("Destroyed", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_USED_UP, new Info("Used up (other)", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_OTHER_GAINED, new Info("Gained (other)", StatFormat.NUMBER, StatMode.SUM))
		.put(ITEM_OTHER_LOST, new Info("Lost (other)", StatFormat.NUMBER, StatMode.SUM))

		.put(NPC_KILLED, new Info("Killed", StatFormat.NUMBER, StatMode.SUM))
		.put(NPC_KILLED_BY, new Info("Killed by", StatFormat.NUMBER, StatMode.SUM))
		.put(NPC_DAMAGE_DEALT, new Info("Damage dealt", StatFormat.NUMBER, StatMode.SUM))
		.put(NPC_DAMAGE_TAKEN, new Info("Damage taken", StatFormat.NUMBER, StatMode.SUM))
		.put(NPC_LOOT_VALUE, new Info("Loot value", StatFormat.GP, StatMode.SUM))
		.put(NPC_LOOT_COUNT, new Info("Loot drops", StatFormat.NUMBER, StatMode.SUM))

		.put(ACTIVITY_KC, new Info("Counts reported by the game", StatFormat.NUMBER, StatMode.MAX))
		.put(ACTIVITY_COMPLETED, new Info("Completed while tracking", StatFormat.NUMBER, StatMode.SUM))
		.put(ACTIVITY_PB, new Info("Fastest times seen", StatFormat.CENTISECONDS, StatMode.MIN))
		.put(ACTIVITY_CLUES, new Info("Clue scrolls completed", StatFormat.NUMBER, StatMode.SUM))
		.put(ACTIVITY_QUESTS, new Info("Quests completed", StatFormat.NUMBER, StatMode.SUM))
		.put(ACTIVITY_DIARY, new Info("Achievement diary tasks", StatFormat.NUMBER, StatMode.SUM))
		.put(ACTIVITY_CA, new Info("Combat achievement tasks", StatFormat.NUMBER, StatMode.SUM))
		.put(ACTIVITY_CLOG, new Info("New collection log slots", StatFormat.NUMBER, StatMode.SUM))
		.put(ACTIVITY_PETS, new Info("Pets", StatFormat.NUMBER, StatMode.SUM))
		.put(ACTIVITY_DROPS, new Info("Notable drops", StatFormat.NUMBER, StatMode.SUM))
		.put(ACTIVITY_LOOT_VALUE, new Info("Chest & reward loot value", StatFormat.GP, StatMode.SUM))
		.put(ACTIVITY_LOOT_COUNT, new Info("Chests & rewards opened", StatFormat.NUMBER, StatMode.SUM))

		.put(WORLD_AREA_TIME, new Info("Time per area", StatFormat.TICKS, StatMode.SUM))
		.put(WORLD_TELEPORTS, new Info("Teleports & travel by method", StatFormat.NUMBER, StatMode.SUM))
		.put(MENU_OPTIONS, new Info("Menu options clicked", StatFormat.NUMBER, StatMode.SUM))
		.put(MENU_ACTIONS, new Info("Actions (option + target)", StatFormat.NUMBER, StatMode.SUM))
		.put(INTERFACES, new Info("Interfaces opened", StatFormat.NUMBER, StatMode.SUM))
		.put(EMOTES, new Info("Emotes performed", StatFormat.NUMBER, StatMode.SUM))
		.build();

	/**
	 * Keys whose format differs from their category's default.
	 */
	private static final Map<String, StatFormat> KEY_FORMATS = ImmutableMap.<String, StatFormat>builder()
		.put(Keys.MOUSE_MOVED, StatFormat.PIXELS)
		.put(Keys.CAMERA_ROTATED, StatFormat.DEGREES)
		.put(Keys.SPEC_ENERGY_USED, StatFormat.PERCENT)
		.put(Keys.RUN_ENERGY_USED, StatFormat.PERCENT)
		.put(Keys.RUN_ENERGY_RESTORED, StatFormat.PERCENT)
		.put(Keys.TOTAL_XP, StatFormat.NUMBER)
		.build();

	private static final Info DEFAULT_INFO = new Info(null, StatFormat.NUMBER, StatMode.SUM);

	private Cat()
	{
	}

	public static Info info(String category)
	{
		Info info = INFO.get(category);
		if (info != null)
		{
			return info;
		}
		// Per-skill lists: "skill.<Skill>.<List>"
		if (category.startsWith(SKILL_PREFIX))
		{
			int idx = category.indexOf('.', SKILL_PREFIX.length());
			String title = idx > 0 ? category.substring(idx + 1) : category;
			return new Info(title, StatFormat.NUMBER, StatMode.SUM);
		}
		return new Info(category, DEFAULT_INFO.getFormat(), DEFAULT_INFO.getMode());
	}

	public static StatMode mode(String category)
	{
		Info info = INFO.get(category);
		return info != null ? info.getMode() : StatMode.SUM;
	}

	public static StatFormat format(String category, String key)
	{
		StatFormat f = KEY_FORMATS.get(key);
		return f != null ? f : info(category).getFormat();
	}

	public static String skillList(String skillName, String list)
	{
		return SKILL_PREFIX + skillName + "." + list;
	}
}
