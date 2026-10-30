package com.neverenoughstats.tracker;

import com.neverenoughstats.data.Cat;
import com.neverenoughstats.data.Keys;
import com.neverenoughstats.data.StatStore;
import com.neverenoughstats.data.StatsService;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Damage dealt and taken, kills, deaths, special attacks, status effects and prayers.
 */
@Singleton
public class CombatTracker
{
	/**
	 * An NPC dying this many ticks after we last hit it still counts as our kill.
	 */
	private static final int KILL_CREDIT_TICKS = 100;
	private static final int VENOM_THRESHOLD = 1_000_000;

	private static final int[] FREEZE_SPOTANIMS = {
		SpotanimID.ICE_RUSH_IMPACT, SpotanimID.ICE_BURST_IMPACT, SpotanimID.ICE_BLITZ_IMPACT,
		SpotanimID.ICE_BARRAGE_IMPACT, SpotanimID.ENTANGLE_IMPACT, SpotanimID.SNARE_IMPACT,
		SpotanimID.BIND_IMPACT,
	};

	private final Client client;
	private final StatsService stats;
	private final TickState state;

	/**
	 * NPCs we've damaged, by index, with the tick we last hit them.
	 */
	private final Map<Integer, Integer> damagedNpcs = new HashMap<>();
	private final Map<Player, Integer> damagedPlayers = new HashMap<>();
	/**
	 * NPCs currently targeting us.
	 */
	private final Set<NPC> attackers = new HashSet<>();
	private final Set<Prayer> activePrayers = EnumSet.noneOf(Prayer.class);

	private int lastSpecEnergy = -1;
	private int lastPoison = -1;

	@Inject
	CombatTracker(Client client, StatsService stats, TickState state)
	{
		this.client = client;
		this.stats = stats;
		this.state = state;
	}

	void reset()
	{
		damagedNpcs.clear();
		damagedPlayers.clear();
		attackers.clear();
		activePrayers.clear();
		lastSpecEnergy = -1;
		lastPoison = -1;
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (stats.getStore() == null)
		{
			return;
		}
		Actor target = event.getActor();
		Hitsplat hitsplat = event.getHitsplat();
		Player local = client.getLocalPlayer();
		int amount = hitsplat.getAmount();
		int type = hitsplat.getHitsplatType();

		if (target == local)
		{
			onDamageTaken(type, amount);
			return;
		}

		if (!hitsplat.isMine())
		{
			return;
		}

		state.lastDamageDealtTick = state.tick;
		if (target instanceof NPC)
		{
			damagedNpcs.put(((NPC) target).getIndex(), state.tick);
		}
		else if (target instanceof Player)
		{
			damagedPlayers.put((Player) target, state.tick);
		}

		if (type == HitsplatID.BLOCK_ME || amount <= 0)
		{
			stats.increment(Cat.COMBAT, Keys.ZEROS_DEALT);
			return;
		}

		state.damageDealtThisTick += amount;
		stats.increment(Cat.COMBAT, Keys.HITS_LANDED);
		stats.record(Cat.COMBAT, Keys.DAMAGE_DEALT, amount);
		stats.record(Cat.COMBAT_RECORDS, Keys.MAX_HIT_DEALT, amount);
		stats.record(Cat.COMBAT_STYLE, state.lastStyle, amount);
		stats.record(Cat.COMBAT_WEAPON, state.weaponName, amount);
		stats.record(Cat.NPC_DAMAGE_DEALT, actorName(target), amount);
	}

	private void onDamageTaken(int type, int amount)
	{
		state.lastDamageTakenTick = state.tick;
		if (type == HitsplatID.BLOCK_ME || type == HitsplatID.BLOCK_OTHER)
		{
			stats.increment(Cat.COMBAT, Keys.ZEROS_TAKEN);
			return;
		}
		if (type == HitsplatID.HEAL || type == HitsplatID.CYAN_UP || type == HitsplatID.SANITY_RESTORE || amount <= 0)
		{
			return;
		}

		String damageType;
		String source;
		switch (type)
		{
			case HitsplatID.POISON:
				damageType = "Poison";
				source = "Poison";
				break;
			case HitsplatID.VENOM:
				damageType = "Venom";
				source = "Venom";
				break;
			case HitsplatID.DISEASE:
				damageType = "Disease";
				source = "Disease";
				break;
			case HitsplatID.BURN:
				damageType = "Burn";
				source = "Burn";
				break;
			case HitsplatID.BLEED:
				damageType = "Bleed";
				source = "Bleed";
				break;
			case HitsplatID.PRAYER_DRAIN:
			case HitsplatID.SANITY_DRAIN:
				// Not hitpoint damage
				return;
			default:
				damageType = "Hits";
				source = currentAttacker();
				break;
		}

		state.damageTakenThisTick += amount;
		stats.increment(Cat.COMBAT, Keys.HITS_TAKEN);
		stats.record(Cat.COMBAT, Keys.DAMAGE_TAKEN, amount);
		stats.record(Cat.COMBAT_RECORDS, Keys.MAX_HIT_TAKEN, amount);
		stats.record(Cat.COMBAT_TAKEN_TYPE, damageType, amount);
		stats.record(Cat.NPC_DAMAGE_TAKEN, source, amount);
		state.lastAttackerName = source;
		state.lastAttackerTick = state.tick;
	}

