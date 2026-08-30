package com.learnwithashfaq.artemis.consumer;

import com.learnwithashfaq.artemis.dto.OrderEvent;
import com.learnwithashfaq.artemis.exception.InvalidDataException;
import com.learnwithashfaq.artemis.exception.PaymentException;
import com.learnwithashfaq.artemis.service.OrderProcessingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderConsumerTest {

    @Mock
    private OrderProcessingService orderProcessingService;

    @Mock
    private JmsTemplate jmsTemplate;

    @InjectMocks
    private OrderConsumer orderConsumer;

    @Test
    void receiveOrder_HappyPath_ProcessesSuccessfully() {
        // GIVEN
        OrderEvent orderEvent = OrderEvent.builder().orderId("ORD-123").build();

        // WHEN
        orderConsumer.receiveOrder(orderEvent, 0);

        // THEN
        verify(orderProcessingService).processOrder(orderEvent);
        verifyNoInteractions(jmsTemplate); // No errors, so no DLQ or Error Queue
    }

    @Test
    void receiveOrder_FatalError_MovesToDLQ() {
        // GIVEN
        OrderEvent orderEvent = OrderEvent.builder().orderId("ORD-123").build();
        doThrow(new InvalidDataException("Corrupted")).when(orderProcessingService).processOrder(orderEvent);

        // WHEN
        orderConsumer.receiveOrder(orderEvent, 0);

        // THEN
        verify(jmsTemplate).convertAndSend(eq("DEAD_LETTER_QUEUE"), eq(orderEvent), any(MessagePostProcessor.class));
    }

    @Test
    void receiveOrder_RecoverableErrorUnderMaxRetries_MovesToErrorQueue() {
        // GIVEN
        OrderEvent orderEvent = OrderEvent.builder().orderId("ORD-123").build();
        doThrow(new PaymentException("Funds low")).when(orderProcessingService).processOrder(orderEvent);

        // WHEN (retry 1 of 3)
        orderConsumer.receiveOrder(orderEvent, 1);

        // THEN
        verify(jmsTemplate).convertAndSend(eq("ERROR_QUEUE"), eq(orderEvent), any(MessagePostProcessor.class));
    }

    @Test
    void receiveOrder_RecoverableErrorAtMaxRetries_MovesToDLQ() {
        // GIVEN
        OrderEvent orderEvent = OrderEvent.builder().orderId("ORD-123").build();
        doThrow(new PaymentException("Funds low")).when(orderProcessingService).processOrder(orderEvent);

        // WHEN (retry 3 of 3)
        orderConsumer.receiveOrder(orderEvent, 3);

        // THEN
        verify(jmsTemplate).convertAndSend(eq("DEAD_LETTER_QUEUE"), eq(orderEvent), any(MessagePostProcessor.class));
    }
}
