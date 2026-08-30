package com.learnwithashfaq.artemis.service;

import com.learnwithashfaq.artemis.dto.OrderEvent;
import com.learnwithashfaq.artemis.dto.OrderRequest;
import com.learnwithashfaq.artemis.dto.OrderResponse;
import com.learnwithashfaq.artemis.producer.OrderProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderProducer orderProducer;

    @InjectMocks
    private OrderService orderService;

    @Captor
    private ArgumentCaptor<OrderEvent> orderEventCaptor;

    @Test
    void placeOrder_ValidRequest_SendsMessageAndReturnsAccepted() {
        // GIVEN
        OrderRequest request = OrderRequest.builder()
                .productName("MacBook Pro")
                .quantity(1)
                .price(2000.0)
                .customerName("Ashfaq")
                .build();

        // WHEN
        OrderResponse response = orderService.placeOrder(request);

        // THEN
        assertNotNull(response);
        assertEquals("ACCEPTED", response.getStatus());
        assertTrue(response.getOrderId().startsWith("ORD-"));
        
        verify(orderProducer).sendOrder(orderEventCaptor.capture());
        OrderEvent sentEvent = orderEventCaptor.getValue();
        
        assertEquals(response.getOrderId(), sentEvent.getOrderId());
        assertEquals("MacBook Pro", sentEvent.getProductName());
        assertEquals(2000.0, sentEvent.getTotalAmount());
        assertEquals("PENDING", sentEvent.getStatus());
    }

    @Test
    void placeOrder_MissingProductName_ThrowsException() {
        // GIVEN
        OrderRequest request = OrderRequest.builder()
                .productName("")
                .quantity(1)
                .price(2000.0)
                .customerName("Ashfaq")
                .build();

        // WHEN & THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.placeOrder(request);
        });

        assertEquals("Product name is required!", exception.getMessage());
        verifyNoInteractions(orderProducer);
    }
    
    @Test
    void placeOrder_NegativeQuantity_ThrowsException() {
        // GIVEN
        OrderRequest request = OrderRequest.builder()
                .productName("MacBook")
                .quantity(0)
                .price(2000.0)
                .customerName("Ashfaq")
                .build();

        // WHEN & THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.placeOrder(request);
        });

        assertEquals("Quantity must be at least 1!", exception.getMessage());
        verifyNoInteractions(orderProducer);
    }
}
