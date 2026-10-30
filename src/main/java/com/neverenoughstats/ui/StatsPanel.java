package com.neverenoughstats.ui;

import com.neverenoughstats.data.Cat;
import com.neverenoughstats.data.Period;
import com.neverenoughstats.data.StatBlock;
import com.neverenoughstats.data.StatRef;
import com.neverenoughstats.data.StatStore;
import com.neverenoughstats.data.StatsService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.Filepath;

/**
 * The side panel: pick a view and a time period, search, and browse every stat.
 */
@Slf4j
public class StatsPanel extends PluginPanel
{
	private static final int REFRESH_MS = 2000;
	private static final Color ROW_COLOR = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color ROW_HOVER_COLOR = ColorScheme.DARKER_GRAY_HOVER_COLOR;
	private static final Color DETAIL_COLOR = new Color(24, 24, 24);

	private final StatsService stats;
	private final Pins pins;
	private final ScheduledExecutorService executor;

	private final JComboBox<ViewType> viewBox = new JComboBox<>(ViewType.values());
	private final JComboBox<Period> periodBox = new JComboBox<>(Period.values());
	private final JComboBox<SortOption> sortBox = new JComboBox<>();
	private final JPanel sortRow = new JPanel(new BorderLayout(4, 0));
	private final IconTextField search = new IconTextField();
	private final JLabel status = new JLabel();
	private final JPanel content = new ScrollablePanel();
	private final JScrollPane scroll;
	private final Timer timer;

	/**
	 * Sections the player has toggled away from their default state.
	 */
	private final Set<String> toggledSections = new HashSet<>();
	private final Set<String> expandedRows = new HashSet<>();
	private final Map<ViewType, String> sortByView = new EnumMap<>(ViewType.class);

	private StatStore lastStore;
	private long lastRevision = -1;
	private boolean updatingSortBox;

