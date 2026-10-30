package com.neverenoughstats.tracker;

import com.neverenoughstats.data.Cat;
import com.neverenoughstats.data.Keys;
import com.neverenoughstats.data.StatsService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Stats the game announces in the chatbox: kill counts, clue completions,
 * quests, diaries, pets, collection log slots, burnt food, pickpockets...
 * Also counts (but never stores) chat messages you send.
 */
@Singleton
public class ChatTracker
{
	private static final Pattern KILL_COUNT = Pattern.compile(
		"^Your (?:completed |subdued )?(.+?) (?:kill |chest |completion |lap |harvest |success |rescue |)count is:? ?([\\d,]+)");
	private static final Pattern DURATION = Pattern.compile(
		"(?:Fight|Lap|Challenge|Corrupted challenge|Delve level: \\d+) duration: ([\\d:.]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern CLUE = Pattern.compile(
		"^You have completed ([\\d,]+) (beginner|easy|medium|hard|elite|master) Treasure Trails?", Pattern.CASE_INSENSITIVE);
	private static final Pattern COLLECTION_LOG = Pattern.compile("^New item added to your collection log: (.+)$");
	private static final Pattern QUEST = Pattern.compile("^Congratulations, you've completed a quest: (.+?)\\.?$");
	private static final Pattern DIARY = Pattern.compile(
		"^Well done! You have completed an? (easy|medium|hard|elite) task in the (.+?) area");
	private static final Pattern COMBAT_TASK = Pattern.compile(
		"^Congratulations, you've completed an? (easy|medium|hard|elite|master|grandmaster) combat task", Pattern.CASE_INSENSITIVE);
	private static final Pattern VALUABLE_DROP = Pattern.compile("^(?:Valuable|Untradeable) drop: (?:\\d[\\d,]* x )?(.+?)(?: \\(.*\\))?$");
	private static final Pattern SLAYER_TASKS = Pattern.compile("^You've completed ([\\d,]+) tasks?");
	private static final Pattern RIFTS = Pattern.compile("^Amount of rifts you have closed: ([\\d,]+)");
	private static final Pattern BURN = Pattern.compile("^You (?:accidentally )?burn the (.+?)\\.$");
	private static final Pattern PICKPOCKET = Pattern.compile("^You pick (?:the )?(.+?)'s pocket");
	private static final Pattern PICKPOCKET_FAIL = Pattern.compile("^You fail to pick (?:the )?(.+?)'s pocket");
	private static final Pattern STALL = Pattern.compile("^You steal (?:an? |some )?(.+?) from (?:the )?(.+?)\\.?$");
	private static final Pattern GEM = Pattern.compile("^You just (?:found|mined) an? (.+?)!");
	private static final Pattern PLANT = Pattern.compile("^You plant (?:an? |some |\\d+ )?(.+?) in ");
	private static final Pattern COMPOST = Pattern.compile("^You treat the .+? with (.+?)\\.$");

	private final Client client;
	private final StatsService stats;
	private final TickState state;
	private final CombatTracker combat;

	private String lastKcName;
	private int lastKcTick = TickState.NEVER;

	@Inject
	ChatTracker(Client client, StatsService stats, TickState state, CombatTracker combat)
	{
		this.client = client;
		this.stats = stats;
		this.state = state;
		this.combat = combat;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (stats.getStore() == null)
		{
			return;
		}
		ChatMessageType type = event.getType();
		switch (type)
		{
			case PUBLICCHAT:
			case MODCHAT:
				if (isLocalPlayer(event.getName()))
				{
					stats.increment(Cat.SOCIAL, Keys.PUBLIC_MESSAGES);
				}
				return;
			case PRIVATECHATOUT:
				stats.increment(Cat.SOCIAL, Keys.PRIVATE_SENT);
				return;
			case PRIVATECHAT:
			case MODPRIVATECHAT:
				stats.increment(Cat.SOCIAL, Keys.PRIVATE_RECEIVED);
				return;
			case FRIENDSCHAT:
			case CLAN_CHAT:
			case CLAN_GUEST_CHAT:
			case CLAN_GIM_CHAT:
				if (isLocalPlayer(event.getName()))
				{
					stats.increment(Cat.SOCIAL, Keys.CLAN_MESSAGES);
				}
				return;
			case TRADE:
			case GAMEMESSAGE:
			case SPAM:
			case MESBOX:
			case ENGINE:
			case CLAN_MESSAGE:
			case CLAN_GIM_MESSAGE:
			case FRIENDSCHATNOTIFICATION:
				onGameMessage(Text.removeTags(event.getMessage()).replace(' ', ' ').trim());
				return;
			default:
				return;
		}
	}

	private boolean isLocalPlayer(String name)
	{
		Player local = client.getLocalPlayer();
		return local != null && local.getName() != null
			&& Text.sanitize(name).equalsIgnoreCase(Text.sanitize(local.getName()));
	}

