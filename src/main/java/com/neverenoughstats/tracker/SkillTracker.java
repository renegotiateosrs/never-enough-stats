package com.neverenoughstats.tracker;

import com.neverenoughstats.data.Cat;
import com.neverenoughstats.data.Keys;
import com.neverenoughstats.data.StatsService;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;

/**
 * XP, levels, actions (XP drops) and time spent per skill.
 */
@Singleton
public class SkillTracker
{
	/**
	 * Gaps between XP drops longer than this (in ticks) aren't counted as time training.
	 */
	private static final int MAX_TRAINING_GAP = 50;

	private final Client client;
	private final StatsService stats;
	private final TickState state;

	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);
	private final Map<Skill, Integer> lastLevel = new EnumMap<>(Skill.class);
	private final Map<Skill, Integer> lastXpTick = new EnumMap<>(Skill.class);
	private boolean accountStatsDirty;

	@Inject
	SkillTracker(Client client, StatsService stats, TickState state)
	{
		this.client = client;
		this.stats = stats;
		this.state = state;
	}

	void reset()
	{
		lastXp.clear();
		lastLevel.clear();
		lastXpTick.clear();
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		int xp = event.getXp();
		int level = event.getLevel();

		Integer prevXp = lastXp.put(skill, xp);
		Integer prevLevel = lastLevel.put(skill, level);
		// The first update after login just tells us where we are
		// (an xp of 0 can also be the client's placeholder before stats arrive)
		if (prevXp == null || prevXp == 0 || prevLevel == null || stats.getStore() == null)
		{
			accountStatsDirty = true;
			return;
		}

		int gained = xp - prevXp;
		if (gained > 0)
		{
			String name = skill.getName();
			state.xpThisTick.merge(skill, gained, Integer::sum);
			stats.record(Cat.SKILL_XP, name, gained);
			stats.increment(Cat.SKILL_ACTIONS, name);

			Integer lastTick = lastXpTick.put(skill, state.tick);
			if (lastTick != null)
			{
				int gap = state.tick - lastTick;
				if (gap > 0 && gap <= MAX_TRAINING_GAP)
				{
					stats.record(Cat.SKILL_TIME, name, gap);
				}
			}

			switch (skill)
			{
				case RANGED:
					state.lastRangedXpTick = state.tick;
					state.lastStyle = "Ranged";
					break;
				case MAGIC:
					state.lastMagicXpTick = state.tick;
					state.lastStyle = "Magic";
					break;
				case ATTACK:
				case STRENGTH:
					state.lastStyle = "Melee";
					break;
				case DEFENCE:
					// Defensive ranged/magic also gives Defence XP
					if (!state.within(state.lastRangedXpTick, 0) && !state.within(state.lastMagicXpTick, 0))
					{
						state.lastStyle = "Melee";
					}
					break;
				default:
					break;
			}
			accountStatsDirty = true;
		}

		if (level > prevLevel)
		{
			stats.record(Cat.SKILL_LEVELS, skill.getName(), level - prevLevel);
			stats.record(Cat.SESSION, Keys.LEVEL_UPS, level - prevLevel);
			accountStatsDirty = true;
		}
	}

	void tick()
	{
		Integer slayerXp = state.xpThisTick.get(Skill.SLAYER);
		if (slayerXp != null && state.recentKillName != null && state.within(state.recentKillTick, 3))
		{
			stats.increment(Cat.COMBAT, Keys.SLAYER_KILLS);
			stats.increment(Cat.skillList(Skill.SLAYER.getName(), "Task kills"), state.recentKillName);
			state.recentKillName = null;
		}

		if (accountStatsDirty && stats.getStore() != null)
		{
			accountStatsDirty = false;
			stats.record(Cat.ACCOUNT_LATEST, Keys.TOTAL_LEVEL, client.getTotalLevel());
			stats.record(Cat.ACCOUNT_LATEST, Keys.TOTAL_XP, client.getOverallExperience());
			Player p = client.getLocalPlayer();
			if (p != null && p.getCombatLevel() > 0)
			{
				stats.record(Cat.ACCOUNT_LATEST, Keys.COMBAT_LEVEL, p.getCombatLevel());
			}
		}
	}
}
