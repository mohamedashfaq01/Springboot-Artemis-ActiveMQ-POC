# 🧪 Testing Guide

This project has **two layers of testing** that work together to guarantee correctness at every level: from individual class behavior to the entire end-to-end async flow.

---

## Overview

| Layer | Class | Type | Tool | Spring Context? |
|---|---|---|---|---|
| Integration | `OrderFlowIntegrationTest` | E2E / Integration | `@SpringBootTest` | ✅ Full context |
| Controller | `OrderControllerTest` | Unit | `@WebMvcTest` | ✅ Web layer only |
| Service | `OrderServiceTest` | Unit | `@ExtendWith(MockitoExtension)` | ❌ No context |
| Processing | `OrderProcessingServiceTest` | Unit | `@ExtendWith(MockitoExtension)` | ❌ No context |
| Producer | `OrderProducerTest` | Unit | `@ExtendWith(MockitoExtension)` | ❌ No context |
| Consumer | `OrderConsumerTest` | Unit | `@ExtendWith(MockitoExtension)` | ❌ No context |

**Total: 24 tests, 0 failures.**

---

## How to Run

```bash
# Run all tests
.\gradlew.bat test

# Run a specific test class
.\gradlew.bat test --tests "com.learnwithashfaq.artemis.integration.OrderFlowIntegrationTest"
.\gradlew.bat test --tests "com.learnwithashfaq.artemis.service.OrderServiceTest"

# View the HTML test report
# Open: build/reports/tests/test/index.html
```

---

## Layer 1: Integration Tests (`OrderFlowIntegrationTest.java`)

**What it tests:** The entire application from the HTTP request, through JMS, to the database — using a real embedded Artemis broker and real H2 database.

**How it works:**
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` — starts the full Spring Boot context.
- `MockMvc` — fires real HTTP requests.
- `Awaitility` — polls the database to wait for async processing to complete (instead of `Thread.sleep()`).

### Test Cases

| # | Test Method | Scenario Verified |
|---|---|---|
| 1 | `testHappyPath_OrderProcessedAndSavedToDb` | Full happy path — order reaches `COMPLETED` status in DB |
| 2 | `testRestApi_Returns202Accepted` | HTTP response is exactly `202 ACCEPTED` |
| 3 | `testValidation_MissingProductName_Returns400` | Missing product name returns `400 Bad Request` |
| 4 | `testValidation_NegativeQuantity_Returns400` | Quantity ≤ 0 returns `400 Bad Request` |
| 5 | `testValidation_NegativePrice_Returns400` | Price ≤ 0 returns `400 Bad Request` |
| 6 | `testValidation_MissingCustomerName_Returns400` | Missing customer name returns `400 Bad Request` |
| 7 | `testInventoryFailure_OrderNotCompleted` | `FAIL_INVENTORY` → order never reaches `COMPLETED` |
| 8 | `testPaymentFailure_OrderNotCompleted` | `FAIL_PAYMENT` → retries exhausted → message in DLQ |
| 9 | `testInvalidData_GoesDirectlyToDLQ` | `FATAL_ERROR` → instant DLQ, no retries |

---

## Layer 2: Unit Tests

Unit tests are **fast** (no Spring context, no DB, no JMS broker) and test each class in complete isolation using Mockito mocks.

---

### `OrderControllerTest.java` — `@WebMvcTest`

**What it tests:** The REST layer only — routing, response status codes, and request/response mapping.

**How it works:**
- `@WebMvcTest(OrderController.class)` — loads only the web layer (no JMS, no DB).
- `@MockBean OrderService` — the service is completely mocked.
- `MockMvc` — fires HTTP requests against the controller.

| Test | What It Verifies |
|---|---|
| `placeOrder_HappyPath_Returns202Accepted` | A valid request returns `202 ACCEPTED` with `orderId` and `status: ACCEPTED` |
| `placeOrder_ValidationFailure_Returns400` | When service throws `IllegalArgumentException`, controller returns `400 Bad Request` |

**Key JUnit 5 / Spring Testing Concepts Demonstrated:**
- `@WebMvcTest` — a "slice test" that loads only the MVC layer, not the full context.
- `@MockBean` — registers a Mockito mock into the Spring application context.
- `mockMvc.perform(post(...))` — simulates HTTP request without a running server.
- `jsonPath("$.orderId")` — asserts on JSON response body fields.

---

### `OrderServiceTest.java` — Pure Mockito

**What it tests:** The business logic in `OrderService` — validation, `orderId` generation, event construction, and message dispatch.

| Test | What It Verifies |
|---|---|
| `placeOrder_ValidRequest_SendsMessageAndReturnsAccepted` | Sends event to producer with correct fields; response has `ACCEPTED` status and `ORD-` prefix |
| `placeOrder_MissingProductName_ThrowsException` | Blank product name throws `IllegalArgumentException` before producer is called |
| `placeOrder_NegativeQuantity_ThrowsException` | Quantity = 0 throws `IllegalArgumentException` before producer is called |

**Key JUnit 5 / Mockito Concepts Demonstrated:**
- `@ExtendWith(MockitoExtension.class)` — enables Mockito annotations without Spring.
- `@Mock` — creates a mock of a dependency.
- `@InjectMocks` — injects all `@Mock` fields into the class under test.
- `@Captor` + `ArgumentCaptor` — captures the argument passed to `verify()` for deep inspection.
- `verifyNoInteractions(mock)` — asserts that a mock was never called at all.

---

### `OrderProcessingServiceTest.java` — Pure Mockito

**What it tests:** The 3-step processing pipeline, idempotency check, and exception behavior.

| Test | What It Verifies |
|---|---|
| `processOrder_HappyPath_CompletesAllSteps` | Saves entity, calls `findByOrderId` twice (save + update), and entity status becomes `COMPLETED` |
| `processOrder_FatalError_ThrowsInvalidDataException` | `FATAL_ERROR` product name throws `InvalidDataException` before any DB call |
| `processOrder_InventoryFailure_ThrowsInventoryException` | `FAIL_INVENTORY` saves order but then throws `InventoryException` |
| `processOrder_PaymentFailure_ThrowsPaymentException` | `FAIL_PAYMENT` saves order but then throws `PaymentException` |

**Key JUnit 5 / Mockito Concepts Demonstrated:**
- `when(...).thenReturn(...)` — stub a mock's method with specific return values (including chaining multiple calls).
- `assertThrows(ExceptionType.class, () -> ...)` — assert that a specific exception type is thrown.
- `verify(mock, times(N))` — assert a method was called exactly N times.
- `verify(mock, never())` — assert a method was never called.

---

### `OrderProducerTest.java` — Pure Mockito

**What it tests:** That the producer calls `JmsTemplate.convertAndSend()` with the correct queue name and exact payload.

| Test | What It Verifies |
|---|---|
| `sendOrder_CallsJmsTemplateWithCorrectQueueAndPayload` | `jmsTemplate.convertAndSend("order-queue", orderEvent)` is called once |

**Key Concepts Demonstrated:**
- Testing the "boundary" between your code and a framework (`JmsTemplate`).
- `verify(jmsTemplate).convertAndSend(eq("order-queue"), eq(orderEvent))` — exact argument matching.

---

### `OrderConsumerTest.java` — Pure Mockito

**What it tests:** The consumer's error routing logic — which types of errors go to `ERROR_QUEUE` vs. `DEAD_LETTER_QUEUE`.

| Test | What It Verifies |
|---|---|
| `receiveOrder_HappyPath_ProcessesSuccessfully` | Success case: no JmsTemplate calls at all (no routing needed) |
| `receiveOrder_FatalError_MovesToDLQ` | `InvalidDataException` → message sent to `DEAD_LETTER_QUEUE` |
| `receiveOrder_RecoverableErrorUnderMaxRetries_MovesToErrorQueue` | Retry count 1 → message sent to `ERROR_QUEUE` |
| `receiveOrder_RecoverableErrorAtMaxRetries_MovesToDLQ` | Retry count 3 → message sent to `DEAD_LETTER_QUEUE` |

**Key Concepts Demonstrated:**
- `doThrow(new SomeException()).when(mock).method(arg)` — stub a void method to throw an exception.
- Testing the **boundary condition** (`retryCount < 3` vs `retryCount >= 3`).
- `verifyNoInteractions(jmsTemplate)` — the happy path MUST NOT trigger any routing.

---

## Test Pyramid

```
        ▲
       /█\
      /███\         Integration Tests (9)
     /█████\        Slow, heavy, high confidence
    /---------\
   /███████████\
  /█████████████\   Unit Tests (15)
 /███████████████\  Fast, isolated, precise