	private void onGameMessage(String msg)
	{
		Matcher m;

		if ((m = KILL_COUNT.matcher(msg)).find())
		{
			String name = m.group(1);
			lastKcName = name;
			lastKcTick = state.tick;
			stats.record(Cat.ACTIVITY_KC, name, parse(m.group(2)));
			stats.increment(Cat.ACTIVITY_COMPLETED, name);
			return;
		}
		if ((m = DURATION.matcher(msg)).find())
		{
			long centis = parseDuration(m.group(1));
			if (centis > 0 && lastKcName != null && state.within(lastKcTick, 3))
			{
				stats.record(Cat.ACTIVITY_PB, lastKcName, centis);
			}
			return;
		}
		if ((m = CLUE.matcher(msg)).find())
		{
			String tier = Character.toUpperCase(m.group(2).charAt(0)) + m.group(2).substring(1).toLowerCase();
			stats.record(Cat.ACTIVITY_KC, "Clue scrolls (" + tier + ")", parse(m.group(1)));
			stats.increment(Cat.ACTIVITY_CLUES, tier);
			return;
		}
		if ((m = COLLECTION_LOG.matcher(msg)).find())
		{
			stats.increment(Cat.ACTIVITY_CLOG, m.group(1).trim());
			return;
		}
		if ((m = QUEST.matcher(msg)).find())
		{
			stats.increment(Cat.ACTIVITY_QUESTS, m.group(1).trim());
			return;
		}
		if ((m = DIARY.matcher(msg)).find())
		{
			stats.increment(Cat.ACTIVITY_DIARY, m.group(2) + " (" + m.group(1) + ")");
			return;
		}
		if ((m = COMBAT_TASK.matcher(msg)).find())
		{
			String tier = m.group(1);
			stats.increment(Cat.ACTIVITY_CA, Character.toUpperCase(tier.charAt(0)) + tier.substring(1).toLowerCase());
			return;
		}
		if ((m = VALUABLE_DROP.matcher(msg)).find())
		{
			stats.increment(Cat.ACTIVITY_DROPS, m.group(1).trim());
			return;
		}
		if ((m = SLAYER_TASKS.matcher(msg)).find())
		{
			stats.record(Cat.ACTIVITY_KC, "Slayer tasks", parse(m.group(1)));
			stats.increment(Cat.ACTIVITY_COMPLETED, "Slayer tasks");
			return;
		}
		if ((m = RIFTS.matcher(msg)).find())
		{
			stats.record(Cat.ACTIVITY_KC, "Guardians of the Rift (rifts closed)", parse(m.group(1)));
			stats.increment(Cat.ACTIVITY_COMPLETED, "Guardians of the Rift (rifts closed)");
			return;
		}
		if ((m = BURN.matcher(msg)).find())
		{
			stats.increment(Cat.skillList("Cooking", "Burnt"), capitalize(m.group(1)));
			return;
		}
		if ((m = PICKPOCKET_FAIL.matcher(msg)).find())
		{
			stats.increment(Cat.skillList("Thieving", "Failed pickpockets"), capitalize(m.group(1)));
			return;
		}
		if ((m = PICKPOCKET.matcher(msg)).find())
		{
			stats.increment(Cat.skillList("Thieving", "Pickpockets"), capitalize(m.group(1)));
			return;
		}
		if ((m = STALL.matcher(msg)).find())
		{
			stats.increment(Cat.skillList("Thieving", "Stolen from stalls"), capitalize(m.group(2)));
			return;
		}
		if ((m = GEM.matcher(msg)).find())
		{
			stats.increment(Cat.skillList("Mining", "Gems found"), capitalize(m.group(1)));
			return;
		}
		if ((m = PLANT.matcher(msg)).find())
		{
			stats.increment(Cat.skillList("Farming", "Planted"), capitalize(m.group(1)));
			return;
		}
		if ((m = COMPOST.matcher(msg)).find())
		{
			stats.increment(Cat.skillList("Farming", "Compost used"), capitalize(m.group(1)));
			return;
		}

		if (msg.startsWith("Oh dear, you are dead!"))
		{
			combat.onLocalDeath();
		}
		else if (msg.startsWith("A bird's nest falls out of the tree"))
		{
			stats.increment(Cat.skillList("Woodcutting", "Events"), "Bird's nests");
		}
		else if (msg.startsWith("Your pouch has decayed"))
		{
			stats.increment(Cat.skillList("Runecraft", "Events"), "Pouches decayed");
		}
		else if (msg.startsWith("You slip") || msg.startsWith("You lose your footing") || msg.startsWith("You fall"))
		{
			stats.increment(Cat.skillList("Agility", "Events"), "Obstacles failed");
		}
		else if (msg.contains("You've been stunned") || msg.contains("You have been stunned"))
		{
			stats.increment(Cat.COMBAT_STATUS, Keys.STUNNED);
		}
		else if (msg.startsWith("A Tele Block spell has been cast on you"))
		{
			stats.increment(Cat.COMBAT_STATUS, Keys.TELEBLOCKED);
		}
		else if (msg.startsWith("A superior foe has appeared"))
		{
			stats.increment(Cat.COMBAT, Keys.SUPERIORS);
		}
		else if (msg.equals("Accepted trade."))
		{
			stats.increment(Cat.SOCIAL, Keys.TRADES);
		}
		else if (msg.startsWith("You have a funny feeling like you're being followed"))
		{
			stats.increment(Cat.ACTIVITY_PETS, "Pets received");
		}
		else if (msg.startsWith("You feel something weird sneaking into your backpack"))
		{
			stats.increment(Cat.ACTIVITY_PETS, "Pets received (backpack)");
		}
		else if (msg.startsWith("You have a funny feeling like you would have been followed"))
		{
			stats.increment(Cat.ACTIVITY_PETS, "Duplicate pets");
		}
	}

	private static long parse(String number)
	{
		try
		{
			return Long.parseLong(number.replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	/**
	 * "1:23.40" -> 8340, "1:02:03" -> 372300
	 */
	static long parseDuration(String text)
	{
		try
		{
			if (text.endsWith("."))
			{
				text = text.substring(0, text.length() - 1);
			}
			String[] parts = text.split(":");
			double seconds = 0;
			for (String part : parts)
			{
				seconds = seconds * 60 + Double.parseDouble(part);
			}
			return Math.round(seconds * 100);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private static String capitalize(String s)
	{
		s = s.trim();
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
