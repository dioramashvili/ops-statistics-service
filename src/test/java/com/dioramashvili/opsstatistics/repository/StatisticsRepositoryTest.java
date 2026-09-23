package com.dioramashvili.opsstatistics.repository;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatisticsRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 11);

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final StatisticsRepository repository = new StatisticsRepository(jdbcTemplate);

    @Test
    void decrementSkipsQuietlyWhenNoPositiveRowMatched() {
        // op_count > 0 guard matched nothing: row missing or already zero
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(0);

        assertDoesNotThrow(() -> repository.decrement("Mass", "Premium", 5, DATE));
    }

    @Test
    void decrementPropagatesDataAccessException() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any()))
                .thenThrow(new QueryTimeoutException("database unavailable"));

        assertThrows(DataAccessException.class,
                () -> repository.decrement("Mass", "Premium", 5, DATE));
    }
}
