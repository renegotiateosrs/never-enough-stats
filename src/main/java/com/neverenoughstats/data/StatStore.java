package com.neverenoughstats.data;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import lombok.Getter;

/**
 * All stats for one account: lifetime totals, one bucket per day, and the
 * current session. Every method is thread safe; trackers write from the
 * client thread and input listeners/the panel read from the Swing thread.
 */
public class StatStore
{
	public static final String META_FIRST_TRACKED = "firstTracked";
	public static final String META_LAST_DEATH = "lastDeath";
	public static final String META_LAST_PLAYED_DAY = "lastPlayedDay";

	@Getter
	private final long accountHash;

	private final StatBlock lifetime = new StatBlock();
	private final TreeMap<String, StatBlock> daily = new TreeMap<>();
	private final Map<String, String> meta = new HashMap<>();
	private StatBlock session = new StatBlock();

	private String today = LocalDate.now().toString();
	private StatBlock todayBlock;

	/**
	 * True once the save file has been read (or found not to exist). Saving is
	 * refused before then so a slow load can never overwrite existing stats.
	 */
	@Getter
	private volatile boolean loaded;

	/**
	 * Bumped on every change so the panel knows when to redraw.
	 */
	@Getter
	private volatile long revision;

	@Getter
	private volatile long lastChangeRevisionSaved;

	public StatStore(long accountHash)
	{
		this.accountHash = accountHash;
	}

	public synchronized void record(String category, String key, long value)
	{
		if (key == null || key.isEmpty())
		{
			return;
		}
		if (value == 0 && Cat.mode(category) == StatMode.SUM)
		{
			return;
		}
		lifetime.record(category, key, value);
		today().record(category, key, value);
		session.record(category, key, value);
		revision++;
	}

	public void increment(String category, String key)
	{
		record(category, key, 1);
	}

	/**
	 * Call periodically so stats roll over into a new daily bucket at midnight.
	 */
	public synchronized void rollDay()
	{
		String now = LocalDate.now().toString();
		if (!now.equals(today))
		{
			today = now;
			todayBlock = null;
		}
	}

	public synchronized String getToday()
	{
		return today;
	}

	private StatBlock today()
	{
		if (todayBlock == null)
		{
			todayBlock = daily.computeIfAbsent(today, k -> new StatBlock());
		}
		return todayBlock;
	}

	public synchronized long get(Period period, String category, String key)
	{
		switch (period)
		{
			case SESSION:
				return session.get(category, key);
			case ALL_TIME:
				return lifetime.get(category, key);
			default:
				if (Cat.mode(category) != StatMode.SUM)
				{
					return query(period).get(category, key);
				}
				long total = 0;
				for (StatBlock b : daysFor(period).values())
				{
					total += b.get(category, key);
				}
				return total;
		}
	}

	/**
	 * @return a copy of the stats for the given period
	 */
	public synchronized StatBlock query(Period period)
	{
		switch (period)
		{
			case SESSION:
				return session.copy();
			case ALL_TIME:
				return lifetime.copy();
			default:
				StatBlock merged = new StatBlock();
				for (StatBlock b : daysFor(period).values())
				{
					merged.mergeFrom(b);
				}
				return merged;
		}
	}

	private Map<String, StatBlock> daysFor(Period period)
	{
		String cutoff = LocalDate.parse(today).minusDays(Math.max(0, period.getDays() - 1)).toString();
		return daily.tailMap(cutoff, true);
	}

	public synchronized int getDaysTracked()
	{
		return daily.size();
	}

	public synchronized void newSession()
	{
		session = new StatBlock();
		revision++;
	}

	public synchronized void resetSession()
	{
		newSession();
	}

	public synchronized void resetAll()
	{
		lifetime.raw().clear();
		daily.clear();
		todayBlock = null;
		session = new StatBlock();
		String first = String.valueOf(System.currentTimeMillis());
		meta.clear();
		meta.put(META_FIRST_TRACKED, first);
		revision++;
	}

	public synchronized String getMeta(String key)
	{
		return meta.get(key);
	}

	public synchronized void setMeta(String key, String value)
	{
		if (value == null)
		{
			meta.remove(key);
		}
		else
		{
			meta.put(key, value);
		}
		revision++;
	}

	/**
	 * Drop daily buckets older than {@code days} days. Lifetime totals are kept.
	 */
	public synchronized void pruneDays(int days)
	{
		if (days <= 0)
		{
			return;
		}
		String cutoff = LocalDate.parse(today).minusDays(days).toString();
		daily.headMap(cutoff, false).clear();
	}

	public synchronized SaveFile toSaveFile()
	{
		SaveFile f = new SaveFile();
		f.version = SaveFile.CURRENT_VERSION;
		f.lifetime = lifetime.copy().raw();
		f.daily = new TreeMap<>();
		for (Map.Entry<String, StatBlock> e : daily.entrySet())
		{
			f.daily.put(e.getKey(), e.getValue().copy().raw());
		}
		f.meta = new HashMap<>(meta);
		return f;
	}

	public synchronized void markSaved(long savedRevision)
	{
		lastChangeRevisionSaved = savedRevision;
	}

	public boolean isDirty()
	{
		return revision != lastChangeRevisionSaved;
	}

	/**
	 * Combine stats read from disk with anything recorded while the file was
	 * loading, then allow saving.
	 */
	public synchronized void finishLoad(SaveFile f)
	{
		if (f != null)
		{
			if (f.lifetime != null)
			{
				replace(lifetime, f.lifetime);
			}
			if (f.daily != null)
			{
				for (Map.Entry<String, Map<String, Map<String, Long>>> e : f.daily.entrySet())
				{
					StatBlock existing = daily.get(e.getKey());
					if (existing == null)
					{
						daily.put(e.getKey(), new StatBlock(e.getValue()));
					}
					else
					{
						replace(existing, e.getValue());
					}
				}
			}
			if (f.meta != null)
			{
				Map<String, String> merged = new HashMap<>(f.meta);
				merged.putAll(meta);
				meta.clear();
				meta.putAll(merged);
			}
		}
		meta.putIfAbsent(META_FIRST_TRACKED, String.valueOf(System.currentTimeMillis()));
		todayBlock = null;
		loaded = true;
		revision++;
	}

	/**
	 * Make {@code target} = loaded data with target's current contents merged on top.
	 */
	private static void replace(StatBlock target, Map<String, Map<String, Long>> loaded)
	{
		StatBlock merged = new StatBlock(loaded);
		merged.mergeFrom(target);
		target.raw().clear();
		target.raw().putAll(merged.raw());
	}
}
