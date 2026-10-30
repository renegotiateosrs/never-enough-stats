package com.neverenoughstats.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A set of stats: {@code category -> key -> value}. Not thread safe; the
 * owning {@link StatStore} synchronizes access.
 */
public class StatBlock
{
	private final Map<String, Map<String, Long>> data;

	public StatBlock()
	{
		this(new HashMap<>());
	}

	public StatBlock(Map<String, Map<String, Long>> data)
	{
		this.data = data != null ? data : new HashMap<>();
	}

	public void record(String category, String key, long value)
	{
		record(category, key, value, Cat.mode(category));
	}

	private void record(String category, String key, long value, StatMode mode)
	{
		Map<String, Long> cat = data.computeIfAbsent(category, k -> new HashMap<>());
		switch (mode)
		{
			case SUM:
				cat.merge(key, value, Long::sum);
				break;
			case MAX:
				cat.merge(key, value, Math::max);
				break;
			case MIN:
				if (value > 0)
				{
					cat.merge(key, value, (a, b) -> a <= 0 ? b : Math.min(a, b));
				}
				break;
			case LATEST:
				cat.put(key, value);
				break;
		}
	}

	public long get(String category, String key)
	{
		Map<String, Long> cat = data.get(category);
		if (cat == null)
		{
			return 0;
		}
		Long v = cat.get(key);
		return v != null ? v : 0;
	}

	public Map<String, Long> getCategory(String category)
	{
		Map<String, Long> cat = data.get(category);
		return cat != null ? Collections.unmodifiableMap(cat) : Collections.emptyMap();
	}

	public Iterable<String> categories()
	{
		return Collections.unmodifiableSet(data.keySet());
	}

	/**
	 * Merge another block into this one, using each category's mode.
	 * For {@link StatMode#LATEST} categories, {@code other} wins, so merge
	 * older blocks first.
	 */
	public void mergeFrom(StatBlock other)
	{
		for (Map.Entry<String, Map<String, Long>> cat : other.data.entrySet())
		{
			StatMode mode = Cat.mode(cat.getKey());
			for (Map.Entry<String, Long> e : cat.getValue().entrySet())
			{
				record(cat.getKey(), e.getKey(), e.getValue(), mode);
			}
		}
	}

	public StatBlock copy()
	{
		StatBlock copy = new StatBlock();
		for (Map.Entry<String, Map<String, Long>> cat : data.entrySet())
		{
			copy.data.put(cat.getKey(), new HashMap<>(cat.getValue()));
		}
		return copy;
	}

	public boolean isEmpty()
	{
		return data.isEmpty();
	}

	/**
	 * The backing map, for serialization.
	 */
	public Map<String, Map<String, Long>> raw()
	{
		return data;
	}
}
