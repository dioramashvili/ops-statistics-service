package ge.bsb.ops.statistics.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedMessageRepository {
    private static final Logger log = LoggerFactory.getLogger(StatisticsRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public ProcessedMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryMarkProcessed(String messageId) {
        try {
            int rowsAffected = jdbcTemplate.update(
                    """
                            INSERT INTO basis.OPS_PROCESSED_MESSAGES_DAVIT (message_id)
                                VALUES (?);
                            """,
                    messageId
            );
            log.info("MessageId marked as processed successfully {}", messageId);
        } catch (DuplicateKeyException e) {
            return false;
        }
        return true;
    }
}
