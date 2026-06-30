package ge.bsb.ops.statistics.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ge.bsb.ops.statistics.model.Segment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;


@Service
public class SegmentResolver {
    private static final Logger log = LoggerFactory.getLogger(SegmentResolver.class);

    // Segments change rarely and there is no change event to react to, so entries are
    // expired by age (expireAfterWrite) rather than kept forever: a segment change is
    // picked up within the TTL, and maximumSize bounds memory. Errors are never cached.
    private final Cache<Integer, String> cache;
    private final JdbcTemplate jdbcTemplate;

    public SegmentResolver(
            JdbcTemplate jdbcTemplate,
            @Value("${app.segment-cache.ttl-minutes:60}") long ttlMinutes,
            @Value("${app.segment-cache.max-size:50000}") long maxSize
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .maximumSize(maxSize)
                .build();
    }

    public String resolve(int customerId) {
        if (customerId == 0) {
            return Segment.NOT_APPLICABLE;
        }

        String cached = cache.getIfPresent(customerId);
        if (cached != null) {
            log.debug("Cache hit for customerId: {}", customerId);
            return cached;
        }

        try {
            String segment;

            if (isJuridical(customerId)) {
                segment = Segment.COMPANY;
            } else if (hasAttribute(customerId, "UNIQUE_BANKER")) {
                segment = Segment.UNIQUE;
            } else if (hasAttribute(customerId, "PREMIUM_BANKER")) {
                segment = Segment.PREMIUM;
            } else {
                segment = Segment.MASS;
            }

            log.info("Resolved segment for customerId: {} -> {}", customerId, segment);
            cache.put(customerId, segment);
            return segment;

        } catch (DataAccessException e) {
            // Do not swallow DB failures: returning "N/A" here would let a real
            // transaction be treated as "no client", committed and acked, and lost
            // forever. Propagate so the message is nacked and retried / dead-lettered.
            log.error("Failed to resolve segment for customerId: {} - propagating to abort message processing",
                    customerId, e);
            throw e;
        }
    }

    private boolean isJuridical(int customerId) {
        Integer result = jdbcTemplate.query(
                "SELECT IS_JURIDICAL FROM dbo.CLIENTS WHERE CLIENT_NO = ?",
                rs -> rs.next() ? rs.getInt("IS_JURIDICAL") : null,
                customerId
        );

        return result != null && result == 1;
    }

    private boolean hasAttribute(int customerId, String attribCode) {
        Integer result = jdbcTemplate.query(
                "SELECT 1 FROM dbo.CLIENT_ATTRIBUTES WHERE CLIENT_NO = ? AND ATTRIB_CODE = ?",
                rs -> rs.next() ? 1 : null,
                customerId,
                attribCode
        );

        return result != null;
    }
}