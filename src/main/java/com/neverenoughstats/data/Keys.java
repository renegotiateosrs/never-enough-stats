package com.neverenoughstats.data;

/**
 * Key names for stats in the fixed categories. These are shown to the player
 * as-is and are written to the save file, so changing one resets that stat.
 */
public final class Keys
{
	// Cat.TIME
	public static final String TIME_LOGGED_IN = "Time logged in";
	public static final String TIME_ACTIVE = "Active time";
	public static final String TIME_AFK = "AFK time";
	public static final String TIME_COMBAT = "Time in combat";
	public static final String TIME_WILDERNESS = "Time in the Wilderness";
	public static final String TIME_RUNNING = "Time with run on";

	// Cat.TIME_RECORDS
	public static final String LONGEST_SESSION = "Longest session";

	// Cat.SESSION
	public static final String LOGINS = "Logins";
	public static final String WORLD_HOPS = "World hops";
	public static final String DAYS_PLAYED = "Days played";
	public static final String LEVEL_UPS = "Level-ups";

	// Cat.INPUT
	public static final String CLICKS = "Mouse clicks";
	public static final String LEFT_CLICKS = "Left clicks";
	public static final String RIGHT_CLICKS = "Right clicks";
	public static final String MIDDLE_CLICKS = "Middle clicks";
	public static final String SCROLLS = "Mouse wheel scrolls";
	public static final String MOUSE_MOVED = "Mouse distance moved";
	public static final String KEYS_PRESSED = "Keys pressed";
	public static final String CAMERA_ROTATED = "Camera rotated";
	public static final String MENU_CLICKS = "Menu actions chosen";

	// Cat.MOVEMENT
	public static final String TILES_WALKED = "Tiles walked";
	public static final String TILES_RUN = "Tiles run";
	public static final String TELEPORTS = "Teleports & long-distance travel";
	public static final String CLIMBS = "Ladders & stairs climbed";
	public static final String DOORS = "Doors & gates opened";
	public static final String RUN_ENERGY_USED = "Run energy used";
	public static final String RUN_ENERGY_RESTORED = "Run energy restored";
	public static final String AREAS_VISITED = "New areas visited";

	// Cat.SOCIAL
	public static final String PUBLIC_MESSAGES = "Public messages sent";
	public static final String PRIVATE_SENT = "Private messages sent";
	public static final String PRIVATE_RECEIVED = "Private messages received";
	public static final String CLAN_MESSAGES = "Clan / friends chat messages sent";
	public static final String TRADES = "Trades completed";
	public static final String EMOTES = "Emotes performed";

	// Cat.ECONOMY
	public static final String GP_ALCH = "Coins from alchemy";
	public static final String GP_SHOP_SPENT = "Coins spent in shops";
	public static final String GP_SHOP_EARNED = "Coins earned from shops";
	public static final String GP_GE_SPENT = "Coins spent on the GE";
	public static final String GP_GE_EARNED = "Coins earned on the GE";
	public static final String GP_LOOT_NPC = "Loot value from monsters";
	public static final String GP_LOOT_PVP = "Loot value from players";
	public static final String GP_LOOT_OTHER = "Loot value from chests & rewards";

	// Cat.ECONOMY_LATEST
	public static final String BANK_VALUE = "Bank value";
	public static final String CARRIED_VALUE = "Inventory + worn value";

	// Cat.ACCOUNT_LATEST
	public static final String TOTAL_LEVEL = "Total level";
	public static final String TOTAL_XP = "Total XP";
	public static final String COMBAT_LEVEL = "Combat level";

	// Cat.COMBAT
	public static final String DAMAGE_DEALT = "Damage dealt";
	public static final String DAMAGE_TAKEN = "Damage taken";
	public static final String HITS_LANDED = "Hits landed";
	public static final String ZEROS_DEALT = "Zero hits dealt";
	public static final String HITS_TAKEN = "Hits taken";
	public static final String ZEROS_TAKEN = "Zero hits taken";
	public static final String SPLASHES = "Spells splashed";
	public static final String SPECS = "Special attacks used";
	public static final String SPEC_ENERGY_USED = "Special attack energy used";
	public static final String KILLS = "Monsters killed";
	public static final String SLAYER_KILLS = "Slayer task kills";
	public static final String PLAYER_KILLS = "Players killed";
	public static final String DEATHS = "Deaths";
	public static final String SUPERIORS = "Superior slayer monsters";

	// Cat.COMBAT_RECORDS
	public static final String MAX_HIT_DEALT = "Highest hit dealt";
	public static final String MAX_HIT_TAKEN = "Highest hit taken";
	public static final String MOST_DAMAGE_TICK = "Most damage taken in one tick";

	// Cat.COMBAT_STATUS
	public static final String POISONED = "Times poisoned";
	public static final String VENOMED = "Times venomed";
	public static final String FROZEN = "Times frozen / bound";
	public static final String TELEBLOCKED = "Times teleblocked";
	public static final String STUNNED = "Times stunned";

	// Cat.PRAYER_POINTS
	public static final String PRAYER_DRAINED = "Prayer points drained";
	public static final String PRAYER_RESTORED = "Prayer points restored";

	// Cat.HEAL
	public static final String HP_HEALED = "Hitpoints healed";
	public static final String FOOD_EATEN = "Food eaten";
	public static final String DOSES_DRUNK = "Potion doses drunk";

	// Heal sources that aren't items
	public static final String SOURCE_REGEN = "Natural regeneration";
	public static final String SOURCE_OTHER = "Other / unknown";

	private Keys()
	{
	}
}
