package com.neverenoughstats.tracker;

import com.neverenoughstats.data.Cat;
import com.neverenoughstats.data.Keys;
import com.neverenoughstats.data.StatsService;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Skill;

/**
 * Hitpoints healed, prayer restored and run energy used/restored, with a
 * best guess at the source of each.
 * <p>
 * The game only says that a number went up, so the source is inferred from
 * what else happened on the same tick: food eaten, potion drunk, damage dealt
 * while wearing a healing item, a blood spell cast, or a restore pool used.
 */
@Singleton
public class HealingTracker
{
	private final Client client;
	private final StatsService stats;
	private final TickState state;

	private int lastHp = -1;
	private int lastHpLevel = -1;
	private int lastPrayer = -1;
	private int lastEnergy = -1;

	@Inject
	HealingTracker(Client client, StatsService stats, TickState state)
	{
		this.client = client;
		this.stats = stats;
		this.state = state;
	}

	void reset()
	{
		lastHp = -1;
		lastHpLevel = -1;
		lastPrayer = -1;
		lastEnergy = -1;
	}

	void tick()
	{
		boolean recentlyDied = state.within(state.deathTick, 20);

		int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int hpLevel = client.getRealSkillLevel(Skill.HITPOINTS);
		if (lastHp >= 0 && !recentlyDied && hpLevel == lastHpLevel)
		{
			// Damage taken this tick hides part of the heal. Only count hidden heals
			// when something on this tick is known to heal, so a hitsplat that shows
			// before the hitpoints update isn't mistaken for healing.
			int raw = hp - lastHp;
			int healed = raw + state.damageTakenThisTick;
			if (healed > 0 && (raw > 0 || hasKnownHealSource()))
			{
				String source = hpSource(healed);
				stats.record(Cat.HEAL, Keys.HP_HEALED, healed);
				stats.record(Cat.HEAL_SOURCE, source, healed);
			}
		}
		lastHp = hp;
		lastHpLevel = hpLevel;

		int prayer = client.getBoostedSkillLevel(Skill.PRAYER);
		if (lastPrayer >= 0 && !recentlyDied)
		{
			int delta = prayer - lastPrayer;
			if (delta < 0)
			{
				stats.record(Cat.PRAYER_POINTS, Keys.PRAYER_DRAINED, -delta);
			}
			else if (delta > 0 && !state.xpThisTick.containsKey(Skill.PRAYER))
			{
				stats.record(Cat.PRAYER_POINTS, Keys.PRAYER_RESTORED, delta);
				stats.record(Cat.HEAL_PRAYER_SOURCE, restoreSource(true), delta);
			}
		}
		lastPrayer = prayer;

		int energy = client.getEnergy() / 100;
		if (lastEnergy >= 0 && !recentlyDied)
		{
			int delta = energy - lastEnergy;
			if (delta < 0)
			{
				stats.record(Cat.MOVEMENT, Keys.RUN_ENERGY_USED, -delta);
			}
			else if (delta > 0)
			{
				stats.record(Cat.MOVEMENT, Keys.RUN_ENERGY_RESTORED, delta);
				stats.record(Cat.HEAL_ENERGY_SOURCE, restoreSource(false), delta);
			}
		}
		lastEnergy = energy;
	}

	private boolean hasKnownHealSource()
	{
		return !state.eatenThisTick.isEmpty() || !state.drunkThisTick.isEmpty()
			|| (state.damageDealtThisTick > 0 && (state.bloodFury || state.sangStaff || state.guthans));
	}

	private String hpSource(int healed)
	{
		if (!state.eatenThisTick.isEmpty())
		{
			return join(state.eatenThisTick);
		}
		if (!state.drunkThisTick.isEmpty())
		{
			return join(state.drunkThisTick);
		}
		if (state.damageDealtThisTick > 0)
		{
			if (state.bloodFury)
			{
				return "Amulet of blood fury";
			}
			if (state.sangStaff)
			{
				return state.weaponName;
			}
			if (state.lastSpell != null && state.within(state.lastSpellTick, 8)
				&& state.lastSpell.toLowerCase(Locale.ROOT).startsWith("blood"))
			{
				return state.lastSpell;
			}
			if (state.guthans)
			{
				return "Guthan's set";
			}
		}
		String obj = recentObject();
		if (obj != null)
		{
			return obj;
		}
		if (healed <= 2)
		{
			return Keys.SOURCE_REGEN;
		}
		return Keys.SOURCE_OTHER;
	}

	private String restoreSource(boolean prayer)
	{
		if (!state.drunkThisTick.isEmpty())
		{
			return join(state.drunkThisTick);
		}
		if (!state.eatenThisTick.isEmpty())
		{
			return join(state.eatenThisTick);
		}
		String obj = recentObject();
		if (obj != null)
		{
			return obj;
		}
		return prayer ? Keys.SOURCE_OTHER : Keys.SOURCE_REGEN;
	}

	/**
	 * Restore pools, altars and the like, if one was used in the last few ticks.
	 */
	private String recentObject()
	{
		if (state.lastObjectTarget == null || !state.within(state.lastObjectTick, 5))
		{
			return null;
		}
		String option = state.lastObjectOption.toLowerCase(Locale.ROOT);
		if (option.startsWith("drink") || option.startsWith("pray") || option.startsWith("recharge")
			|| option.startsWith("restore") || option.startsWith("use") || option.startsWith("touch"))
		{
			return state.lastObjectTarget;
		}
		return null;
	}

	private static String join(List<String> names)
	{
		return names.size() == 1 ? names.get(0) : String.join(" + ", names);
	}
}
