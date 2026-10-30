package com.neverenoughstats.data;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Owns the active account's {@link StatStore} and reads/writes it to disk.
 * All disk access happens on the executor, never the client thread.
 */
@Slf4j
@Singleton
public class StatsService
{
	private final ScheduledExecutorService executor;
	private final Gson gson;

	/**
	 * The plugin's data directory. Set by the plugin once it has been created.
	 */
	private volatile Filepath dataDir;

	@Getter
	@Nullable
	private volatile StatStore store;

	@Inject
	StatsService(ScheduledExecutorService executor, Gson gson)
	{
		this.executor = executor;
		this.gson = gson;
	}

	public void setDataDir(Filepath dataDir)
	{
		this.dataDir = dataDir;
	}

	/**
	 * Record a stat for the active account. Does nothing while logged out.
	 */
	public void record(String category, String key, long value)
	{
		StatStore s = store;
		if (s != null)
		{
			s.record(category, key, value);
		}
	}

	public void increment(String category, String key)
	{
		record(category, key, 1);
	}

	public boolean isReady()
	{
		StatStore s = store;
		return s != null && s.isLoaded();
	}

	/**
	 * Switch to the given account, saving the previous one. The new store is
	 * usable immediately; data from disk is merged in once it has been read.
	 */
	public void switchAccount(long accountHash)
	{
		StatStore old = store;
		if (old != null && old.getAccountHash() == accountHash)
		{
			return;
		}
		if (old != null)
		{
			save(old);
		}

		StatStore s = new StatStore(accountHash);
		store = s;
		executor.execute(() -> load(s));
	}

	public void unload()
	{
		StatStore old = store;
		store = null;
		if (old != null)
		{
			save(old);
		}
	}

	public CompletableFuture<Void> saveAsync()
	{
		StatStore s = store;
		if (s == null)
		{
			return CompletableFuture.completedFuture(null);
		}
		return save(s);
	}

	private CompletableFuture<Void> save(StatStore s)
	{
		CompletableFuture<Void> future = new CompletableFuture<>();
		executor.execute(() ->
		{
			try
			{
				write(s);
			}
			catch (Exception e)
			{
				log.warn("Unable to save stats", e);
			}
			finally
			{
				future.complete(null);
			}
		});
		return future;
	}

	private Filepath fileFor(long accountHash) throws IOException
	{
		Filepath dir = dataDir;
		if (dir == null)
		{
			throw new IOException("data directory is not ready");
		}
		return dir.joinSegment(accountHash + ".json");
	}

	private void load(StatStore s)
	{
		SaveFile loadedFile = null;
		try
		{
			Filepath file = fileFor(s.getAccountHash());
			if (file.exists())
			{
				try (BufferedReader reader = file.openBufferedReader())
				{
					loadedFile = gson.fromJson(reader, SaveFile.class);
				}
				catch (JsonParseException | IllegalStateException e)
				{
					// Keep the unreadable file instead of overwriting it with an empty one
					Filepath bad = fileFor(s.getAccountHash()).getParent()
						.joinSegment(s.getAccountHash() + ".corrupt-" + System.currentTimeMillis() + ".json");
					log.warn("Stats file is unreadable, moving it to {}", bad.getFileName(), e);
					file.moveTo(bad);
				}
			}
		}
		catch (IOException e)
		{
			log.warn("Unable to load stats", e);
			// Don't mark as loaded: we would risk overwriting a file we couldn't read
			return;
		}
		s.finishLoad(loadedFile);
		log.debug("Loaded stats for account {}", s.getAccountHash());
	}

	private synchronized void write(StatStore s) throws IOException
	{
		if (!s.isLoaded() || !s.isDirty())
		{
			return;
		}

		long revision = s.getRevision();
		SaveFile snapshot = s.toSaveFile();
		String json = gson.toJson(snapshot);

		Filepath file = fileFor(s.getAccountHash());
		Filepath dir = file.getParent();

		// Keep one backup per day in case something goes wrong
		String today = LocalDate.now().toString();
		Filepath backup = dir.joinSegment(s.getAccountHash() + ".backup.json");
		if (file.exists() && (!backup.exists() || !today.equals(snapshotDay(backup))))
		{
			file.copyTo(backup, StandardCopyOption.REPLACE_EXISTING);
		}

		Filepath tmp = dir.joinSegment(s.getAccountHash() + ".json.tmp");
		tmp.write(json);
		try
		{
			tmp.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e)
		{
			tmp.moveTo(file, StandardCopyOption.REPLACE_EXISTING);
		}
		s.markSaved(revision);
		log.debug("Saved stats for account {}", s.getAccountHash());
	}

	private static String snapshotDay(Filepath file)
	{
		try
		{
			return LocalDate.ofInstant(file.getLastModifiedTime().toInstant(), ZoneId.systemDefault()).toString();
		}
		catch (IOException e)
		{
			return "";
		}
	}

	public String toJson(SaveFile f)
	{
		return gson.newBuilder().setPrettyPrinting().create().toJson(f);
	}
}
