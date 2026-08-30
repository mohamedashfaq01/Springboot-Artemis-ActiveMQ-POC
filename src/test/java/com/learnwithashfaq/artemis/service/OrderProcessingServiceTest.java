package com.learnwithashfaq.artemis.service;

import com.learnwithashfaq.artemis.dto.OrderEvent;
import com.learnwithashfaq.artemis.entity.OrderEntity;
import com.learnwithashfaq.artemis.exception.InventoryException;
import com.learnwithashfaq.artemis.exception.InvalidDataException;
import com.learnwithashfaq.artemis.exception.PaymentException;
import com.learnwithashfaq.artemis.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderProcessingServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderProcessingService orderProcessingService;

    @Captor
    private ArgumentCaptor<OrderEntity> orderEntityCaptor;

    @Test
    void processOrder_HappyPath_CompletesAllSteps() {
        // GIVEN
        OrderEvent orderEvent = OrderEvent.builder()
                .orderId("ORD-123")
                .productName("MacBook Pro")
                .quantity(1)
                .price(2000.0)
                .build();

        OrderEntity mockEntity = OrderEntity.builder().orderId("ORD-123").status("PROCESSING").build();

        when(orderRepository.findByOrderId("ORD-123"))
                .thenReturn(Optional.empty()) // First check: no existing order
                .thenReturn(Optional.of(mockEntity)); // Second check: update to COMPLETED

        // WHEN
        orderProcessingService.processOrder(orderEvent);

        // THEN
        verify(orderRepository, times(2)).save(orderEntityCaptor.capture());
        verify(orderRepository, times(2)).findByOrderId("ORD-123");
        
        // Assert updated entity status
        assertEquals("COMPLETED", mockEntity.getStatus());
    }

    @Test
    void processOrder_FatalError_ThrowsInvalidDataException() {
        // GIVEN
        OrderEvent orderEvent = OrderEvent.builder()
                .orderId("ORD-123")
                .productName("FATAL_ERROR")
                .build();

        // WHEN & THEN
        assertThrows(InvalidDataException.class, () -> orderProcessingService.processOrder(orderEvent));
        
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void processOrder_InventoryFailure_ThrowsInventoryException() {
        // GIVEN
        OrderEvent orderEvent = OrderEvent.builder()
                .orderId("ORD-123")
                .productName("FAIL_INVENTORY")
                .quantity(1)
                .build();

        when(orderRepository.findByOrderId("ORD-123")).thenReturn(Optional.empty());

        // WHEN & THEN
        assertThrows(InventoryException.class, () -> orderProcessingService.processOrder(orderEvent));
        
        verify(orderRepository).save(any(OrderEntity.class)); // Initial save occurs
        verify(orderRepository, times(1)).findByOrderId("ORD-123"); // Doesn't reach COMPLETED update
    }

    @Test
    void processOrder_PaymentFailure_ThrowsPaymentException() {
        // GIVEN
        OrderEvent orderEvent = OrderEvent.builder()
                .orderId("ORD-123")
                .productName("FAIL_PAYMENT")
                .price(100.0)
                .quantity(1)
                .build();

        when(orderRepository.findByOrderId("ORD-123")).thenReturn(Optional.empty());

        // WHEN & THEN
        assertThrows(PaymentException.class, () -> orderProcessingService.processOrder(orderEvent));
        
        verify(orderRepository).save(any(OrderEntity.class)); // Initial save occurs
        verify(orderRepository, times(1)).findByOrderId("ORD-123"); // Doesn't reach COMPLETED update
    }
}
