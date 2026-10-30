# Never Enough Stats

Minecraft-style statistics for Old School RuneScape. Every kill, hit, heal, log, ore, click, step,
item and death on your account is counted and kept, split by day so you can look at this session,
today, the last 7/30/365 days or all time.

Open the bar-chart icon in the RuneLite sidebar to browse your stats. Right-click any stat to pin it
to an in-game overlay.

## Views

| View | What's in it |
| --- | --- |
| **General** | Time played (active vs AFK), logins, days played, longest session, clicks (left/right/middle), keys pressed, scrolls, mouse distance, camera rotation, tiles walked/run, teleports, ladders climbed, doors opened, run energy, chat messages sent, trades, emotes, GP from loot/alching/GE/shops, bank value, total level & XP |
| **Combat** | Damage dealt and taken, hits and zero-hits, highest hits, damage by style and by weapon, damage taken by type (hits, poison, venom, burn...), special attacks per weapon, spells cast, splashes, deaths by cause, times poisoned/venomed/frozen/teleblocked/stunned, prayer points drained, prayers activated and time active |
| **Healing & consumables** | HP healed by source (each food, each potion, Blood fury, Sanguinesti staff, blood spells, Guthan's, restore pools, natural regeneration), food eaten, potion doses, prayer and run energy restored by source |
| **Skills** | XP, levels, actions, time training, XP/hour for every skill, plus what you gathered/made and used in each skill (logs per tree type, ores, fish, bars, potions, runes, bones buried...), food burnt, pickpockets and failures per NPC, gems found, seeds planted, bird's nests and more |
| **Items** | For every item: gathered/made, picked up, dropped by monsters, used in skilling, eaten, drunk, dropped, opened, alched, fired/thrown, bought/sold in shops and on the GE, traded, lost on death, destroyed |
| **Creatures** | For every monster: killed, killed by, damage dealt, damage taken, loot value, loot drops |
| **Activities & bosses** | Kill counts, lap counts and chest counts reported by the game, completions while tracking, fastest times, clue scrolls by tier, chest/reward loot, notable drops, pets, collection log slots, quests, diary tasks, combat achievements |
| **World & menus** | Time spent per area, teleports by method, interfaces opened, emotes, every menu option you have clicked and every option + target combination ("Chop down: Oak tree") |

## How it works

* Stats are only collected from the moment the plugin is installed. Counts that the game announces
  (boss kill counts, clue counts, lap counts) are picked up from chat, so those show your real totals
  as soon as you see the message once.
* Some stats are worked out from clues because the game doesn't say why something happened. For
  example a heal is credited to Blood fury when your hitpoints go up on the same tick you hit
  something while wearing it. These are very good estimates but not perfect.
* Key presses are counted only as a total. Which keys you press is never recorded.
  Chat messages are counted, never stored.
* Everything is stored locally in the RuneLite plugin data folder, one file per account, with a daily
  backup. Nothing is ever sent anywhere. You can export everything as JSON, or one period as CSV,
  from the menu button in the panel.

## Settings

* **Count key presses** - on by default
* **AFK after** - seconds without input before time counts as AFK
* **Show pinned stats overlay** / **Overlay period**
* **Autosave every** - minutes between saves (stats are also saved on logout and when closing RuneLite)
* **Days of history** - how many days of per-day stats to keep (all-time totals are always kept)
