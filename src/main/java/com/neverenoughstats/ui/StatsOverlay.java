package com.neverenoughstats.ui;

import com.neverenoughstats.NeverEnoughStatsConfig;
import com.neverenoughstats.data.Cat;
import com.neverenoughstats.data.Period;
import com.neverenoughstats.data.StatRef;
import com.neverenoughstats.data.StatStore;
import com.neverenoughstats.data.StatsService;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Shows the stats the player has pinned from the side panel.
 */
public class StatsOverlay extends OverlayPanel
{
	private final StatsService stats;
	private final Pins pins;
	private final NeverEnoughStatsConfig config;

	@Inject
	StatsOverlay(StatsService stats, Pins pins, NeverEnoughStatsConfig config)
	{
		this.stats = stats;
		this.pins = pins;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		StatStore store = stats.getStore();
		List<StatRef> refs = pins.get();
		if (!config.showOverlay() || store == null || refs.isEmpty())
		{
			return null;
		}

		Period period = config.overlayPeriod();
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Stats - " + period.getDisplayName())
			.build());
		for (StatRef ref : refs)
		{
			long value = store.get(period, ref.getCategory(), ref.getKey());
			panelComponent.getChildren().add(LineComponent.builder()
				.left(ref.label())
				.right(Cat.format(ref.getCategory(), ref.getKey()).format(value))
				.build());
		}
		return super.render(graphics);
	}
}
