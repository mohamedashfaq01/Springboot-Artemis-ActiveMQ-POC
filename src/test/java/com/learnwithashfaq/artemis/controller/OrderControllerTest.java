package com.learnwithashfaq.artemis.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnwithashfaq.artemis.dto.OrderRequest;
import com.learnwithashfaq.artemis.dto.OrderResponse;
import com.learnwithashfaq.artemis.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void placeOrder_HappyPath_Returns202Accepted() throws Exception {
        // GIVEN
        OrderRequest request = OrderRequest.builder()
                .productName("iPhone 15 Pro")
                .quantity(2)
                .price(999.99)
                .customerName("Ashfaq")
                .build();

        OrderResponse mockResponse = OrderResponse.builder()
                .message("Order placed successfully! It will be processed in the background.")
                .orderId("ORD-12345678")
                .status("ACCEPTED")
                .timestamp(LocalDateTime.now())
                .build();

        when(orderService.placeOrder(any(OrderRequest.class))).thenReturn(mockResponse);

        // WHEN & THEN
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.orderId").value("ORD-12345678"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void placeOrder_ValidationFailure_Returns400() throws Exception {
        // GIVEN
        OrderRequest request = OrderRequest.builder()
                .productName("") // Invalid
                .quantity(2)
                .price(999.99)
                .customerName("Ashfaq")
                .build();

        when(orderService.placeOrder(any(OrderRequest.class)))
                .thenThrow(new IllegalArgumentException("Product name is required!"));

        // WHEN & THEN
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Product name is required!"));
    }
}
