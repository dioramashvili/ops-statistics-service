package ge.bsb.ops.statistics.handler;

import ge.bsb.ops.statistics.model.Transaction;
import ge.bsb.ops.statistics.model.TransactionMessage;
import ge.bsb.ops.statistics.parser.MessageParser;
import ge.bsb.ops.statistics.repository.ProcessedMessageRepository;
import ge.bsb.ops.statistics.repository.StatisticsRepository;
import ge.bsb.ops.statistics.service.SegmentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MessageHandlerTest {

    private MessageParser messageParser;
    private SegmentResolver segmentResolver;
    private StatisticsRepository statisticsRepository;
    private ProcessedMessageRepository processedMessageRepository;
    private MessageHandler messageHandler;

    @BeforeEach
    void setUp() {
        messageParser = mock(MessageParser.class);
        segmentResolver = mock(SegmentResolver.class);
        statisticsRepository = mock(StatisticsRepository.class);
        processedMessageRepository = mock(ProcessedMessageRepository.class);

        messageHandler = new MessageHandler(
                messageParser,
                segmentResolver,
                statisticsRepository,
                processedMessageRepository,
                "transaction.create",
                "transaction.delete"
        );
    }

    @Test
    void shouldSkipDuplicateMessage() throws Exception {
        TransactionMessage message = new TransactionMessage(
                "msg-1",
                "transaction.create",
                "{}"
        );

        when(processedMessageRepository.tryMarkProcessed("msg-1")).thenReturn(false);

        messageHandler.handle(message);

        verifyNoInteractions(messageParser);
        verifyNoInteractions(segmentResolver);
        verifyNoInteractions(statisticsRepository);
    }

    @Test
    void shouldRejectMessageWithNullMessageId() {
        TransactionMessage message = new TransactionMessage(
                null,
                "transaction.create",
                "{}"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> messageHandler.handle(message)
        );

        verifyNoInteractions(processedMessageRepository);
        verifyNoInteractions(messageParser);
        verifyNoInteractions(segmentResolver);
        verifyNoInteractions(statisticsRepository);
    }

    @Test
    void shouldRejectMessageWithBlankMessageId() {
        TransactionMessage message = new TransactionMessage(
                "   ",
                "transaction.create",
                "{}"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> messageHandler.handle(message)
        );

        verifyNoInteractions(processedMessageRepository);
        verifyNoInteractions(messageParser);
        verifyNoInteractions(segmentResolver);
        verifyNoInteractions(statisticsRepository);
    }

    @Test
    void shouldIncrementStatisticsForCreateMessage() throws Exception {
        TransactionMessage message = new TransactionMessage(
                "msg-1",
                "transaction.create",
                "{}"
        );

        Transaction transaction = new Transaction();
        transaction.setDebitCustomerId(10);
        transaction.setCreditCustomerId(20);
        transaction.setChannelId(5);
        transaction.setDate(LocalDate.of(2026, 6, 11));

        when(processedMessageRepository.tryMarkProcessed("msg-1")).thenReturn(true);
        when(messageParser.parse("{}")).thenReturn(transaction);
        when(segmentResolver.resolve(10)).thenReturn("Mass");
        when(segmentResolver.resolve(20)).thenReturn("Premium");

        messageHandler.handle(message);

        verify(statisticsRepository).increment(
                "Mass",
                "Premium",
                5,
                LocalDate.of(2026, 6, 11)
        );

        verify(statisticsRepository, never()).decrement(any(), any(), anyInt(), any());
    }

    @Test
    void shouldDecrementStatisticsForDeleteMessage() throws Exception {
        TransactionMessage message = new TransactionMessage(
                "msg-1",
                "transaction.delete",
                "{}"
        );

        Transaction transaction = new Transaction();
        transaction.setDebitCustomerId(10);
        transaction.setCreditCustomerId(20);
        transaction.setChannelId(5);
        transaction.setDate(LocalDate.of(2026, 6, 11));

        when(processedMessageRepository.tryMarkProcessed("msg-1")).thenReturn(true);
        when(messageParser.parse("{}")).thenReturn(transaction);
        when(segmentResolver.resolve(10)).thenReturn("Mass");
        when(segmentResolver.resolve(20)).thenReturn("Premium");

        messageHandler.handle(message);

        verify(statisticsRepository).decrement(
                "Mass",
                "Premium",
                5,
                LocalDate.of(2026, 6, 11)
        );

        verify(statisticsRepository, never()).increment(any(), any(), anyInt(), any());
    }

    @Test
    void shouldSkipWhenBothSegmentsAreNotApplicable() throws Exception {
        TransactionMessage message = new TransactionMessage(
                "msg-1",
                "transaction.create",
                "{}"
        );

        Transaction transaction = new Transaction();
        transaction.setDebitCustomerId(0);
        transaction.setCreditCustomerId(0);
        transaction.setChannelId(5);
        transaction.setDate(LocalDate.of(2026, 6, 11));

        when(processedMessageRepository.tryMarkProcessed("msg-1")).thenReturn(true);
        when(messageParser.parse("{}")).thenReturn(transaction);
        when(segmentResolver.resolve(0)).thenReturn("N/A");

        messageHandler.handle(message);

        verify(statisticsRepository, never()).increment(any(), any(), anyInt(), any());
        verify(statisticsRepository, never()).decrement(any(), any(), anyInt(), any());
    }

    @Test
    void shouldThrowForUnsupportedRoutingKey() throws Exception {
        TransactionMessage message = new TransactionMessage(
                "msg-1",
                "transaction.craete",
                "{}"
        );

        Transaction transaction = new Transaction();
        transaction.setDebitCustomerId(10);
        transaction.setCreditCustomerId(20);
        transaction.setChannelId(5);
        transaction.setDate(LocalDate.of(2026, 6, 11));

        when(processedMessageRepository.tryMarkProcessed("msg-1")).thenReturn(true);
        when(messageParser.parse("{}")).thenReturn(transaction);
        when(segmentResolver.resolve(10)).thenReturn("Mass");
        when(segmentResolver.resolve(20)).thenReturn("Premium");

        assertThrows(
                IllegalArgumentException.class,
                () -> messageHandler.handle(message)
        );

        verify(processedMessageRepository).tryMarkProcessed("msg-1");
        verify(messageParser).parse("{}");


        verify(statisticsRepository, never()).increment(any(), any(), anyInt(), any());
        verify(statisticsRepository, never()).decrement(any(), any(), anyInt(), any());
    }
}