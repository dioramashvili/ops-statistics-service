package ge.bsb.ops.statistics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class SegmentResolver {
    private static final Logger log = LoggerFactory.getLogger(SegmentResolver.class);

    private final Map<Integer, String> cache = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbcTemplate;

    public SegmentResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String resolve(int customerId) {
        if (customerId == 0) {
            return "N/A";
        }

        if (cache.containsKey(customerId)) {
            log.info("Cache hit for customerId: {}", customerId);
            return cache.get(customerId);
        }

        try {
            String segment;

            if (isJuridical(customerId)) {
                segment = "Company";
            } else if (hasAttribute(customerId, "UNIQUE_BANKER")) {
                segment = "Unique";
            } else if (hasAttribute(customerId, "PREMIUM_BANKER")) {
                segment = "Premium";
            } else {
                segment = "Mass";
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