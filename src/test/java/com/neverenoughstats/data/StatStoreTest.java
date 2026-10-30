package com.neverenoughstats.data;

import com.google.gson.Gson;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class StatStoreTest
{
	@Test
	public void testModes()
	{
		StatStore store = new StatStore(1);
		store.record(Cat.NPC_KILLED, "Goblin", 1);
		store.record(Cat.NPC_KILLED, "Goblin", 2);
		store.record(Cat.COMBAT_RECORDS, Keys.MAX_HIT_DEALT, 30);
		store.record(Cat.COMBAT_RECORDS, Keys.MAX_HIT_DEALT, 12);
		store.record(Cat.ACTIVITY_PB, "Zulrah", 9000);
		store.record(Cat.ACTIVITY_PB, "Zulrah", 8000);
		store.record(Cat.ACTIVITY_PB, "Zulrah", 8500);
		store.record(Cat.ECONOMY_LATEST, Keys.BANK_VALUE, 100);
		store.record(Cat.ECONOMY_LATEST, Keys.BANK_VALUE, 50);

		assertEquals(3, store.get(Period.ALL_TIME, Cat.NPC_KILLED, "Goblin"));
		assertEquals(30, store.get(Period.ALL_TIME, Cat.COMBAT_RECORDS, Keys.MAX_HIT_DEALT));
		assertEquals(8000, store.get(Period.ALL_TIME, Cat.ACTIVITY_PB, "Zulrah"));
		assertEquals(50, store.get(Period.ALL_TIME, Cat.ECONOMY_LATEST, Keys.BANK_VALUE));
		assertEquals(3, store.get(Period.TODAY, Cat.NPC_KILLED, "Goblin"));
		assertEquals(3, store.get(Period.WEEK, Cat.NPC_KILLED, "Goblin"));
		assertEquals(30, store.get(Period.WEEK, Cat.COMBAT_RECORDS, Keys.MAX_HIT_DEALT));
	}

	@Test
	public void testSessionReset()
	{
		StatStore store = new StatStore(1);
		store.record(Cat.INPUT, Keys.CLICKS, 10);
		store.newSession();
		store.record(Cat.INPUT, Keys.CLICKS, 5);
		assertEquals(5, store.get(Period.SESSION, Cat.INPUT, Keys.CLICKS));
		assertEquals(15, store.get(Period.ALL_TIME, Cat.INPUT, Keys.CLICKS));
		assertEquals(15, store.query(Period.TODAY).get(Cat.INPUT, Keys.CLICKS));
	}

	@Test
	public void testLoadMergesWithStatsRecordedWhileLoading()
	{
		StatStore old = new StatStore(7);
		old.finishLoad(null);
		old.record(Cat.NPC_KILLED, "Cow", 100);
		old.record(Cat.COMBAT_RECORDS, Keys.MAX_HIT_DEALT, 40);
		old.record(Cat.ECONOMY_LATEST, Keys.BANK_VALUE, 1000);
		old.setMeta(StatStore.META_FIRST_TRACKED, "123");

		// Round trip through JSON like the save file does
		Gson gson = new Gson();
		SaveFile file = gson.fromJson(gson.toJson(old.toSaveFile()), SaveFile.class);

		StatStore fresh = new StatStore(7);
		assertFalse(fresh.isLoaded());
		fresh.record(Cat.NPC_KILLED, "Cow", 1);
		fresh.record(Cat.COMBAT_RECORDS, Keys.MAX_HIT_DEALT, 20);
		fresh.record(Cat.ECONOMY_LATEST, Keys.BANK_VALUE, 2000);
		fresh.finishLoad(file);

		assertTrue(fresh.isLoaded());
		assertEquals(101, fresh.get(Period.ALL_TIME, Cat.NPC_KILLED, "Cow"));
		assertEquals(101, fresh.get(Period.TODAY, Cat.NPC_KILLED, "Cow"));
		assertEquals(1, fresh.get(Period.SESSION, Cat.NPC_KILLED, "Cow"));
		assertEquals(40, fresh.get(Period.ALL_TIME, Cat.COMBAT_RECORDS, Keys.MAX_HIT_DEALT));
		// The value seen most recently wins
		assertEquals(2000, fresh.get(Period.ALL_TIME, Cat.ECONOMY_LATEST, Keys.BANK_VALUE));
		assertEquals("123", fresh.getMeta(StatStore.META_FIRST_TRACKED));
	}

	@Test
	public void testPeriodsAndPruning()
	{
		String today = LocalDate.now().toString();
		String fiveDaysAgo = LocalDate.now().minusDays(5).toString();
		String twentyDaysAgo = LocalDate.now().minusDays(20).toString();
		String yearsAgo = LocalDate.now().minusDays(1000).toString();

		SaveFile file = new SaveFile();
		file.lifetime = block(Cat.NPC_KILLED, "Imp", 15);
		file.daily = new HashMap<>();
		file.daily.put(today, block(Cat.NPC_KILLED, "Imp", 1));
		file.daily.put(fiveDaysAgo, block(Cat.NPC_KILLED, "Imp", 2));
		file.daily.put(twentyDaysAgo, block(Cat.NPC_KILLED, "Imp", 4));
		file.daily.put(yearsAgo, block(Cat.NPC_KILLED, "Imp", 8));

		StatStore store = new StatStore(1);
		store.finishLoad(file);

		assertEquals(1, store.get(Period.TODAY, Cat.NPC_KILLED, "Imp"));
		assertEquals(3, store.get(Period.WEEK, Cat.NPC_KILLED, "Imp"));
		assertEquals(7, store.get(Period.MONTH, Cat.NPC_KILLED, "Imp"));
		assertEquals(7, store.get(Period.YEAR, Cat.NPC_KILLED, "Imp"));
		assertEquals(15, store.get(Period.ALL_TIME, Cat.NPC_KILLED, "Imp"));
		assertEquals(7, store.query(Period.MONTH).get(Cat.NPC_KILLED, "Imp"));

		store.pruneDays(10);
		assertEquals(2, store.getDaysTracked());
		assertEquals(15, store.get(Period.ALL_TIME, Cat.NPC_KILLED, "Imp"));
	}

	@Test
	public void testDirtyTracking()
	{
		StatStore store = new StatStore(1);
		store.finishLoad(null);
		store.markSaved(store.getRevision());
		assertFalse(store.isDirty());
		store.increment(Cat.MOVEMENT, Keys.TILES_WALKED);
		assertTrue(store.isDirty());
	}

	@Test
	public void testFormats()
	{
		assertEquals("1,234", StatFormat.NUMBER.format(1234));
		assertEquals("1m 0s", StatFormat.TICKS.format(100));
		assertEquals("1d 0h 0m", StatFormat.TICKS.format(144000));
		assertEquals("1:23.40", StatFormat.CENTISECONDS.format(8340));
		assertEquals(StatFormat.TICKS, Cat.format(Cat.SKILL_TIME, "Mining"));
		assertEquals(StatFormat.PIXELS, Cat.format(Cat.INPUT, Keys.MOUSE_MOVED));
		assertEquals("Burnt", Cat.info(Cat.skillList("Cooking", "Burnt")).getTitle());
	}

	private static Map<String, Map<String, Long>> block(String cat, String key, long value)
	{
		Map<String, Map<String, Long>> m = new HashMap<>();
		Map<String, Long> inner = new HashMap<>();
		inner.put(key, value);
		m.put(cat, inner);
		return m;
	}
}
