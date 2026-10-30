package com.neverenoughstats.tracker;

import com.neverenoughstats.data.Cat;
import com.neverenoughstats.data.Keys;
import com.neverenoughstats.data.StatsService;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Works out where every item in the inventory and equipment came from and
 * went to. Once per tick the inventory + worn items are compared with the
 * previous tick, and each change is explained using what the player clicked,
 * which interfaces were open and which XP was gained.
 */
@Singleton
public class ItemLedger
{
	/**
	 * Skills whose XP drops say nothing about items appearing or disappearing.
	 */
	private static final Set<Skill> NON_ITEM_SKILLS = EnumSet.of(
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS, Skill.RANGED, Skill.SLAYER
	);

	private static final int ITEM_ACTION_WINDOW = 5;
	private static final int TAKE_WINDOW = 100;
	private static final int DEATH_WINDOW = 15;

	private final Client client;
	private final StatsService stats;
	private final TickState state;
	private final Names names;

	private Map<Integer, Integer> lastInv;
	private Map<Integer, Integer> lastWorn;
	private boolean dirty;
	private int settleTicks;

	@Inject
	ItemLedger(Client client, StatsService stats, TickState state, Names names)
	{
		this.client = client;
		this.stats = stats;
		this.state = state;
		this.names = names;
	}

