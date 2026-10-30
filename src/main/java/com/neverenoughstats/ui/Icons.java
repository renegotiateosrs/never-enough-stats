package com.neverenoughstats.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Draws the sidebar icon (a small bar chart) so no image file is needed.
 */
public final class Icons
{
	private Icons()
	{
	}

	public static BufferedImage panelIcon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		g.setColor(new Color(220, 138, 0));
		g.fillRect(1, 9, 3, 6);
		g.setColor(new Color(240, 200, 60));
		g.fillRect(6, 5, 3, 10);
		g.setColor(new Color(110, 225, 110));
		g.fillRect(11, 1, 3, 14);

		g.setColor(new Color(0, 0, 0, 160));
		g.drawRect(1, 9, 2, 5);
		g.drawRect(6, 5, 2, 9);
		g.drawRect(11, 1, 2, 13);
		g.dispose();
		return img;
	}
}
