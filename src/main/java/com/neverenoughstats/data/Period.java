package com.neverenoughstats.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Period
{
	SESSION("This session", 0),
	TODAY("Today", 1),
	WEEK("Last 7 days", 7),
	MONTH("Last 30 days", 30),
	YEAR("Last 365 days", 365),
	ALL_TIME("All time", 0);

	private final String displayName;
	private final int days;

	@Override
	public String toString()
	{
		return displayName;
	}
}
