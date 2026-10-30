package com.neverenoughstats.ui;

import com.neverenoughstats.data.StatRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * A titled, collapsible group of rows in the panel.
 */
@Getter
class Section
{
	private final String id;
	private final String title;
	private final boolean collapsedByDefault;
	private final List<Row> rows = new ArrayList<>();

	Section(String id, String title, boolean collapsedByDefault)
	{
		this.id = id;
		this.title = title;
		this.collapsedByDefault = collapsedByDefault;
	}

	Section add(Row row)
	{
		rows.add(row);
		return this;
	}

	/**
	 * One line: a label and a formatted value, optionally with detail lines
	 * that show when the row is clicked.
	 */
	@Getter
	static class Row
	{
		private final String label;
		private final String value;
		private final long sortValue;
		/**
		 * The stat behind this row, for pinning. Null for computed rows.
		 */
		private final StatRef ref;
		private final List<Row> details;

		Row(String label, String value, long sortValue, StatRef ref)
		{
			this(label, value, sortValue, ref, Collections.emptyList());
		}

		Row(String label, String value, long sortValue, StatRef ref, List<Row> details)
		{
			this.label = label;
			this.value = value;
			this.sortValue = sortValue;
			this.ref = ref;
			this.details = details;
		}

		boolean hasDetails()
		{
			return !details.isEmpty();
		}
	}
}
