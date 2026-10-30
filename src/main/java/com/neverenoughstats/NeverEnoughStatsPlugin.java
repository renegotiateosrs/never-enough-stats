package com.neverenoughstats;

import com.google.inject.Provides;
import com.neverenoughstats.data.Cat;
import com.neverenoughstats.data.Keys;
import com.neverenoughstats.data.StatStore;
import com.neverenoughstats.data.StatsService;
import com.neverenoughstats.tracker.TrackerManager;
import com.neverenoughstats.ui.Icons;
import com.neverenoughstats.ui.StatsOverlay;
import com.neverenoughstats.ui.StatsPanel;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;

@Slf4j
@PluginDescriptor(
	name = "Never Enough Stats",
	description = "Minecraft-style statistics for everything you do on your account",
	tags = {"stats", "statistics", "tracker", "kills", "damage", "healing", "clicks", "minecraft", "counter"},
	internalName = "never-enough-stats"
)
public class NeverEnoughStatsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private NeverEnoughStatsConfig config;

	@Inject
	private StatsService stats;

	@Inject
	private TrackerManager trackers;

	@Inject
	private StatsOverlay overlay;

	private StatsPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> autosaveTask;

	private boolean loggedOut = true;
	private boolean hopping;

	@Override
	protected void startUp()
	{
		executor.execute(() ->
		{
			try
			{
				Filepath dir = getPluginDirectory();
				dir.createDirectories();
				stats.setDataDir(dir);
			}
			catch (IOException e)
			{
				log.warn("Unable to create stats directory; stats will not be saved", e);
			}
		});

		trackers.startUp();
		mouseManager.registerMouseListener(trackers.getInput());
		mouseManager.registerMouseWheelListener(trackers.getInput());
		keyManager.registerKeyListener(trackers.getInput());

		panel = injector.getInstance(StatsPanel.class);
		navButton = NavigationButton.builder()
			.tooltip("Never Enough Stats")
			.icon(Icons.panelIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);

		scheduleAutosave();

		loggedOut = true;
		hopping = false;
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				enterGame();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		if (autosaveTask != null)
		{
			autosaveTask.cancel(false);
			autosaveTask = null;
		}
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		panel.shutDown();
		panel = null;
		mouseManager.unregisterMouseListener(trackers.getInput());
		mouseManager.unregisterMouseWheelListener(trackers.getInput());
		keyManager.unregisterKeyListener(trackers.getInput());
		trackers.shutDown();
		// Saves on the executor; never blocks here
		stats.unload();
		loggedOut = true;
	}

	private void scheduleAutosave()
	{
		if (autosaveTask != null)
		{
			autosaveTask.cancel(false);
		}
		long minutes = Math.max(1, config.autosaveMinutes());
		autosaveTask = executor.scheduleWithFixedDelay(this::autosave, minutes, minutes, TimeUnit.MINUTES);
	}

	private void autosave()
	{
		StatStore store = stats.getStore();
		if (store != null)
		{
			store.pruneDays(config.historyDays());
		}
		stats.saveAsync();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
				if (!loggedOut)
				{
					stats.unload();
					trackers.onLoggedOut();
					loggedOut = true;
				}
				hopping = false;
				break;
			case HOPPING:
				hopping = true;
				stats.increment(Cat.SESSION, Keys.WORLD_HOPS);
				break;
			case LOGGED_IN:
				if (loggedOut)
				{
					enterGame();
				}
				else if (hopping)
				{
					trackers.onWorldEntered();
					hopping = false;
				}
				break;
			default:
				break;
		}
	}

	/**
	 * Start tracking for the logged in account.
	 *
	 * @return false if the account isn't known yet
	 */
	private boolean enterGame()
	{
		long hash = client.getAccountHash();
		if (hash == -1)
		{
			return false;
		}
		stats.switchAccount(hash);
		StatStore store = stats.getStore();
		if (store != null)
		{
			store.newSession();
		}
		stats.increment(Cat.SESSION, Keys.LOGINS);
		trackers.onNewSession();
		trackers.onWorldEntered();
		loggedOut = false;
		hopping = false;
		return true;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (loggedOut && !enterGame())
		{
			return;
		}
		trackers.tick();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (NeverEnoughStatsConfig.GROUP.equals(event.getGroup()) && "autosaveMinutes".equals(event.getKey()))
		{
			scheduleAutosave();
		}
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		event.waitFor(stats.saveAsync());
	}

	@Provides
	NeverEnoughStatsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NeverEnoughStatsConfig.class);
	}
}
