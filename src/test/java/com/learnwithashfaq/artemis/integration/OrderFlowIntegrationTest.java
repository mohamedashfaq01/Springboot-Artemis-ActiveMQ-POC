package com.learnwithashfaq.artemis.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnwithashfaq.artemis.dto.OrderRequest;
import com.learnwithashfaq.artemis.entity.OrderEntity;
import com.learnwithashfaq.artemis.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OrderFlowIntegrationTest — End-to-end integration tests.
 *
 * ═══════════════════════════════════════════════════════════════
 * WHAT IS AN INTEGRATION TEST?
 * ═══════════════════════════════════════════════════════════════
 *
 * Unlike unit tests (which test a single class in isolation),
 * integration tests boot the FULL Spring application context and test
 * the entire flow end-to-end:
 *
 *   HTTP Request → Controller → Service → JMS Producer → Queue
 *                → JMS Consumer → Processing Service → Database
 *
 * ═══════════════════════════════════════════════════════════════
 * KEY ANNOTATIONS EXPLAINED:
 * ═══════════════════════════════════════════════════════════════
 *
 * @SpringBootTest
 *   → Boots the complete application (Tomcat, Artemis, H2, all beans)
 *   → webEnvironment = RANDOM_PORT avoids port conflicts
 *
 * @AutoConfigureMockMvc
 *   → Gives us MockMvc to send HTTP requests without a real HTTP client
 *   → Much faster than using RestTemplate/WebClient
 *
 * @ActiveProfiles("test")
 *   → Uses application-test.yml instead of application.yml
 *   → Separate H2 instance, faster redelivery delays
 *
 * ═══════════════════════════════════════════════════════════════
 * WHY Awaitility?
 * ═══════════════════════════════════════════════════════════════
 *
 * JMS processing is ASYNCHRONOUS. When the REST API returns 202,
 * the consumer hasn't processed the message yet! We need to WAIT
 * for the consumer to finish before checking the database.
 *
 * Awaitility provides a clean "poll until condition is true" API:
 *   await().atMost(10, SECONDS).until(() -> orderExists(orderId));
 *
 * This is much better than Thread.sleep() because:
 *   - It returns as soon as the condition is met (fast!)
 *   - It has a timeout (won't hang forever)
 *   - It retries automatically with configurable polling intervals
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private org.springframework.jms.core.JmsTemplate jmsTemplate;

    /**
     * Clears the database before each test.
     * This ensures each test starts with a clean slate.
     */
    @BeforeEach
    void cleanUp() {
        orderRepository.deleteAll();
        
        // Drain the DEAD_LETTER_QUEUE to ensure a clean state between tests
        jmsTemplate.setReceiveTimeout(100);
        while (jmsTemplate.receive("DEAD_LETTER_QUEUE") != null) {
            // Draining old messages
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  TEST 1: HAPPY PATH — Full end-to-end success
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("✅ Happy Path — Order is processed and saved to DB with status COMPLETED")
    void testHappyPath_OrderProcessedAndSavedToDb() throws Exception {

        // GIVEN: A valid order request
        OrderRequest request = OrderRequest.builder()
                .productName("MacBook Pro")
                .quantity(1)
                .price(2499.99)
                .customerName("Ashfaq")
                .build();

        // WHEN: We send the order via REST API
        String responseBody = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        // Extract orderId from response
        String orderId = objectMapper.readTree(responseBody).get("orderId").asText();

        // THEN: Wait for JMS consumer to process the message and save to DB
        // The consumer is async, so we use Awaitility to poll until the order appears
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<OrderEntity> order = orderRepository.findByOrderId(orderId);
                    assertTrue(order.isPresent(), "Order should be saved in database");
                    assertEquals("COMPLETED", order.get().getStatus(),
                            "Order status should be COMPLETED after all 3 steps pass");
                    assertEquals("MacBook Pro", order.get().getProductName());
                    assertEquals(1, order.get().getQuantity());
                    assertEquals(2499.99, order.get().getTotalAmount(), 0.01);
                    assertEquals("Ashfaq", order.get().getCustomerName());
                });
    }

    // ═══════════════════════════════════════════════════════════════
    //  TEST 2: REST API returns 202 ACCEPTED
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("✅ REST API — Returns HTTP 202 ACCEPTED with order details")
    void testRestApi_Returns202Accepted() throws Exception {

        OrderRequest request = OrderRequest.builder()
                .productName("iPhone 15")
                .quantity(2)
                .price(999.99)
                .customerName("Ashfaq")
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.orderId", notNullValue()))
                .andExpect(jsonPath("$.status", is("ACCEPTED")))
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    // ═══════════════════════════════════════════════════════════════
    //  TEST 3-6: VALIDATION ERRORS — Bad input returns 400
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("❌ Validation — Missing product name returns HTTP 400")
    void testValidation_MissingProductName_Returns400() throws Exception {

        OrderRequest request = OrderRequest.builder()
                .productName("")   // ← BLANK — should fail validation
                .quantity(1)
                .price(100.00)
                .customerName("Ashfaq")
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Product name is required!")));
    }

    @Test
    @DisplayName("❌ Validation — Negative quantity returns HTTP 400")
    void testValidation_NegativeQuantity_Returns400() throws Exception {

        OrderRequest request = OrderRequest.builder()
                .productName("iPad")
                .quantity(-1)   // ← NEGATIVE — should fail validation
                .price(799.99)
                .customerName("Ashfaq")
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Quantity must be at least 1!")));
    }

    @Test
    @DisplayName("❌ Validation — Negative price returns HTTP 400")
    void testValidation_NegativePrice_Returns400() throws Exception {

        OrderRequest request = OrderRequest.builder()
                .productName("AirPods")
                .quantity(1)
                .price(-50.00)  // ← NEGATIVE — should fail validation
                .customerName("Ashfaq")
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Price must be greater than 0!")));
    }

    @Test
    @DisplayName("❌ Validation — Missing customer name returns HTTP 400")
    void testValidation_MissingCustomerName_Returns400() throws Exception {

        OrderRequest request = OrderRequest.builder()
                .productName("Apple Watch")
                .quantity(1)
                .price(399.99)
                .customerName("")   // ← BLANK — should fail validation
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Customer name is required!")));
    }

    // ═══════════════════════════════════════════════════════════════
    //  TEST 7: INVENTORY FAILURE — Order stays in PROCESSING/FAILED
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("❌ Inventory Failure — Order is NOT completed, retries exhaust, status stays PROCESSING")
    void testInventoryFailure_OrderNotCompleted() throws Exception {

        // GIVEN: An order with the magic product name that triggers inventory failure
        OrderRequest request = OrderRequest.builder()
                .productName("FAIL_INVENTORY")
                .quantity(1)
                .price(300.00)
                .customerName("Ashfaq")
                .build();

        // WHEN: We send the order
        String responseBody = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String orderId = objectMapper.readTree(responseBody).get("orderId").asText();

        // THEN: Wait for all retries to exhaust
        // The message should eventually be routed to the DEAD_LETTER_QUEUE
        jmsTemplate.setReceiveTimeout(5000);
        jakarta.jms.Message dlqMessage = jmsTemplate.receive("DEAD_LETTER_QUEUE");
        
        assertTrue(dlqMessage != null, "Message should be found in DEAD_LETTER_QUEUE after retries exhaust");
        assertEquals(3, dlqMessage.getIntProperty("RetryCount"), "Message should have been retried exactly 3 times");
        assertTrue(dlqMessage.getStringProperty("ErrorReason").contains("Max retries exhausted"), 
                "ErrorReason should indicate retries were exhausted");

        // Verify database state: Order was created but never completed
        Optional<OrderEntity> order = orderRepository.findByOrderId(orderId);
        assertTrue(order.isPresent(), "Order should be saved in database (Step 1 succeeds before Step 2 fails)");
        String status = order.get().getStatus();
        assertTrue("PROCESSING".equals(status) || "FAILED".equals(status),
                "Order status should be PROCESSING or FAILED, not COMPLETED. Actual: " + status);
    }

    // ═══════════════════════════════════════════════════════════════
    //  TEST 8: PAYMENT FAILURE — Order stays in PROCESSING/FAILED
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("❌ Payment Failure — Steps 1&2 pass but Step 3 fails, order NOT completed")
    void testPaymentFailure_OrderNotCompleted() throws Exception {

        // GIVEN: An order with the magic product name that triggers payment failure
        OrderRequest request = OrderRequest.builder()
                .productName("FAIL_PAYMENT")
                .quantity(1)
                .price(500.00)
                .customerName("Ashfaq")
                .build();

        // WHEN: We send the order
        String responseBody = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String orderId = objectMapper.readTree(responseBody).get("orderId").asText();

        // THEN: Wait for all retries to exhaust
        // The message should eventually be routed to the DEAD_LETTER_QUEUE
        jmsTemplate.setReceiveTimeout(5000);
        jakarta.jms.Message dlqMessage = jmsTemplate.receive("DEAD_LETTER_QUEUE");
        
        assertTrue(dlqMessage != null, "Message should be found in DEAD_LETTER_QUEUE after retries exhaust");
        assertEquals(3, dlqMessage.getIntProperty("RetryCount"), "Message should have been retried exactly 3 times");
        assertTrue(dlqMessage.getStringProperty("ErrorReason").contains("Max retries exhausted"), 
                "ErrorReason should indicate retries were exhausted");

        // Verify database state: Order was created but never completed
        Optional<OrderEntity> order = orderRepository.findByOrderId(orderId);
        assertTrue(order.isPresent(), "Order should be saved in database (Steps 1&2 succeed before Step 3 fails)");
        String status = order.get().getStatus();
        assertTrue("PROCESSING".equals(status) || "FAILED".equals(status),
                "Order status should be PROCESSING or FAILED, not COMPLETED. Actual: " + status);
    }

    // ═══════════════════════════════════════════════════════════════
    //  TEST 9: FATAL ERROR — Direct to DLQ without retries
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("❌ Fatal Error — Invalid data goes directly to DEAD_LETTER_QUEUE")
    void testInvalidData_GoesDirectlyToDLQ() throws Exception {

        // GIVEN: An order with the magic product name that triggers InvalidDataException
        OrderRequest request = OrderRequest.builder()
                .productName("FATAL_ERROR")
                .quantity(1)
                .price(100.00)
                .customerName("Ashfaq")
                .build();

        // WHEN: We send the order
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        // THEN: It should go DIRECTLY to the DEAD_LETTER_QUEUE
        // We set a receive timeout so it doesn't block forever if it fails
        jmsTemplate.setReceiveTimeout(5000); // 5 seconds
        jakarta.jms.Message dlqMessage = jmsTemplate.receive("DEAD_LETTER_QUEUE");
        
        assertTrue(dlqMessage != null, "Message should be found in DEAD_LETTER_QUEUE");
        assertEquals(0, dlqMessage.getIntProperty("RetryCount"), "Fatal errors should NOT be retried");
        assertEquals("InvalidDataException: Product name indicates a fatal unrecoverable error.", 
                dlqMessage.getStringProperty("ErrorReason"));
    }
}