	@Inject
	StatsPanel(StatsService stats, Pins pins, ScheduledExecutorService executor)
	{
		super(false);
		this.stats = stats;
		this.pins = pins;
		this.executor = executor;

		sortByView.put(ViewType.ITEMS, ViewBuilder.ALL_COLUMNS);
		sortByView.put(ViewType.CREATURES, Cat.NPC_KILLED);

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		header.setBorder(new EmptyBorder(8, 8, 6, 8));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		JLabel title = new JLabel("Never Enough Stats");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		titleRow.add(title, BorderLayout.CENTER);
		JButton menuButton = new JButton("☰");
		menuButton.setToolTipText("Save, export or reset");
		menuButton.setFocusable(false);
		menuButton.addActionListener(e -> showMenu(menuButton));
		titleRow.add(menuButton, BorderLayout.EAST);
		header.add(titleRow);

		header.add(labeled("View", viewBox));
		header.add(labeled("Period", periodBox));
		periodBox.setSelectedItem(Period.ALL_TIME);

		JLabel sortLabel = new JLabel("Sort by");
		sortLabel.setPreferredSize(new Dimension(48, 0));
		sortRow.setOpaque(false);
		sortRow.add(sortLabel, BorderLayout.WEST);
		sortRow.add(sortBox, BorderLayout.CENTER);
		header.add(sortRow);

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(PANEL_WIDTH - 20, 30));
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				rebuild();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				rebuild();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				rebuild();
			}
		});
		search.addClearListener(this::rebuild);
		header.add(search);

		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(status);

		add(header, BorderLayout.NORTH);

		content.setLayout(new DynamicGridLayout(0, 1, 0, 6));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(new EmptyBorder(0, 8, 8, 8));
		scroll = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);

		viewBox.addActionListener(e ->
		{
			updateSortBox();
			rebuild();
		});
		periodBox.addActionListener(e -> rebuild());
		sortBox.addActionListener(e ->
		{
			SortOption opt = (SortOption) sortBox.getSelectedItem();
			if (!updatingSortBox && opt != null)
			{
				sortByView.put(selectedView(), opt.column);
				rebuild();
			}
		});
		updateSortBox();

		timer = new Timer(REFRESH_MS, e -> refreshIfChanged());
		timer.start();
		rebuild();
	}

	private static JPanel labeled(String text, Component component)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		JLabel label = new JLabel(text);
		label.setPreferredSize(new Dimension(48, 0));
		row.add(label, BorderLayout.WEST);
		row.add(component, BorderLayout.CENTER);
		return row;
	}

	public void shutDown()
	{
		timer.stop();
	}

	@Override
	public void onActivate()
	{
		rebuild();
	}

	private ViewType selectedView()
	{
		ViewType v = (ViewType) viewBox.getSelectedItem();
		return v != null ? v : ViewType.GENERAL;
	}

	private Period selectedPeriod()
	{
		Period p = (Period) periodBox.getSelectedItem();
		return p != null ? p : Period.ALL_TIME;
	}

	private void updateSortBox()
	{
		ViewType view = selectedView();
		List<String> columns;
		if (view == ViewType.ITEMS)
		{
			columns = ViewBuilder.ITEM_COLUMNS;
		}
		else if (view == ViewType.CREATURES)
		{
			columns = ViewBuilder.NPC_COLUMNS;
		}
		else
		{
			sortRow.setVisible(false);
			return;
		}

		updatingSortBox = true;
		sortBox.removeAllItems();
		if (view == ViewType.ITEMS)
		{
			sortBox.addItem(new SortOption(ViewBuilder.ALL_COLUMNS, "All activity"));
		}
		String selected = sortByView.get(view);
		for (String col : columns)
		{
			sortBox.addItem(new SortOption(col, Cat.info(col).getTitle()));
		}
		for (int i = 0; i < sortBox.getItemCount(); i++)
		{
			if (sortBox.getItemAt(i).column.equals(selected))
			{
				sortBox.setSelectedIndex(i);
			}
		}
		updatingSortBox = false;
		sortRow.setVisible(true);
	}

	private void refreshIfChanged()
	{
		if (!isShowing())
		{
			return;
		}
		StatStore store = stats.getStore();
		long revision = store != null ? store.getRevision() : -1;
		if (store != lastStore || revision != lastRevision)
		{
			rebuild();
		}
	}

	private void rebuild()
	{
		StatStore store = stats.getStore();
		lastStore = store;
		lastRevision = store != null ? store.getRevision() : -1;

		int scrollPos = scroll.getVerticalScrollBar().getValue();
		content.removeAll();

		if (store == null)
		{
			status.setText("Log in to start tracking.");
			content.revalidate();
			content.repaint();
			return;
		}
		status.setText(store.isLoaded() ? "" : "Loading saved stats…");

		StatBlock block = store.query(selectedPeriod());
		ViewType view = selectedView();
		List<Section> sections = new ViewBuilder(block, store).build(view, sortByView.get(ViewType.ITEMS),
			sortByView.get(ViewType.CREATURES));

		String query = search.getText().trim().toLowerCase(Locale.ROOT);
		int shown = 0;
		for (Section section : sections)
		{
			JPanel panel = renderSection(section, query);
			if (panel != null)
			{
				content.add(panel);
				shown++;
			}
		}
		if (shown == 0)
		{
			JLabel empty = new JLabel(query.isEmpty()
				? "<html>Nothing tracked here yet for this period. Go do something!</html>"
				: "<html>No stats match \"" + escape(query) + "\".</html>");
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			empty.setBorder(new EmptyBorder(8, 2, 8, 2));
			content.add(empty);
		}

		content.revalidate();
		content.repaint();
		SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(scrollPos));
	}

	private JPanel renderSection(Section section, String query)
	{
		boolean titleMatches = !query.isEmpty() && section.getTitle().toLowerCase(Locale.ROOT).contains(query);
		List<Section.Row> rows = new ArrayList<>();
		for (Section.Row row : section.getRows())
		{
			if (query.isEmpty() || titleMatches || matches(row, query))
			{
				rows.add(row);
			}
		}
		if (rows.isEmpty())
		{
			return null;
		}

		boolean searching = !query.isEmpty();
		boolean collapsed = !searching && (section.isCollapsedByDefault() ^ toggledSections.contains(section.getId()));

		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel header = new JLabel((collapsed ? "▸ " : "▾ ") + section.getTitle());
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(ColorScheme.BRAND_ORANGE);
		header.setBorder(new EmptyBorder(4, 2, 4, 2));
		header.setToolTipText(collapsed ? "Click to expand" : "Click to collapse");
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					if (!toggledSections.remove(section.getId()))
					{
						toggledSections.add(section.getId());
					}
					rebuild();
				}
			}
		});
		panel.add(header, BorderLayout.NORTH);

		if (!collapsed)
		{
			JPanel body = new JPanel(new DynamicGridLayout(0, 1, 0, 1));
			body.setBackground(ColorScheme.DARK_GRAY_COLOR);
			for (Section.Row row : rows)
			{
				String rowId = section.getId() + "/" + row.getLabel();
				boolean expanded = row.hasDetails() && (expandedRows.contains(rowId)
					|| (searching && !row.getLabel().toLowerCase(Locale.ROOT).contains(query) && !titleMatches));
				body.add(renderRow(row, rowId, expanded, false));
				if (expanded)
				{
					for (Section.Row detail : row.getDetails())
					{
						body.add(renderRow(detail, null, false, true));
					}
				}
			}
			panel.add(body, BorderLayout.CENTER);
		}
		return panel;
	}

	private static boolean matches(Section.Row row, String query)
	{
		if (row.getLabel().toLowerCase(Locale.ROOT).contains(query))
		{
			return true;
		}
		for (Section.Row d : row.getDetails())
		{
			if (d.getLabel().toLowerCase(Locale.ROOT).contains(query))
			{
				return true;
			}
		}
		return false;
	}

	private JPanel renderRow(Section.Row row, String rowId, boolean expanded, boolean detail)
	{
		Color bg = detail ? DETAIL_COLOR : ROW_COLOR;
		JPanel panel = new JPanel(new BorderLayout(6, 0));
		panel.setBackground(bg);
		panel.setBorder(new EmptyBorder(3, detail ? 18 : 6, 3, 6));

		String label = row.getLabel();
		if (row.hasDetails())
		{
			label = (expanded ? "▾ " : "▸ ") + label;
		}
		StatRef ref = row.getRef();
		boolean pinned = ref != null && pins.isPinned(ref);
		if (pinned)
		{
			label = "★ " + label;
		}

		JLabel name = new JLabel(label);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(detail ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.TEXT_COLOR);
		name.setToolTipText(row.getLabel());
		panel.add(name, BorderLayout.CENTER);

		JLabel value = new JLabel(row.getValue(), SwingConstants.RIGHT);
		value.setFont(FontManager.getRunescapeSmallFont());
		value.setForeground(pinned ? ColorScheme.BRAND_ORANGE : Color.WHITE);
		panel.add(value, BorderLayout.EAST);

		panel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				panel.setBackground(ROW_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				panel.setBackground(bg);
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					showRowMenu(row, panel, e);
				}
				else if (SwingUtilities.isLeftMouseButton(e) && rowId != null && row.hasDetails())
				{
					if (!expandedRows.remove(rowId))
					{
						expandedRows.add(rowId);
					}
					rebuild();
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					showRowMenu(row, panel, e);
				}
			}
		});
		return panel;
	}

	private void showRowMenu(Section.Row row, Component invoker, MouseEvent e)
	{
		JPopupMenu menu = new JPopupMenu();
		StatRef ref = row.getRef();
		if (ref != null)
		{
			boolean pinned = pins.isPinned(ref);
			JMenuItem pin = new JMenuItem(pinned ? "Unpin from overlay" : "Pin to overlay");
			pin.addActionListener(ev ->
			{
				pins.toggle(ref);
				rebuild();
			});
			menu.add(pin);
			if (pinned)
			{
				JMenuItem up = new JMenuItem("Move up in overlay");
				up.addActionListener(ev -> pins.move(ref, -1));
				menu.add(up);
				JMenuItem down = new JMenuItem("Move down in overlay");
				down.addActionListener(ev -> pins.move(ref, 1));
				menu.add(down);
			}
		}
		JMenuItem copy = new JMenuItem("Copy");
		copy.addActionListener(ev -> Toolkit.getDefaultToolkit().getSystemClipboard()
			.setContents(new StringSelection(row.getLabel() + ": " + row.getValue()), null));
		menu.add(copy);
		menu.show(invoker, e.getX(), e.getY());
	}

	private void showMenu(Component invoker)
	{
		JPopupMenu menu = new JPopupMenu();

		JMenuItem save = new JMenuItem("Save now");
		save.addActionListener(e -> stats.saveAsync());
		menu.add(save);

		JMenuItem json = new JMenuItem("Export everything (JSON)…");
		json.addActionListener(e -> exportJson());
		menu.add(json);

		JMenuItem csv = new JMenuItem("Export selected period (CSV)…");
		csv.addActionListener(e -> exportCsv());
		menu.add(csv);

		menu.addSeparator();

		JMenuItem resetSession = new JMenuItem("Reset this session");
		resetSession.addActionListener(e ->
		{
			StatStore store = stats.getStore();
			if (store != null && confirm("Reset the stats for this session? Lifetime and daily stats are kept."))
			{
				store.resetSession();
				rebuild();
			}
		});
		menu.add(resetSession);

		JMenuItem resetAll = new JMenuItem("Reset ALL stats for this account…");
		resetAll.addActionListener(e ->
		{
			StatStore store = stats.getStore();
			if (store != null && confirm("Permanently delete EVERY stat for this account, including all history?\n"
				+ "A backup from earlier today is kept in the plugin's data folder."))
			{
				store.resetAll();
				stats.saveAsync();
				rebuild();
			}
		});
		menu.add(resetAll);

		menu.show(invoker, 0, invoker.getHeight());
	}

	private boolean confirm(String message)
	{
		return JOptionPane.showConfirmDialog(this, message, "Never Enough Stats",
			JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
	}

	private void exportJson()
	{
		StatStore store = stats.getStore();
		if (store == null)
		{
			return;
		}
		Filepath target = chooseFile("never-enough-stats.json", "JSON files", "json");
		if (target == null)
		{
			return;
		}
		String json = stats.toJson(store.toSaveFile());
		write(target, json);
	}

	private void exportCsv()
	{
		StatStore store = stats.getStore();
		if (store == null)
		{
			return;
		}
		Period period = selectedPeriod();
		Filepath target = chooseFile("never-enough-stats-" + period.name().toLowerCase(Locale.ROOT) + ".csv",
			"CSV files", "csv");
		if (target == null)
		{
			return;
		}

		StatBlock block = store.query(period);
		Map<String, Map<String, Long>> sorted = new TreeMap<>(block.raw());
		StringBuilder sb = new StringBuilder("period,category,category_name,stat,value,formatted\n");
		for (Map.Entry<String, Map<String, Long>> cat : sorted.entrySet())
		{
			String catTitle = Cat.info(cat.getKey()).getTitle();
			for (Map.Entry<String, Long> e : new TreeMap<>(cat.getValue()).entrySet())
			{
				sb.append(csv(period.getDisplayName())).append(',')
					.append(csv(cat.getKey())).append(',')
					.append(csv(catTitle)).append(',')
					.append(csv(e.getKey())).append(',')
					.append(e.getValue()).append(',')
					.append(csv(Cat.format(cat.getKey(), e.getKey()).format(e.getValue())))
					.append('\n');
			}
		}
		write(target, sb.toString());
	}

	private Filepath chooseFile(String name, String description, String extension)
	{
		List<Filepath> files = new Filepath.Chooser()
			.setIsSave()
			.setDialogTitle("Export stats")
			.setFileName(name)
			.addExtensionFilter(description, extension)
			.setDefaultExtension(extension)
			.showDialog(this);
		return files == null || files.isEmpty() ? null : files.get(0);
	}

	private void write(Filepath target, String text)
	{
		executor.execute(() ->
		{
			try
			{
				target.write(text);
			}
			catch (Exception ex)
			{
				log.warn("Unable to export stats", ex);
				SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
					"Unable to export: " + ex.getMessage(), "Never Enough Stats", JOptionPane.ERROR_MESSAGE));
			}
		});
	}

	private static String csv(String s)
	{
		if (s == null)
		{
			return "";
		}
		if (s.contains(",") || s.contains("\"") || s.contains("\n"))
		{
			return "\"" + s.replace("\"", "\"\"") + "\"";
		}
		return s;
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static final class SortOption
	{
		private final String column;
		private final String title;

		SortOption(String column, String title)
		{
			this.column = column;
			this.title = title;
		}

		@Override
		public String toString()
		{
			return title;
		}
	}

	/**
	 * Keeps the content as wide as the scroll pane so long labels are
	 * truncated instead of scrolling sideways.
	 */
	private static final class ScrollablePanel extends JPanel implements Scrollable
	{
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
