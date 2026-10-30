package com.neverenoughstats.data;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * How a raw stored value should be shown to the player.
 */
public enum StatFormat
{
	NUMBER,
	/**
	 * Value is a number of game ticks (0.6s each).
	 */
	TICKS,
	GP,
	PIXELS,
	DEGREES,
	PERCENT,
	/**
	 * Value is a duration in hundredths of a second.
	 */
	CENTISECONDS;

	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

	public String format(long value)
	{
		switch (this)
		{
			case TICKS:
				return formatDuration(value * 600L);
			case CENTISECONDS:
				return formatTimer(value);
			case GP:
				return formatNumber(value) + " gp";
			case PIXELS:
				return formatNumber(value) + " px";
			case DEGREES:
				return formatNumber(value) + "°";
			case PERCENT:
				return formatNumber(value) + "%";
			default:
				return formatNumber(value);
		}
	}

	public static String formatNumber(long value)
	{
		synchronized (NUMBER_FORMAT)
		{
			return NUMBER_FORMAT.format(value);
		}
	}

	private static String formatDuration(long millis)
	{
		long seconds = millis / 1000;
		long days = seconds / 86400;
		long hours = (seconds % 86400) / 3600;
		long minutes = (seconds % 3600) / 60;
		long secs = seconds % 60;

		if (days > 0)
		{
			return days + "d " + hours + "h " + minutes + "m";
		}
		if (hours > 0)
		{
			return hours + "h " + minutes + "m " + secs + "s";
		}
		if (minutes > 0)
		{
			return minutes + "m " + secs + "s";
		}
		return secs + "s";
	}

	private static String formatTimer(long centis)
	{
		long minutes = centis / 6000;
		long seconds = (centis % 6000) / 100;
		long hundredths = centis % 100;
		if (minutes >= 60)
		{
			return String.format("%d:%02d:%02d.%02d", minutes / 60, minutes % 60, seconds, hundredths);
		}
		return String.format("%d:%02d.%02d", minutes, seconds, hundredths);
	}
}
