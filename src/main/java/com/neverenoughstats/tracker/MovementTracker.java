package com.neverenoughstats.tracker;

import com.google.common.collect.ImmutableMap;
import com.neverenoughstats.data.Cat;
import com.neverenoughstats.data.Keys;
import com.neverenoughstats.data.Period;
import com.neverenoughstats.data.StatStore;
import com.neverenoughstats.data.StatsService;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;

/**
 * Tiles walked and run, teleports (and how), ladders climbed, and time spent in each area.
 */
@Singleton
public class MovementTracker
{
	/**
	 * Names for well-known map regions. Anything else is shown as "Region #id".
	 */
	private static final Map<Integer, String> AREA_NAMES = ImmutableMap.<Integer, String>builder()
		.put(12850, "Lumbridge")
		.put(12849, "Lumbridge Swamp")
		.put(12853, "Varrock")
		.put(12597, "Varrock (west)")
		.put(12598, "Grand Exchange")
		.put(12342, "Edgeville")
		.put(12341, "Barbarian Village")
		.put(12338, "Draynor Village")
		.put(12082, "Port Sarim")
		.put(11826, "Rimmington")
		.put(11828, "Falador")
		.put(11573, "Taverley")
		.put(11575, "Burthorpe")
		.put(13105, "Al Kharid")
		.put(13362, "PvP Arena")
		.put(13104, "Shantay Pass")
		.put(13358, "Pollnivneach")
		.put(13613, "Nardah")
		.put(10806, "Seers' Village")
		.put(11062, "Camelot")
		.put(11061, "Catherby")
		.put(10547, "East Ardougne")
		.put(10288, "Yanille")
		.put(10293, "Fishing Guild")
		.put(9781, "Tree Gnome Stronghold")
		.put(10553, "Rellekka")
		.put(11057, "Brimhaven")
		.put(11569, "Musa Point")
		.put(9776, "Castle Wars")
		.put(10536, "Pest Control")
		.put(13878, "Canifis")
		.put(13623, "Slayer Tower")
		.put(14131, "Barrows")
		.put(14646, "Port Phasmatys")
		.put(12344, "Ferox Enclave")
		.put(12895, "Prifddinas")
		.put(6461, "Wintertodt Camp")
		.put(6462, "Wintertodt")
		.build();

	private final Client client;
	private final StatsService stats;
	private final TickState state;

	private WorldPoint last;

	@Inject
	MovementTracker(Client client, StatsService stats, TickState state)
	{
		this.client = client;
		this.stats = stats;
		this.state = state;
	}

	void reset()
	{
		last = null;
	}

	void tick()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		WorldPoint now = player.getWorldLocation();
		if (now == null)
		{
			return;
		}

		trackArea(player);

		WorldPoint prev = last;
		last = now;
		if (prev == null)
		{
			return;
		}

		int dist = Math.max(Math.abs(now.getX() - prev.getX()), Math.abs(now.getY() - prev.getY()));
		boolean planeChanged = now.getPlane() != prev.getPlane();

		if (!planeChanged && dist == 1)
		{
			stats.increment(Cat.MOVEMENT, Keys.TILES_WALKED);
			return;
		}
		if (!planeChanged && dist == 2)
		{
			stats.record(Cat.MOVEMENT, Keys.TILES_RUN, 2);
			return;
		}
		if (dist == 0 && !planeChanged)
		{
			return;
		}

		// Respawning after death isn't travel
		if (state.within(state.deathTick, 15))
		{
			return;
		}

		if (planeChanged && dist < 15 && state.within(state.lastClimbTick, 20))
		{
			stats.increment(Cat.MOVEMENT, Keys.CLIMBS);
			return;
		}

		if (planeChanged || dist >= 15)
		{
			stats.increment(Cat.MOVEMENT, Keys.TELEPORTS);
			String method = state.teleportIntent != null && state.within(state.teleportIntentTick, 25)
				? state.teleportIntent
				: "Other / unknown";
			stats.increment(Cat.WORLD_TELEPORTS, method);
			state.teleportIntent = null;
		}
	}

	private void trackArea(Player player)
	{
		String area;
		if (client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1)
		{
			area = "Wilderness";
		}
		else
		{
			// Inside instances, use the region the instance was copied from
			WorldPoint wp = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
			if (wp == null)
			{
				return;
			}
			int region = wp.getRegionID();
			area = AREA_NAMES.getOrDefault(region, "Region #" + region);
		}

		StatStore store = stats.getStore();
		if (store != null && store.isLoaded() && store.get(Period.ALL_TIME, Cat.WORLD_AREA_TIME, area) == 0)
		{
			stats.increment(Cat.MOVEMENT, Keys.AREAS_VISITED);
		}
		stats.increment(Cat.WORLD_AREA_TIME, area);
	}
}
