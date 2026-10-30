package com.neverenoughstats.data;

import java.util.Map;

/**
 * The on-disk format of one account's stats.
 */
public class SaveFile
{
	public static final int CURRENT_VERSION = 1;

	int version;
	/**
	 * Lifetime totals.
	 */
	Map<String, Map<String, Long>> lifetime;
	/**
	 * Per-day totals, keyed by ISO date (yyyy-MM-dd) in the player's time zone.
	 */
	Map<String, Map<String, Map<String, Long>>> daily;
	/**
	 * Bookkeeping that isn't a stat (first tracked date, GE offer progress...).
	 */
	Map<String, String> meta;
}
