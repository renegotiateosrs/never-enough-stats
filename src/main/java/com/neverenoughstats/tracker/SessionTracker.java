package com.neverenoughstats.tracker;

import com.neverenoughstats.NeverEnoughStatsConfig;
import com.neverenoughstats.data.Cat;
import com.neverenoughstats.data.Keys;
import com.neverenoughstats.data.StatStore;
import com.neverenoughstats.data.StatsService;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Time played, active vs AFK time, session length, days played and camera movement.
 */
@Singleton
public class SessionTracker
{
	private static final int YAW_UNITS = 2048;

	private final Client client;
	private final StatsService stats;
	private final TickState state;
	private final InputTracker input;
	private final NeverEnoughStatsConfig config;

	private long sessionTicks;
	private int lastYaw = -1;
	private double degreeRemainder;

	@Inject
	SessionTracker(Client client, StatsService stats, TickState state, InputTracker input, NeverEnoughStatsConfig config)
	{
		this.client = client;
		this.stats = stats;
		this.state = state;
		this.input = input;
		this.config = config;
	}

	void startSession()
	{
		sessionTicks = 0;
		lastYaw = -1;
	}

	void tick()
	{
		StatStore store = stats.getStore();
		if (store == null)
		{
			return;
		}
		store.rollDay();

		String today = store.getToday();
		if (store.isLoaded() && !today.equals(store.getMeta(StatStore.META_LAST_PLAYED_DAY)))
		{
			store.setMeta(StatStore.META_LAST_PLAYED_DAY, today);
			stats.increment(Cat.SESSION, Keys.DAYS_PLAYED);
		}

		stats.increment(Cat.TIME, Keys.TIME_LOGGED_IN);
		sessionTicks++;
		stats.record(Cat.TIME_RECORDS, Keys.LONGEST_SESSION, sessionTicks);

		long idleMillis = System.currentTimeMillis() - input.getLastInputMillis();
		boolean afk = idleMillis > config.afkSeconds() * 1000L;
		stats.increment(Cat.TIME, afk ? Keys.TIME_AFK : Keys.TIME_ACTIVE);

		if (state.inCombat())
		{
			stats.increment(Cat.TIME, Keys.TIME_COMBAT);
		}
		if (client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1)
		{
			stats.increment(Cat.TIME, Keys.TIME_WILDERNESS);
		}
		if (client.getVarpValue(VarPlayerID.OPTION_RUN) == 1)
		{
			stats.increment(Cat.TIME, Keys.TIME_RUNNING);
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (stats.getStore() == null || client.getGameState() != GameState.LOGGED_IN)
		{
			lastYaw = -1;
			return;
		}

		int yaw = client.getCameraYaw();
		if (lastYaw >= 0)
		{
			int delta = Math.abs(yaw - lastYaw) % YAW_UNITS;
			delta = Math.min(delta, YAW_UNITS - delta);
			degreeRemainder += delta * 360.0 / YAW_UNITS;
			long whole = (long) degreeRemainder;
			if (whole > 0)
			{
				stats.record(Cat.INPUT, Keys.CAMERA_ROTATED, whole);
				degreeRemainder -= whole;
			}
		}
		lastYaw = yaw;
	}
}
