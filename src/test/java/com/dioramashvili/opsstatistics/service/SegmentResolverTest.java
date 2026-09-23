package com.dioramashvili.opsstatistics.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SegmentResolverTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SegmentResolver segmentResolver = new SegmentResolver(jdbcTemplate, 60L, 50_000L);

    @Test
    void shouldReturnNotApplicableForCustomerIdZeroWithoutHittingDatabase() {
        assertEquals("N/A", segmentResolver.resolve(0));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldPropagateDataAccessExceptionInsteadOfReturningNotApplicable() {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), (Object[]) any()))
                .thenThrow(new QueryTimeoutException("database unavailable"));

        assertThrows(DataAccessException.class, () -> segmentResolver.resolve(123));
    }

    @Test
    void shouldServeRepeatedLookupsFromCacheWithoutRequeryingDatabase() {
        // isJuridical returns 1 -> "Company"; the second resolve must hit the cache
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), (Object[]) any()))
                .thenReturn(1);

        assertEquals("Company", segmentResolver.resolve(55));
        assertEquals("Company", segmentResolver.resolve(55));

        verify(jdbcTemplate, times(1))
                .query(anyString(), any(ResultSetExtractor.class), (Object[]) any());
    }
}
