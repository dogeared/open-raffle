package org.openraffle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.ColumnDefault;

import java.util.Objects;
import java.util.Optional;

/**
 * A contiguous run of physical ticket numbers, both ends inclusive.
 *
 * <p>Rolls are numbered in different formats: plain numbers ({@code 1 – 100}) or numbers
 * with a dashed prefix ({@code 987-001 – 987-100}, {@code 4563-100-300 – 4563-100-1000}).
 * Everything up to the last dash is the prefix, the digits after it are the sequence. Both
 * ends of a range share the prefix, and ranges with different prefixes never overlap:
 * {@code 4563-100} and {@code 4564-100} are different tickets.
 */
@Embeddable
public class TicketRange {

    /** Everything before the last dash, or "" for plain numbers. Null only in rows that predate prefixes. */
    @Column(name = "prefix", length = 64)
    private String prefix = "";

    @Column(name = "ticket_start", nullable = false)
    private long start;

    @Column(name = "ticket_end", nullable = false)
    private long end;

    /** Width the sequence was written with ("001" → 3), so labels keep the roll's zero-padding; 0 = none. */
    @Column(name = "digits", nullable = false)
    @ColumnDefault("0")
    private int digits;

    protected TicketRange() {
        // JPA
    }

    public TicketRange(long start, long end) {
        this("", start, end, 0);
    }

    public TicketRange(String prefix, long start, long end, int digits) {
        this.prefix = prefix == null ? "" : prefix;
        this.start = start;
        this.end = end;
        this.digits = digits;
    }

    /** A single printed ticket number, parsed. */
    public record TicketNumber(String prefix, long sequence, int digits) {

        /** "987-001" → prefix "987", sequence 1, digits 3; "42" → prefix "", 42, 0. Empty if not a ticket number. */
        public static Optional<TicketNumber> parse(String text) {
            if (text == null) {
                return Optional.empty();
            }
            String trimmed = text.trim();
            int dash = trimmed.lastIndexOf('-');
            String prefix = dash < 0 ? "" : trimmed.substring(0, dash).trim();
            String sequence = dash < 0 ? trimmed : trimmed.substring(dash + 1).trim();
            if (sequence.isEmpty() || !sequence.chars().allMatch(Character::isDigit) || sequence.length() > 15
                    || (dash >= 0 && prefix.isEmpty())) {
                return Optional.empty();
            }
            int digits = sequence.length() > 1 && sequence.charAt(0) == '0' ? sequence.length() : 0;
            return Optional.of(new TicketNumber(prefix, Long.parseLong(sequence), digits));
        }

        public String format() {
            return TicketRange.format(prefix, sequence, digits);
        }
    }

    /**
     * The range from one printed ticket to another, e.g. {@code of("987-001", "987-100")}.
     *
     * @throws IllegalArgumentException if either is not a ticket number, the prefixes differ,
     *                                  or the last ticket comes before the first
     */
    public static TicketRange of(String first, String last) {
        TicketNumber from = TicketNumber.parse(first).orElseThrow(() -> new IllegalArgumentException(
                "\"" + first + "\" is not a ticket number: use digits, optionally with a dashed prefix like 987-001"));
        TicketNumber to = TicketNumber.parse(last).orElseThrow(() -> new IllegalArgumentException(
                "\"" + last + "\" is not a ticket number: use digits, optionally with a dashed prefix like 987-001"));
        if (!from.prefix().equals(to.prefix())) {
            throw new IllegalArgumentException("Both ends of a range must share the prefix: \""
                    + first + "\" and \"" + last + "\" don't");
        }
        if (to.sequence() < from.sequence()) {
            throw new IllegalArgumentException("Ticket range " + first + " – " + last
                    + ": the last ticket must not be before the first");
        }
        return new TicketRange(from.prefix(), from.sequence(), to.sequence(), Math.max(from.digits(), to.digits()));
    }

    public String getPrefix() {
        return prefix == null ? "" : prefix;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public int getDigits() {
        return digits;
    }

    public boolean isValid() {
        return start <= end;
    }

    public long getCount() {
        return end - start + 1;
    }

    public boolean contains(TicketNumber ticket) {
        return getPrefix().equals(ticket.prefix()) && ticket.sequence() >= start && ticket.sequence() <= end;
    }

    public boolean contains(long plainTicket) {
        return contains(new TicketNumber("", plainTicket, 0));
    }

    /** Overlaps only if the prefixes are the same and the sequences intersect. */
    public boolean overlaps(TicketRange other) {
        return getPrefix().equals(other.getPrefix()) && start <= other.end && other.start <= end;
    }

    public String getLabel() {
        return isSingle() ? getStartLabel() : getStartLabel() + " – " + getEndLabel();
    }

    /** The first ticket as printed, e.g. "987-001". */
    public String getStartLabel() {
        return format(start);
    }

    /** The last ticket as printed, e.g. "987-100". */
    public String getEndLabel() {
        return format(end);
    }

    /** A range of exactly one ticket. */
    public boolean isSingle() {
        return start == end;
    }

    private String format(long n) {
        return format(getPrefix(), n, digits);
    }

    static String format(String prefix, long n, int digits) {
        String sequence = digits > 0 ? String.format("%0" + digits + "d", n) : String.valueOf(n);
        return prefix.isEmpty() ? sequence : prefix + "-" + sequence;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TicketRange other && getPrefix().equals(other.getPrefix())
                && start == other.start && end == other.end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPrefix(), start, end);
    }

    @Override
    public String toString() {
        return getLabel();
    }
}
