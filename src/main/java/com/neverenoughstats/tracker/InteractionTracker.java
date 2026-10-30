package com.neverenoughstats.tracker;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.neverenoughstats.data.Cat;
import com.neverenoughstats.data.Keys;
import com.neverenoughstats.data.StatsService;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.eventbus.Subscribe;

/**
 * Counts menu clicks and interfaces opened, and leaves notes in
 * {@link TickState} about what the player asked to do so later changes
 * (an item disappearing, a teleport) can be explained.
 */
@Singleton
public class InteractionTracker
{
	/**
	 * Interfaces where items move in or out of the inventory without being
	 * gained or lost (banks and other storage).
	 */
	static final Set<Integer> STORAGE_GROUPS = ImmutableSet.of(
		InterfaceID.BANKMAIN,
		InterfaceID.BANK_DEPOSITBOX,
		InterfaceID.BANK_DEPOSIT_IMP,
		InterfaceID.GE_OFFERS,
		InterfaceID.GE_COLLECT,
		InterfaceID.SEED_VAULT,
		InterfaceID.SEED_VAULT_DEPOSIT,
		InterfaceID.FARMING_TOOLS,
		InterfaceID.POH_COSTUMES,
		InterfaceID.WILDERNESS_LOOTINGBAG,
		InterfaceID.TACKLE_BOX_MAIN,
		InterfaceID.RAIDS_STORAGE_SHARED
	);

	static final Set<Integer> SHOP_GROUPS = ImmutableSet.of(InterfaceID.SHOPMAIN);
	static final Set<Integer> TRADE_GROUPS = ImmutableSet.of(InterfaceID.TRADEMAIN, InterfaceID.TRADECONFIRM);

	private static final Map<Integer, String> INTERFACE_NAMES = ImmutableMap.<Integer, String>builder()
		.put(InterfaceID.BANKMAIN, "Bank")
		.put(InterfaceID.BANK_DEPOSITBOX, "Deposit box")
		.put(InterfaceID.GE_OFFERS, "Grand Exchange")
		.put(InterfaceID.SHOPMAIN, "Shop")
		.put(InterfaceID.TRADEMAIN, "Trade")
		.put(InterfaceID.SEED_VAULT, "Seed vault")
		.put(InterfaceID.FARMING_TOOLS, "Tool leprechaun")
		.build();

	/**
	 * Item options that move items into or out of containers such as rune
	 * pouches, herb sacks and coal bags.
	 */
	private static final Set<String> CONTAINER_OPTIONS = ImmutableSet.of(
		"fill", "empty", "deposit", "withdraw", "check", "take-all", "deposit-all", "empty-all", "store", "remove",
		"take", "add", "use-all"
	);

	/**
	 * Item options that are never a teleport.
	 */
	private static final Set<String> NON_TRAVEL_ITEM_OPTIONS = ImmutableSet.of(
		"drop", "eat", "drink", "wield", "wear", "use", "examine", "destroy", "bury", "scatter", "check", "fill",
		"empty", "open", "read", "release", "remove", "clean", "craft", "light", "inspect", "deposit", "withdraw"
	);

	private final StatsService stats;
	private final TickState state;

	@Inject
	InteractionTracker(StatsService stats, TickState state)
	{
		this.stats = stats;
		this.state = state;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = Names.clean(event.getMenuOption());
		if (option.isEmpty())
		{
			return;
		}
		String target = Names.target(event.getMenuTarget());
		MenuAction action = event.getMenuAction();
		String optionLower = option.toLowerCase(Locale.ROOT);
		// Menu clicks happen between ticks; their effects show up next tick at the earliest
		int tick = state.tick + 1;

		stats.increment(Cat.INPUT, Keys.MENU_CLICKS);
		stats.increment(Cat.MENU_OPTIONS, option);

		if (action != MenuAction.WALK && action != MenuAction.CANCEL)
		{
			String actionTarget = target;
			if (action.name().startsWith("PLAYER_") || action == MenuAction.WIDGET_TARGET_ON_PLAYER)
			{
				actionTarget = "Player";
			}
			stats.increment(Cat.MENU_ACTIONS, actionTarget.isEmpty() ? option : option + ": " + actionTarget);
		}

		// Emotes
		if (WidgetUtil.componentToInterface(event.getParam1()) == InterfaceID.EMOTE
			&& (action == MenuAction.CC_OP || action.name().startsWith("WIDGET_")))
		{
			stats.increment(Cat.SOCIAL, Keys.EMOTES);
			stats.increment(Cat.EMOTES, target.isEmpty() ? option : target);
		}

		if (optionLower.equals("open") && (target.toLowerCase(Locale.ROOT).contains("door")
			|| target.toLowerCase(Locale.ROOT).contains("gate")))
		{
			stats.increment(Cat.MOVEMENT, Keys.DOORS);
		}

		if (optionLower.startsWith("climb"))
		{
			state.lastClimbTick = tick;
		}

		if (optionLower.equals("cast"))
		{
			onCast(action, target, tick);
			return;
		}

		switch (action)
		{
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
				if (optionLower.equals("take"))
				{
					state.pendingTakes.put(event.getId(), tick);
				}
				return;
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
				state.lastObjectOption = option;
				state.lastObjectTarget = target;
				state.lastObjectTick = tick;
				setTeleportIntent(option + ": " + target, tick);
				return;
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
				if (!optionLower.equals("attack"))
				{
					setTeleportIntent(option + ": " + target, tick);
				}
				return;
			default:
				break;
		}

		int itemId = event.getItemId();
		if (itemId > 0 && event.isItemOp())
		{
			state.pendingItemActions.put(itemId, new TickState.PendingAction(option, tick));
			if (CONTAINER_OPTIONS.contains(optionLower))
			{
				state.lastStorageItemActionTick = tick;
			}
			if (!NON_TRAVEL_ITEM_OPTIONS.contains(optionLower))
			{
				setTeleportIntent(option + ": " + Names.stripDose(target), tick);
			}
		}
	}

	private void onCast(MenuAction action, String target, int tick)
	{
		// Selecting a spell that still needs a target isn't a cast yet
		if (action == MenuAction.WIDGET_TARGET)
		{
			return;
		}
		String spell = target;
		String spellTarget = null;
		int arrow = target.indexOf(" -> ");
		if (arrow >= 0)
		{
			spell = target.substring(0, arrow).trim();
			spellTarget = target.substring(arrow + 4).trim();
		}
		if (spell.isEmpty())
		{
			return;
		}

		stats.increment(Cat.COMBAT_SPELLS, spell);
		state.lastSpell = spell;
		state.lastSpellTick = tick;

		String lower = spell.toLowerCase(Locale.ROOT);
		// The ledger matches the alched/enchanted item by name
		state.lastSpellTarget = spellTarget;
		if (lower.contains("teleport") || lower.contains("home") || lower.startsWith("tele "))
		{
			setTeleportIntent("Spell: " + spell, tick);
		}
	}

	private void setTeleportIntent(String intent, int tick)
	{
		state.teleportIntent = intent;
		state.teleportIntentTick = tick;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		int group = event.getGroupId();
		state.openGroups.add(group);
		String name = INTERFACE_NAMES.get(group);
		if (name != null)
		{
			stats.increment(Cat.INTERFACES, name);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		int group = event.getGroupId();
		state.openGroups.remove(group);
		state.groupClosedTick.put(group, state.tick);
	}
}
