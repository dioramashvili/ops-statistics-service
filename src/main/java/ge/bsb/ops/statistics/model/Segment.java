package ge.bsb.ops.statistics.model;

/**
 * Customer segment labels as resolved by {@code SegmentResolver} and stored in the
 * statistics table. {@code NOT_APPLICABLE} marks a side of the transaction that has
 * no owning client.
 */
public final class Segment {
    public static final String NOT_APPLICABLE = "N/A";
    public static final String COMPANY = "Company";
    public static final String UNIQUE = "Unique";
    public static final String PREMIUM = "Premium";
    public static final String MASS = "Mass";

    private Segment() {
    }
}