/─────────────────\
```

The goal is to have **many unit tests** (fast, isolated) for the business logic, and **fewer integration tests** (slower, full context) to verify the wiring between components.

---

## Key JUnit 5 Annotations Reference

| Annotation | Purpose |
|---|---|
| `@Test` | Marks a method as a test case |
| `@ExtendWith(MockitoExtension.class)` | Enables Mockito support without Spring context |
| `@Mock` | Creates a Mockito mock of a class/interface |
| `@InjectMocks` | Creates the class under test and injects all `@Mock` fields |
| `@Captor` | Creates an `ArgumentCaptor` to capture method arguments |
| `@SpringBootTest` | Loads the full Spring application context |
| `@WebMvcTest` | Loads only the web (MVC) layer — a "slice test" |
| `@MockBean` | Like `@Mock` but registered in the Spring context |
| `@Autowired` | Injects Spring beans in test classes |
| `@BeforeEach` | Runs before each test method |
| `@AfterEach` | Runs after each test method |

## Key Mockito Methods Reference

| Method | Purpose |
|---|---|
| `when(mock.method()).thenReturn(value)` | Stub a method to return a value |
| `when(mock.method()).thenThrow(ex)` | Stub a method to throw an exception |
| `doThrow(ex).when(mock).voidMethod()` | Stub a void method to throw an exception |
| `verify(mock).method(args)` | Assert a method was called exactly once |
| `verify(mock, times(N)).method()` | Assert a method was called N times |
| `verify(mock, never()).method()` | Assert a method was never called |
| `verifyNoInteractions(mock)` | Assert no methods were ever called on the mock |
| `ArgumentCaptor.capture()` | Captures the actual argument passed to a mock |
| `captor.getValue()` | Retrieves the captured argument after `verify()` |
