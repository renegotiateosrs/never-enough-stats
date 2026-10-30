package com.neverenoughstats.tracker;

import com.neverenoughstats.NeverEnoughStatsConfig;
import com.neverenoughstats.data.Cat;
import com.neverenoughstats.data.Keys;
import com.neverenoughstats.data.StatsService;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseWheelListener;

/**
 * Counts mouse clicks, mouse movement, scrolling and key presses. Only counts
 * are kept: which keys were pressed is never recorded.
 * <p>
 * Input arrives on the Swing thread, so counts are buffered here and written
 * to the store once per game tick.
 */
@Singleton
public class InputTracker extends MouseAdapter implements MouseWheelListener, KeyListener
{
	private final StatsService stats;
	private final NeverEnoughStatsConfig config;

	private final AtomicLong leftClicks = new AtomicLong();
	private final AtomicLong rightClicks = new AtomicLong();
	private final AtomicLong middleClicks = new AtomicLong();
	private final AtomicLong scrolls = new AtomicLong();
	private final AtomicLong keys = new AtomicLong();
	private final AtomicLong pixelsMoved = new AtomicLong();

	private final Set<Integer> heldKeys = new HashSet<>();
	private int lastX = -1;
	private int lastY = -1;
	private double pixelRemainder;

	@Getter
	private volatile long lastInputMillis = System.currentTimeMillis();

	@Inject
	InputTracker(StatsService stats, NeverEnoughStatsConfig config)
	{
		this.stats = stats;
		this.config = config;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		switch (e.getButton())
		{
			case MouseEvent.BUTTON1:
				leftClicks.incrementAndGet();
				break;
			case MouseEvent.BUTTON2:
				middleClicks.incrementAndGet();
				break;
			case MouseEvent.BUTTON3:
				rightClicks.incrementAndGet();
				break;
			default:
				break;
		}
		touch();
		return e;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent e)
	{
		moved(e.getX(), e.getY());
		return e;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		moved(e.getX(), e.getY());
		return e;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent e)
	{
		lastX = -1;
		lastY = -1;
		return e;
	}

	private void moved(int x, int y)
	{
		if (lastX >= 0)
		{
			pixelRemainder += Math.hypot(x - lastX, y - lastY);
			long whole = (long) pixelRemainder;
			if (whole > 0)
			{
				pixelsMoved.addAndGet(whole);
				pixelRemainder -= whole;
			}
		}
		lastX = x;
		lastY = y;
		touch();
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e)
	{
		scrolls.incrementAndGet();
		touch();
		return e;
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		// Holding a key repeats keyPressed; only count the first press
		synchronized (heldKeys)
		{
			if (!heldKeys.add(e.getKeyCode()))
			{
				return;
			}
		}
		keys.incrementAndGet();
		touch();
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		synchronized (heldKeys)
		{
			heldKeys.remove(e.getKeyCode());
		}
	}

	private void touch()
	{
		lastInputMillis = System.currentTimeMillis();
	}

	/**
	 * Write buffered counts to the store. Called once per game tick.
	 */
	void flush()
	{
		long left = leftClicks.getAndSet(0);
		long right = rightClicks.getAndSet(0);
		long middle = middleClicks.getAndSet(0);
		stats.record(Cat.INPUT, Keys.CLICKS, left + right + middle);
		stats.record(Cat.INPUT, Keys.LEFT_CLICKS, left);
		stats.record(Cat.INPUT, Keys.RIGHT_CLICKS, right);
		stats.record(Cat.INPUT, Keys.MIDDLE_CLICKS, middle);
		stats.record(Cat.INPUT, Keys.SCROLLS, scrolls.getAndSet(0));
		stats.record(Cat.INPUT, Keys.MOUSE_MOVED, pixelsMoved.getAndSet(0));
		long k = keys.getAndSet(0);
		if (config.countKeys())
		{
			stats.record(Cat.INPUT, Keys.KEYS_PRESSED, k);
		}
	}

	void discard()
	{
		leftClicks.set(0);
		rightClicks.set(0);
		middleClicks.set(0);
		scrolls.set(0);
		keys.set(0);
		pixelsMoved.set(0);
	}
}
