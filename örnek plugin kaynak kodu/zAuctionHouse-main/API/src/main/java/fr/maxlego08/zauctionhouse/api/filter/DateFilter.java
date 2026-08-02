package fr.maxlego08.zauctionhouse.api.filter;

import java.util.Date;

/**
 * Defines date range filters for filtering logs and transactions.
 * <p>
 * Each filter represents a time period relative to the current date.
 */
public enum DateFilter {

    /**
     * No date filtering - includes all entries.
     */
    ALL(0),

    /**
     * Entries from the last 24 hours.
     */
    TODAY(1),

    /**
     * Entries from the last 7 days.
     */
    THIS_WEEK(7),

    /**
     * Entries from the last 30 days.
     */
    THIS_MONTH(30),

    /**
     * Entries from the last 365 days.
     */
    THIS_YEAR(365);

    private final int days;

    DateFilter(int days) {
        this.days = days;
    }

    /**
     * Gets the number of days this filter spans.
     *
     * @return the number of days, or 0 for ALL
     */
    public int getDays() {
        return days;
    }

    /**
     * Checks if a date falls within this filter's range.
     *
     * @param date the date to check
     * @return true if the date is within the filter range
     */
    public boolean matches(Date date) {
        if (days == 0) return true;
        long cutoff = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
        return date.getTime() >= cutoff;
    }

    /**
     * Gets the next filter in the cycle.
     *
     * @return the next date filter
     */
    public DateFilter next() {
        DateFilter[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /**
     * Gets the display name for this filter.
     *
     * @return the human-readable display name
     */
    public String getDisplayName() {
        return switch (this) {
            case ALL -> "All Time";
            case TODAY -> "Today";
            case THIS_WEEK -> "This Week";
            case THIS_MONTH -> "This Month";
            case THIS_YEAR -> "This Year";
        };
    }
}
