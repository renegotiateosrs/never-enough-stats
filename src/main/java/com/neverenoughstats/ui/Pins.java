package com.neverenoughstats.ui;

import com.neverenoughstats.NeverEnoughStatsConfig;
import com.neverenoughstats.data.StatRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/**
 * Stats pinned to the overlay, saved in the plugin config.
 */
@Singleton
public class Pins
{
	private static final String LINE_SEPARATOR = "\n";

	private final ConfigManager configManager;
	private final NeverEnoughStatsConfig config;

	private String cachedRaw;
	private List<StatRef> cached = Collections.emptyList();

	@Inject
	Pins(ConfigManager configManager, NeverEnoughStatsConfig config)
	{
		this.configManager = configManager;
		this.config = config;
	}

	public synchronized List<StatRef> get()
	{
		String raw = config.pinnedStats();
		if (!raw.equals(cachedRaw))
		{
			List<StatRef> refs = new ArrayList<>();
			for (String line : raw.split(LINE_SEPARATOR))
			{
				StatRef ref = StatRef.decode(line);
				if (ref != null && !refs.contains(ref))
				{
					refs.add(ref);
				}
			}
			cachedRaw = raw;
			cached = Collections.unmodifiableList(refs);
		}
		return cached;
	}

	public synchronized boolean isPinned(StatRef ref)
	{
		return get().contains(ref);
	}

	public synchronized void toggle(StatRef ref)
	{
		List<StatRef> refs = new ArrayList<>(get());
		if (!refs.remove(ref))
		{
			refs.add(ref);
		}
		save(refs);
	}

	public synchronized void move(StatRef ref, int direction)
	{
		List<StatRef> refs = new ArrayList<>(get());
		int idx = refs.indexOf(ref);
		int target = idx + direction;
		if (idx < 0 || target < 0 || target >= refs.size())
		{
			return;
		}
		Collections.swap(refs, idx, target);
		save(refs);
	}

	private void save(List<StatRef> refs)
	{
		StringBuilder sb = new StringBuilder();
		for (StatRef r : refs)
		{
			if (sb.length() > 0)
			{
				sb.append(LINE_SEPARATOR);
			}
			sb.append(r.encode());
		}
		configManager.setConfiguration(NeverEnoughStatsConfig.GROUP, NeverEnoughStatsConfig.PINNED_KEY, sb.toString());
	}
}
