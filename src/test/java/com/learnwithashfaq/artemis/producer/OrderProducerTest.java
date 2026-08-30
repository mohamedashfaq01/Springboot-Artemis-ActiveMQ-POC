package com.learnwithashfaq.artemis.producer;

import com.learnwithashfaq.artemis.dto.OrderEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderProducerTest {

    @Mock
    private JmsTemplate jmsTemplate;

    @InjectMocks
    private OrderProducer orderProducer;

    @Test
    void sendOrder_CallsJmsTemplateWithCorrectQueueAndPayload() {
        // GIVEN
        OrderEvent orderEvent = OrderEvent.builder()
                .orderId("ORD-123")
                .productName("MacBook Pro")
                .quantity(1)
                .price(2000.0)
                .build();

        // WHEN
        orderProducer.sendOrder(orderEvent);

        // THEN
        verify(jmsTemplate).convertAndSend("order-queue", orderEvent);
    }
}
