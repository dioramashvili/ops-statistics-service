package ge.bsb.ops.statistics.service;

import org.junit.Test;
import static org.junit.Assert.*;

public class SegmentResolverTest {

    private final SegmentResolver resolver = new SegmentResolver();

    @Test
    public void testResolveReturnsNAForZeroCustomerId() {
        String segment = resolver.resolve(0);
        assertEquals("N/A", segment);
    }
}