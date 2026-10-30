package com.neverenoughstats.tracker;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.client.eventbus.EventBus;

/**
 * Owns the trackers: registers them for events and runs their per-tick
 * processing in a fixed order (later steps use what earlier steps learned).
 */
@Singleton
public class TrackerManager
{
	private final EventBus eventBus;
	private final TickState state;
	@Getter
	private final InputTracker input;
	private final SessionTracker session;
	private final MovementTracker movement;
	private final SkillTracker skills;
	private final ItemLedger ledger;
	private final HealingTracker healing;
	private final CombatTracker combat;
	private final EconomyTracker economy;
	private final InteractionTracker interaction;
	private final ChatTracker chat;

	@Inject
	TrackerManager(EventBus eventBus, TickState state, InputTracker input, SessionTracker session,
		MovementTracker movement, SkillTracker skills, ItemLedger ledger, HealingTracker healing,
		CombatTracker combat, EconomyTracker economy, InteractionTracker interaction, ChatTracker chat)
	{
		this.eventBus = eventBus;
		this.state = state;
		this.input = input;
		this.session = session;
		this.movement = movement;
		this.skills = skills;
		this.ledger = ledger;
		this.healing = healing;
		this.combat = combat;
		this.economy = economy;
		this.interaction = interaction;
		this.chat = chat;
	}

	public void startUp()
	{
		eventBus.register(session);
		eventBus.register(skills);
		eventBus.register(ledger);
		eventBus.register(combat);
		eventBus.register(economy);
		eventBus.register(interaction);
		eventBus.register(chat);
		onLoggedOut();
	}

	public void shutDown()
	{
		eventBus.unregister(session);
		eventBus.unregister(skills);
		eventBus.unregister(ledger);
		eventBus.unregister(combat);
		eventBus.unregister(economy);
		eventBus.unregister(interaction);
		eventBus.unregister(chat);
	}

	/**
	 * Called every game tick while logged in.
	 */
	public void tick()
	{
		input.flush();
		session.tick();
		movement.tick();
		skills.tick();
		ledger.tick();
		healing.tick();
		combat.tick();
		economy.tick();
		state.endTick();
		state.tick++;
	}

	/**
	 * A fresh login (not a world hop).
	 */
	public void onNewSession()
	{
		session.startSession();
		// Ignore clicks made on the login screen
		input.discard();
	}

	/**
	 * After logging in or hopping, the server re-sends containers and
	 * positions; take new baselines instead of counting them as changes.
	 */
	public void onWorldEntered()
	{
		ledger.reset();
		movement.reset();
		healing.reset();
		combat.reset();
		state.reset();
	}

	public void onLoggedOut()
	{
		onWorldEntered();
		skills.reset();
		economy.reset();
		input.discard();
	}
}
