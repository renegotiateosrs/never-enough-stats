package com.neverenoughstats;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class NeverEnoughStatsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(NeverEnoughStatsPlugin.class);
		RuneLite.main(args);
	}
}
