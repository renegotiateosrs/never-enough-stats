package com.neverenoughstats.data;

/**
 * How new values are combined with the stored value, and how buckets
 * (days, sessions) are merged together.
 */
public enum StatMode
{
	/**
	 * Values are added together (kills, clicks, damage...).
	 */
	SUM,
	/**
	 * The highest value is kept (highest hit, kill counts reported by the game...).
	 */
	MAX,
	/**
	 * The lowest non-zero value is kept (personal best times).
	 */
	MIN,
	/**
	 * The most recent value is kept (bank value, total level...).
	 */
	LATEST
}
