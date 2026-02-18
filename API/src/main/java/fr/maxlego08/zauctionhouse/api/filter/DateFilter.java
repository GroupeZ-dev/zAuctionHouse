package fr.maxlego08.zauctionhouse.api.filter;

import java.util.Date;

public enum DateFilter {

    ALL(0),
    TODAY(1),
    THIS_WEEK(7),
    THIS_MONTH(30),
    THIS_YEAR(365);

    private final int days;

    DateFilter(int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }

    public boolean matches(Date date) {
        if (days == 0) return true;
        long cutoff = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
        return date.getTime() >= cutoff;
    }

    public DateFilter next() {
        DateFilter[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

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
