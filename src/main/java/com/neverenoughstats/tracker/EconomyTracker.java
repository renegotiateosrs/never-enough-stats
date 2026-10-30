package com.neverenoughstats.tracker;

import com.neverenoughstats.data.Cat;
import com.neverenoughstats.data.Keys;
import com.neverenoughstats.data.StatStore;
import com.neverenoughstats.data.StatsService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * Loot from monsters, players and chests, Grand Exchange trades and bank value.
 */
@Singleton
public class EconomyTracker
{
	private static final String META_GE_PREFIX = "ge.";

	private final StatsService stats;
	private final Names names;

	/**
	 * GE updates arrive in a burst at login, possibly before saved progress
	 * has loaded; hold them until it has, to avoid counting them twice.
	 */
	private final List<GrandExchangeOfferChanged> pendingOffers = new ArrayList<>();

	@Inject
	EconomyTracker(StatsService stats, Names names)
	{
		this.stats = stats;
		this.names = names;
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		String name = CombatTracker.actorName(event.getNpc());
		long value = recordLoot(event.getItems());
		stats.record(Cat.NPC_LOOT_VALUE, name, value);
		stats.increment(Cat.NPC_LOOT_COUNT, name);
		stats.record(Cat.ECONOMY, Keys.GP_LOOT_NPC, value);
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		long value = recordLoot(event.getItems());
		stats.record(Cat.ECONOMY, Keys.GP_LOOT_PVP, value);
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		// NPC and player loot are handled above; this covers chests, raids, clues, minigame rewards...
		if (event.getType() != LootRecordType.EVENT)
		{
			return;
		}
		String name = event.getName();
		long value = recordLoot(event.getItems());
		stats.record(Cat.ACTIVITY_LOOT_VALUE, name, value);
		stats.record(Cat.ACTIVITY_LOOT_COUNT, name, Math.max(1, event.getAmount()));
		stats.record(Cat.ECONOMY, Keys.GP_LOOT_OTHER, value);
	}

	private long recordLoot(Collection<ItemStack> items)
	{
		long value = 0;
		for (ItemStack item : items)
		{
			String name = names.item(item.getId());
			if (name == null)
			{
				continue;
			}
			stats.record(Cat.ITEM_LOOT, name, item.getQuantity());
			value += names.price(item.getId()) * item.getQuantity();
		}
		return value;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.BANK)
		{
			return;
		}
		ItemContainer bank = event.getItemContainer();
		long value = 0;
		for (Item item : bank.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				value += names.price(item.getId()) * item.getQuantity();
			}
		}
		stats.record(Cat.ECONOMY_LATEST, Keys.BANK_VALUE, value);
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		pendingOffers.add(event);
		processOffers();
	}

	void tick()
	{
		if (!pendingOffers.isEmpty())
		{
			processOffers();
		}
	}

	void reset()
	{
		pendingOffers.clear();
	}

	private void processOffers()
	{
		StatStore store = stats.getStore();
		if (store == null || !store.isLoaded())
		{
			return;
		}
		for (GrandExchangeOfferChanged event : pendingOffers)
		{
			processOffer(store, event.getSlot(), event.getOffer());
		}
		pendingOffers.clear();
	}

	private void processOffer(StatStore store, int slot, GrandExchangeOffer offer)
	{
		String metaKey = META_GE_PREFIX + slot;
		GrandExchangeOfferState offerState = offer.getState();
		if (offerState == GrandExchangeOfferState.EMPTY)
		{
			store.setMeta(metaKey, null);
			return;
		}

		boolean buying = offerState == GrandExchangeOfferState.BUYING
			|| offerState == GrandExchangeOfferState.BOUGHT
			|| offerState == GrandExchangeOfferState.CANCELLED_BUY;
		int itemId = offer.getItemId();
		int sold = offer.getQuantitySold();
		long spent = offer.getSpent();

		// Saved as "itemId:buying:quantitySold:spent" so progress isn't counted twice
		int prevSold = 0;
		long prevSpent = 0;
		String prev = store.getMeta(metaKey);
		if (prev != null)
		{
			String[] parts = prev.split(":");
			try
			{
				if (parts.length == 4 && Integer.parseInt(parts[0]) == itemId && Boolean.parseBoolean(parts[1]) == buying
					&& Integer.parseInt(parts[2]) <= sold)
				{
					prevSold = Integer.parseInt(parts[2]);
					prevSpent = Long.parseLong(parts[3]);
				}
			}
			catch (NumberFormatException ignored)
			{
				// treat as a new offer
			}
		}
		store.setMeta(metaKey, itemId + ":" + buying + ":" + sold + ":" + spent);

		int newQty = sold - prevSold;
		long newGp = spent - prevSpent;
		if (newQty <= 0)
		{
			return;
		}
		String name = names.item(itemId);
		if (name == null)
		{
			return;
		}
		if (buying)
		{
			stats.record(Cat.ITEM_GE_BOUGHT, name, newQty);
			stats.record(Cat.ECONOMY, Keys.GP_GE_SPENT, newGp);
		}
		else
		{
			stats.record(Cat.ITEM_GE_SOLD, name, newQty);
			stats.record(Cat.ECONOMY, Keys.GP_GE_EARNED, newGp);
		}
	}
}
