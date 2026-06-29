package ge.bsb.ops.statistics.repository;

import ge.bsb.ops.statistics.model.DeadLetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeadLetterRepository {
    private static final Logger log = LoggerFactory.getLogger(DeadLetterRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public DeadLetterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(DeadLetter deadLetter) {
        jdbcTemplate.update(
                """
                        INSERT INTO basis.OPS_DEAD_LETTERS_DAVIT
                            (message_id, original_routing_key, death_reason, death_count, body)
                        VALUES (?, ?, ?, ?, ?);
                        """,
                deadLetter.messageId(),
                deadLetter.originalRoutingKey(),
                deadLetter.deathReason(),
                deadLetter.deathCount(),
                deadLetter.body()
        );
        log.warn("Persisted dead letter - messageId: {}, originalRoutingKey: {}, reason: {}, count: {}",
                deadLetter.messageId(), deadLetter.originalRoutingKey(), deadLetter.deathReason(),
                deadLetter.deathCount());
    }
}