	/**
	 * Forget the previous snapshot, e.g. after logging in or hopping when the
	 * containers are re-sent.
	 */
	void reset()
	{
		lastInv = null;
		lastWorn = null;
		settleTicks = 2;
		dirty = true;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.INV || id == InventoryID.WORN)
		{
			dirty = true;
		}
		if (id == InventoryID.WORN)
		{
			refreshEquipment(event.getItemContainer());
		}
	}

	private void refreshEquipment(ItemContainer worn)
	{
		Item weapon = worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		String weaponName = weapon != null ? names.item(weapon.getId()) : null;
		state.weaponName = weaponName != null ? weaponName : "Unarmed";
		state.sangStaff = weaponName != null && weaponName.toLowerCase(Locale.ROOT).contains("sanguinesti");

		Item amulet = worn.getItem(EquipmentInventorySlot.AMULET.getSlotIdx());
		String amuletName = amulet != null ? names.item(amulet.getId()) : null;
		state.bloodFury = amuletName != null && amuletName.toLowerCase(Locale.ROOT).contains("blood fury");

		int guthan = 0;
		for (Item item : worn.getItems())
		{
			String n = names.item(item.getId());
			if (n != null && n.startsWith("Guthan's"))
			{
				guthan++;
			}
		}
		state.guthans = guthan >= 4;
	}

	void tick()
	{
		if (settleTicks > 0)
		{
			settleTicks--;
			lastInv = read(InventoryID.INV);
			lastWorn = read(InventoryID.WORN);
			return;
		}
		if (!dirty)
		{
			return;
		}

		Map<Integer, Integer> inv = read(InventoryID.INV);
		Map<Integer, Integer> worn = read(InventoryID.WORN);
		if (inv == null)
		{
			return;
		}
		dirty = false;
		if (worn == null)
		{
			worn = Collections.emptyMap();
		}
		if (lastInv == null)
		{
			lastInv = inv;
			lastWorn = worn;
			return;
		}

		Map<Integer, Integer> prevInv = lastInv;
		Map<Integer, Integer> prevWorn = lastWorn != null ? lastWorn : Collections.emptyMap();
		lastInv = inv;
		lastWorn = worn;

		if (stats.getStore() == null)
		{
			return;
		}

		Set<Integer> ids = new HashSet<>(inv.keySet());
		ids.addAll(worn.keySet());
		ids.addAll(prevInv.keySet());
		ids.addAll(prevWorn.keySet());

		Map<Integer, Integer> deltas = new HashMap<>();
		Map<Integer, Integer> wornDeltas = new HashMap<>();
		for (int id : ids)
		{
			int now = inv.getOrDefault(id, 0) + worn.getOrDefault(id, 0);
			int before = prevInv.getOrDefault(id, 0) + prevWorn.getOrDefault(id, 0);
			if (now != before)
			{
				deltas.put(id, now - before);
			}
			int wd = worn.getOrDefault(id, 0) - prevWorn.getOrDefault(id, 0);
			if (wd != 0)
			{
				wornDeltas.put(id, wd);
			}
		}

		updateCarriedValue(inv, worn);
		if (!deltas.isEmpty())
		{
			classify(deltas, wornDeltas);
		}
	}

	private void classify(Map<Integer, Integer> deltas, Map<Integer, Integer> wornDeltas)
	{
		boolean storage = state.within(state.lastStorageItemActionTick, 2);
		for (int group : InteractionTracker.STORAGE_GROUPS)
		{
			storage |= state.groupRecentlyOpen(group, 1);
		}
		boolean shop = false;
		for (int group : InteractionTracker.SHOP_GROUPS)
		{
			shop |= state.groupRecentlyOpen(group, 1);
		}
		boolean trade = false;
		for (int group : InteractionTracker.TRADE_GROUPS)
		{
			trade |= state.groupRecentlyOpen(group, 2);
		}
		boolean died = state.within(state.deathTick, DEATH_WINDOW);
		Skill skill = primarySkill();
		boolean alching = state.lastSpell != null && state.within(state.lastSpellTick, 6)
			&& state.lastSpell.toLowerCase(Locale.ROOT).contains("alchemy")
			&& state.xpThisTick.containsKey(Skill.MAGIC);

		// Removals first: they tell us whether food/potions were consumed this tick
		for (Map.Entry<Integer, Integer> e : deltas.entrySet())
		{
			if (e.getValue() >= 0)
			{
				continue;
			}
			int id = e.getKey();
			int n = -e.getValue();
			String name = names.item(id);
			if (name == null)
			{
				continue;
			}

			if (shop)
			{
				if (id == ItemID.COINS)
				{
					stats.record(Cat.ECONOMY, Keys.GP_SHOP_SPENT, n);
				}
				else
				{
					stats.record(Cat.ITEM_SOLD, name, n);
				}
				continue;
			}
			if (trade)
			{
				stats.record(Cat.ITEM_TRADED_OUT, name, n);
				continue;
			}
			if (storage)
			{
				continue;
			}
			if (died)
			{
				stats.record(Cat.ITEM_LOST_DEATH, name, n);
				continue;
			}

			TickState.PendingAction pending = state.pendingItemActions.get(id);
			if (pending != null && state.within(pending.tick, ITEM_ACTION_WINDOW))
			{
				onItemAction(pending.option.toLowerCase(Locale.ROOT), name, n, skill);
				continue;
			}
			if (alching && state.lastSpellTarget != null && state.lastSpellTarget.equalsIgnoreCase(name))
			{
				stats.record(Cat.ITEM_ALCHED, name, n);
				continue;
			}
			int wornDelta = wornDeltas.getOrDefault(id, 0);
			if (wornDelta < 0 && state.within(state.lastRangedXpTick, 3))
			{
				stats.record(Cat.ITEM_FIRED, name, Math.min(n, -wornDelta));
				continue;
			}
			if (skill != null)
			{
				usedInSkill(skill, name, n);
				continue;
			}
			stats.record(Cat.ITEM_OTHER_LOST, name, n);
		}

		boolean consumed = !state.eatenThisTick.isEmpty() || !state.drunkThisTick.isEmpty();
		for (Map.Entry<Integer, Integer> e : deltas.entrySet())
		{
			if (e.getValue() <= 0)
			{
				continue;
			}
			int id = e.getKey();
			int n = e.getValue();
			String name = names.item(id);
			if (name == null)
			{
				continue;
			}

			if (shop)
			{
				if (id == ItemID.COINS)
				{
					stats.record(Cat.ECONOMY, Keys.GP_SHOP_EARNED, n);
				}
				else
				{
					stats.record(Cat.ITEM_BOUGHT, name, n);
				}
				continue;
			}
			if (trade)
			{
				stats.record(Cat.ITEM_TRADED_IN, name, n);
				continue;
			}
			if (storage)
			{
				continue;
			}

			Integer takeTick = state.pendingTakes.get(id);
			if (takeTick != null && state.within(takeTick, TAKE_WINDOW))
			{
				state.pendingTakes.remove(id);
				stats.record(Cat.ITEM_PICKED_UP, name, n);
				continue;
			}
			// Empty vials, lower-dose potions, pie dishes...
			if (consumed)
			{
				continue;
			}
			if (alching && id == ItemID.COINS)
			{
				stats.record(Cat.ECONOMY, Keys.GP_ALCH, n);
				continue;
			}
			if (skill != null)
			{
				stats.record(Cat.ITEM_PRODUCED, name, n);
				stats.record(Cat.skillList(skill.getName(), "Gathered / made"), name, n);
				continue;
			}
			stats.record(Cat.ITEM_OTHER_GAINED, name, n);
		}
	}

	private void onItemAction(String option, String name, int n, Skill skill)
	{
		switch (option)
		{
			case "eat":
				stats.record(Cat.ITEM_EATEN, name, n);
				stats.record(Cat.HEAL, Keys.FOOD_EATEN, n);
				state.eatenThisTick.add(name);
				break;
			case "drink":
				String potion = Names.stripDose(name);
				stats.record(Cat.ITEM_DRUNK, potion, n);
				stats.record(Cat.HEAL, Keys.DOSES_DRUNK, n);
				state.drunkThisTick.add(potion);
				break;
			case "drop":
				stats.record(Cat.ITEM_DROPPED, name, n);
				break;
			case "destroy":
				stats.record(Cat.ITEM_DESTROYED, name, n);
				break;
			case "open":
				stats.record(Cat.ITEM_OPENED, name, n);
				break;
			default:
				if (skill != null)
				{
					usedInSkill(skill, name, n);
				}
				else
				{
					stats.record(Cat.ITEM_USED_UP, name, n);
				}
				break;
		}
	}

	private void usedInSkill(Skill skill, String name, int n)
	{
		stats.record(Cat.ITEM_SKILL_USED, name, n);
		stats.record(Cat.skillList(skill.getName(), "Used"), name, n);
	}

	/**
	 * @return the non-combat skill that gained the most XP this tick, if any
	 */
	private Skill primarySkill()
	{
		Skill best = null;
		int bestXp = 0;
		for (Map.Entry<Skill, Integer> e : state.xpThisTick.entrySet())
		{
			if (!NON_ITEM_SKILLS.contains(e.getKey()) && e.getValue() > bestXp)
			{
				best = e.getKey();
				bestXp = e.getValue();
			}
		}
		return best;
	}

	private void updateCarriedValue(Map<Integer, Integer> inv, Map<Integer, Integer> worn)
	{
		long value = 0;
		for (Map.Entry<Integer, Integer> e : inv.entrySet())
		{
			value += names.price(e.getKey()) * e.getValue();
		}
		for (Map.Entry<Integer, Integer> e : worn.entrySet())
		{
			value += names.price(e.getKey()) * e.getValue();
		}
		stats.record(Cat.ECONOMY_LATEST, Keys.CARRIED_VALUE, value);
	}

	private Map<Integer, Integer> read(int containerId)
	{
		ItemContainer container = client.getItemContainer(containerId);
		if (container == null)
		{
			return null;
		}
		Map<Integer, Integer> counts = new HashMap<>();
		for (Item item : container.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				counts.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return counts;
	}
}
