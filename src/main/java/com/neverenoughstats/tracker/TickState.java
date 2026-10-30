package com.neverenoughstats.tracker;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;
import net.runelite.api.Skill;

/**
 * Facts shared between trackers. Most of the game's effects arrive a tick or
 * more after the click that caused them, so trackers leave notes here (what
 * was clicked, which XP was gained, who hit whom) and the per-tick processing
 * uses them to work out why something happened.
 * <p>
 * Only accessed from the client thread.
 */
@Singleton
class TickState
{
	static final int NEVER = Integer.MIN_VALUE / 2;

	int tick;

	// Per-tick facts, cleared at the end of every tick
	final Map<Skill, Integer> xpThisTick = new EnumMap<>(Skill.class);
	int damageDealtThisTick;
	int damageTakenThisTick;
	final List<String> eatenThisTick = new ArrayList<>();
	final List<String> drunkThisTick = new ArrayList<>();

	// Combat
	int lastDamageDealtTick = NEVER;
	int lastDamageTakenTick = NEVER;
	int lastRangedXpTick = NEVER;
	int lastMagicXpTick = NEVER;
	int deathTick = NEVER;
	String lastStyle = "Melee";
	String lastAttackerName;
	int lastAttackerTick = NEVER;
	String recentKillName;
	int recentKillTick = NEVER;

	// Equipment, refreshed when the worn container changes
	String weaponName = "Unarmed";
	boolean bloodFury;
	boolean sangStaff;
	boolean guthans;

	// Menu context
	final Map<Integer, PendingAction> pendingItemActions = new HashMap<>();
	final Map<Integer, Integer> pendingTakes = new HashMap<>();
	String lastSpell;
	String lastSpellTarget;
	int lastSpellTick = NEVER;
	String lastObjectOption;
	String lastObjectTarget;
	int lastObjectTick = NEVER;
	int lastClimbTick = NEVER;
	String teleportIntent;
	int teleportIntentTick = NEVER;
	int lastStorageItemActionTick = NEVER;

	// Interfaces
	final Set<Integer> openGroups = new HashSet<>();
	final Map<Integer, Integer> groupClosedTick = new HashMap<>();

	static final class PendingAction
	{
		final String option;
		final int tick;

		PendingAction(String option, int tick)
		{
			this.option = option;
			this.tick = tick;
		}
	}

	boolean within(int eventTick, int window)
	{
		return tick - eventTick <= window;
	}

	/**
	 * @return true if the interface group is open, or closed within {@code window} ticks
	 */
	boolean groupRecentlyOpen(int groupId, int window)
	{
		if (openGroups.contains(groupId))
		{
			return true;
		}
		Integer closed = groupClosedTick.get(groupId);
		return closed != null && within(closed, window);
	}

	boolean inCombat()
	{
		return within(lastDamageDealtTick, 8) || within(lastDamageTakenTick, 8);
	}

	void endTick()
	{
		xpThisTick.clear();
		damageDealtThisTick = 0;
		damageTakenThisTick = 0;
		eatenThisTick.clear();
		drunkThisTick.clear();

		pendingItemActions.values().removeIf(a -> !within(a.tick, 10));
		pendingTakes.values().removeIf(t -> !within(t, 100));
	}

	void reset()
	{
		endTick();
		pendingItemActions.clear();
		pendingTakes.clear();
		openGroups.clear();
		groupClosedTick.clear();
		lastAttackerName = null;
		recentKillName = null;
		lastSpell = null;
		teleportIntent = null;
		lastObjectOption = null;
		lastObjectTarget = null;
		deathTick = NEVER;
	}
}