	/**
	 * Best guess at who just hit us.
	 */
	private String currentAttacker()
	{
		Player local = client.getLocalPlayer();
		Actor ourTarget = local != null ? local.getInteracting() : null;
		if (ourTarget instanceof NPC && attackers.contains(ourTarget))
		{
			return actorName(ourTarget);
		}
		attackers.removeIf(n -> n.getInteracting() != local);
		if (attackers.size() == 1)
		{
			return actorName(attackers.iterator().next());
		}
		if (ourTarget instanceof Player && ourTarget.getInteracting() == local)
		{
			return "Players (PvP)";
		}
		if (attackers.size() > 1)
		{
			return "Multiple monsters";
		}
		return "Unknown";
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (!(event.getSource() instanceof NPC))
		{
			return;
		}
		NPC npc = (NPC) event.getSource();
		if (event.getTarget() != null && event.getTarget() == client.getLocalPlayer())
		{
			attackers.add(npc);
		}
		else
		{
			attackers.remove(npc);
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Actor actor = event.getActor();
		if (actor == client.getLocalPlayer())
		{
			onLocalDeath();
		}
		else if (actor instanceof NPC)
		{
			onNpcDeath((NPC) actor);
		}
		else if (actor instanceof Player)
		{
			Integer hitTick = damagedPlayers.remove(actor);
			if (hitTick != null && state.within(hitTick, KILL_CREDIT_TICKS))
			{
				stats.increment(Cat.COMBAT, Keys.PLAYER_KILLS);
			}
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		attackers.remove(npc);
		if (npc.isDead())
		{
			onNpcDeath(npc);
		}
		else
		{
			damagedNpcs.remove(npc.getIndex());
		}
	}

	private void onNpcDeath(NPC npc)
	{
		attackers.remove(npc);
		Integer hitTick = damagedNpcs.remove(npc.getIndex());
		if (hitTick == null || !state.within(hitTick, KILL_CREDIT_TICKS) || stats.getStore() == null)
		{
			return;
		}
		String name = actorName(npc);
		stats.increment(Cat.COMBAT, Keys.KILLS);
		stats.increment(Cat.NPC_KILLED, name);
		state.recentKillName = name;
		state.recentKillTick = state.tick;
	}

	void onLocalDeath()
	{
		// ActorDeath and the "Oh dear, you are dead!" message can both report the same death
		if (state.within(state.deathTick, 10) || stats.getStore() == null)
		{
			return;
		}
		state.deathTick = state.tick;
		String cause = state.lastAttackerName != null && state.within(state.lastAttackerTick, 10)
			? state.lastAttackerName
			: "Unknown";
		stats.increment(Cat.COMBAT, Keys.DEATHS);
		stats.increment(Cat.COMBAT_DEATHS, cause);
		stats.increment(Cat.NPC_KILLED_BY, cause);
		StatStore store = stats.getStore();
		if (store != null)
		{
			store.setMeta(StatStore.META_LAST_DEATH, String.valueOf(System.currentTimeMillis()));
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (stats.getStore() == null)
		{
			return;
		}
		if (event.getVarpId() == VarPlayerID.SA_ENERGY)
		{
			int energy = event.getValue();
			if (lastSpecEnergy >= 0 && energy < lastSpecEnergy)
			{
				stats.increment(Cat.COMBAT, Keys.SPECS);
				stats.record(Cat.COMBAT, Keys.SPEC_ENERGY_USED, (lastSpecEnergy - energy) / 10);
				stats.increment(Cat.COMBAT_SPECS, state.weaponName);
			}
			lastSpecEnergy = energy;
		}
		else if (event.getVarpId() == VarPlayerID.POISON)
		{
			int poison = event.getValue();
			if (lastPoison >= 0 && poison > 0 && lastPoison <= 0)
			{
				stats.increment(Cat.COMBAT_STATUS, poison >= VENOM_THRESHOLD ? Keys.VENOMED : Keys.POISONED);
			}
			else if (lastPoison > 0 && lastPoison < VENOM_THRESHOLD && poison >= VENOM_THRESHOLD)
			{
				stats.increment(Cat.COMBAT_STATUS, Keys.VENOMED);
			}
			lastPoison = poison;
		}
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		if (stats.getStore() == null)
		{
			return;
		}
		Actor actor = event.getActor();
		Player local = client.getLocalPlayer();
		if (actor == local)
		{
			for (int id : FREEZE_SPOTANIMS)
			{
				if (actor.hasSpotAnim(id))
				{
					stats.increment(Cat.COMBAT_STATUS, Keys.FROZEN);
					break;
				}
			}
		}
		else if (local != null && actor == local.getInteracting() && actor.hasSpotAnim(SpotanimID.FAILEDSPELL_IMPACT))
		{
			stats.increment(Cat.COMBAT, Keys.SPLASHES);
		}
	}

	void tick()
	{
		if (state.damageTakenThisTick > 0)
		{
			stats.record(Cat.COMBAT_RECORDS, Keys.MOST_DAMAGE_TICK, state.damageTakenThisTick);
		}

		for (Prayer prayer : Prayer.values())
		{
			if (client.getVarbitValue(prayer.getVarbit()) == 1)
			{
				String name = prayerName(prayer);
				stats.increment(Cat.PRAYER_TIME, name);
				if (activePrayers.add(prayer))
				{
					stats.increment(Cat.PRAYER_ACTIVATIONS, name);
				}
			}
			else
			{
				activePrayers.remove(prayer);
			}
		}

		damagedNpcs.values().removeIf(t -> !state.within(t, KILL_CREDIT_TICKS));
		damagedPlayers.values().removeIf(t -> !state.within(t, KILL_CREDIT_TICKS));
	}

	private static String prayerName(Prayer prayer)
	{
		String name = Text.titleCase(prayer);
		return name.startsWith("Rp ") ? name.substring(3) : name;
	}

	static String actorName(Actor actor)
	{
		if (actor instanceof Player)
		{
			return "Players (PvP)";
		}
		String name = actor != null ? Names.clean(actor.getName()) : "";
		return name.isEmpty() ? "Unknown" : name;
	}
}
