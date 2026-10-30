package com.neverenoughstats.ui;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ViewType
{
	GENERAL("General"),
	COMBAT("Combat"),
	HEALING("Healing & consumables"),
	SKILLS("Skills"),
	ITEMS("Items"),
	CREATURES("Creatures"),
	ACTIVITIES("Activities & bosses"),
	WORLD("World & menus");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
