# 🎯 Project Summary: Spring Boot + Artemis JMS Order Processing

## 1. What We Wanted to Achieve

The goal of this project was to build a learning-oriented **Order Processing Application** that demonstrates the full lifecycle of async message processing using **Java Message Service (JMS)** with **Apache ActiveMQ Artemis** in a **Spring Boot 3.x** environment.

### Core Objectives

| # | Objective | How It Was Solved |
|---|-----------|-------------------|
| 1 | **Asynchronous Processing** | REST API returns `HTTP 202` immediately; all heavy logic runs in the JMS Consumer |
| 2 | **JSON Message Passing** | Configured `MappingJackson2MessageConverter` in `JmsConfig.java` to serialize DTOs to JSON |
| 3 | **Transaction Management** | JMS listener runs inside a `SESSION_TRANSACTED` context — either all steps pass or the message rolls back |
| 4 | **Application-Level Retries** | `OrderConsumer` catches errors and manually routes to `ERROR_QUEUE`; `ErrorConsumer` replays with backoff |
| 5 | **Dead Letter Queue (DLQ)** | Fatal errors or messages exceeding max retries are permanently sent to `DEAD_LETTER_QUEUE` |
| 6 | **Database Persistence** | Spring Data JPA + H2 in-memory database tracks every order and its lifecycle status |
| 7 | **Idempotency** | Unique DB constraint on `orderId` prevents duplicate rows on JMS retries |
| 8 | **Zero External Dependencies** | Everything runs embedded — no standalone Artemis broker or external DB installation needed |
| 9 | **Comprehensive Testing** | Full integration test suite + dedicated unit tests for every class using JUnit 5 & Mockito |

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Spring Boot Application                        │
│                                                                         │
│  Client (Postman/cURL)                                                  │
│       │                                                                 │
│       ▼  POST /api/orders                                               │
│  ┌──────────────────┐      ┌──────────────────┐      ┌───────────────┐ │
│  │ OrderController  │─────▶│  OrderService    │─────▶│ OrderProducer │ │
│  │ (REST layer)     │      │ (business logic) │      │ (JmsTemplate) │ │
│  └──────────────────┘      └──────────────────┘      └───────┬───────┘ │
│       │                                                       │         │
│  HTTP 202 ACCEPTED                                            │ send    │
│  (immediate response)                                         ▼         │
│                                                    ┌─────────────────┐  │
│                                                    │   order-queue   │  │
│                                                    │   (Artemis)     │  │
│                                                    └────────┬────────┘  │
│                                                             │ @JmsListener
│                                                             ▼           │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                       OrderConsumer                                │ │
│  │  ┌─────────────┐  ┌──────────────────┐  ┌───────────────────────┐ │ │
│  │  │ Step 1:     │  │ Step 2:          │  │ Step 3:               │ │ │
│  │  │ Save Order  │─▶│ Reserve Inventory│─▶│ Process Payment       │ │ │
│  │  │ (H2 DB)     │  │ (simulated)      │  │ (simulated)           │ │ │
│  │  └─────────────┘  └──────────────────┘  └───────────────────────┘ │ │
│  │                                                                    │ │
│  │    ✅ All pass → Order Status: COMPLETED                           │ │
│  │    ❌ Exception → Route to ERROR_QUEUE (retry) or DLQ (fatal)     │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│   ERROR_QUEUE ─(wait 500ms)─▶ order-queue (retry, max 3 times)         │
│   DEAD_LETTER_QUEUE ─────────▶ Permanent parking (manual investigation) │
│                                                                         │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │               H2 In-Memory Database (orders table)                │ │
│  │  orderId | productName | status      | totalAmount | customerName │ │
│  │  ORD-001 | iPhone 15   | COMPLETED   | 1999.98     | Ashfaq       │ │
│  │  ORD-002 | FAIL_PAYMENT| PROCESSING  | 500.00      | Ashfaq       │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. How We Implemented It

### Layer 1: REST Controller (`OrderController.java`)
- Receives an `OrderRequest` from the client.
- Returns an **instant `HTTP 202 ACCEPTED`** response — does not wait for processing.
- Why 202 and not 200/201? Because the work hasn't happened yet — it's been queued.

### Layer 2: Service Orchestrator (`OrderService.java`)
- Validates the incoming request (product name, quantity, price, customer).
- Generates a unique `orderId` using `UUID` prefixed with `ORD-`.
- Calculates `totalAmount = price × quantity`.
- Builds an `OrderEvent` DTO and hands it to the producer.

### Layer 3: JMS Producer (`OrderProducer.java`)
- Uses Spring's `JmsTemplate.convertAndSend("order-queue", orderEvent)`.
- Jackson automatically serializes `OrderEvent` → JSON string.
- The message is now safely in the embedded Artemis broker.

### Layer 4: JMS Consumer (`OrderConsumer.java`)
- Annotated with `@JmsListener(destination = "order-queue")`.
- Calls `OrderProcessingService.processOrder()` — the 3-step pipeline.
- Manually handles two categories of error:
  - **Recoverable** (`PaymentException`, `InventoryException`): routes to `ERROR_QUEUE`, increments `RetryCount` header.
  - **Fatal** (`InvalidDataException`): routes directly to `DEAD_LETTER_QUEUE`.

### Layer 5: Error Consumer (`ErrorConsumer.java`)
- Listens to `ERROR_QUEUE`.
- Waits 500ms (backoff) then re-sends the message to `order-queue` with an incremented `RetryCount`.

### Layer 6: Processing Service (`OrderProcessingService.java`)
- **Step 1 — Save Order (Idempotent):** Checks if `orderId` already exists in DB. If yes, resets status to `PROCESSING` and skips the insert. If no, creates and persists a new `OrderEntity`.
- **Step 2 — Reserve Inventory:** Simulated. Product name `FAIL_INVENTORY` triggers failure.
- **Step 3 — Process Payment:** Simulated. Product name `FAIL_PAYMENT` triggers failure.
- On full success, calls `updateOrderStatus("COMPLETED")`.

### Layer 7: Database (`OrderEntity` + `OrderRepository`)
- `OrderEntity` is a JPA entity mapped to the `orders` table.
- `OrderRepository` extends `JpaRepository` for CRUD + custom `findByOrderId()`.
- H2 auto-creates the schema on startup and drops it on shutdown.

---

## 4. Key Design Decisions

### Why Application-Level Retries Instead of Broker-Level?
Broker-level retries (configured in `broker.xml`) are opaque and hard to control per-exception type. With application-level routing:
- We get **full control** over which exceptions trigger retries vs. immediate DLQ.
- We can implement **custom backoff logic** (e.g., exponential backoff).
- The retry flow is **visible in code**, not hidden in broker config files.

### Why Keep `OrderRequest`, `OrderEvent`, and `OrderEntity` Separate?
They look similar but serve entirely different purposes:
- `OrderRequest`: Represents **what the client sends** (untrusted external input).
- `OrderEvent`: Represents **what travels through the queue** (enriched, system-generated fields added).
- `OrderEntity`: Represents **what gets persisted** (database representation with lifecycle status).

Mixing these would create tight coupling between layers and make future changes (e.g., changing the API contract without affecting the DB schema) very difficult.

### Why Idempotency?
JMS guarantees **at-least-once delivery**, not exactly-once. During retries, Step 1 (Save Order) runs again. Without the idempotency check, we'd get:
- Attempt 1: Save order ✅ → Payment ❌ → Retry
- Attempt 2: Save order → **Duplicate key violation / second row inserted!**

The `findByOrderId()` check + `UNIQUE` DB constraint prevents this entirely.

---

## 5. Project File Structure

```
src/
├── main/java/com/learnwithashfaq/artemis/
│   ├── ArtemisJmsApplication.java       ← Spring Boot entry point
│   ├── config/
│   │   └── JmsConfig.java               ← Jackson JSON converter for JMS
│   ├── controller/
│   │   └── OrderController.java         ← REST endpoint (POST /api/orders)
│   ├── consumer/
│   │   ├── OrderConsumer.java           ← @JmsListener + manual error routing
│   │   └── ErrorConsumer.java           ← Retry forwarder (ERROR_QUEUE → order-queue)
│   ├── producer/
│   │   └── OrderProducer.java           ← JmsTemplate wrapper
│   ├── service/
│   │   ├── OrderService.java            ← Orchestrator (validate, enrich, send)
│   │   └── OrderProcessingService.java  ← 3-step business pipeline
│   ├── dto/
│   │   ├── OrderRequest.java            ← REST API input DTO
│   │   ├── OrderEvent.java              ← JMS message DTO
│   │   └── OrderResponse.java           ← REST API response DTO
│   ├── entity/
│   │   └── OrderEntity.java             ← JPA entity (orders table)
│   ├── repository/
│   │   └── OrderRepository.java         ← Spring Data JPA repository
│   └── exception/
│       ├── GlobalExceptionHandler.java  ← @ControllerAdvice for REST errors
│       ├── InventoryException.java      ← Recoverable error
│       ├── PaymentException.java        ← Recoverable error
│       ├── InvalidDataException.java    ← Fatal error (goes straight to DLQ)
│       └── OrderProcessingException.java ← Base exception class
│
└── test/java/com/learnwithashfaq/artemis/
    ├── integration/
    │   └── OrderFlowIntegrationTest.java ← 9 end-to-end flow tests
    ├── controller/
    │   └── OrderControllerTest.java      ← @WebMvcTest unit tests
    ├── service/
    │   ├── OrderServiceTest.java         ← Mockito unit tests
    │   └── OrderProcessingServiceTest.java ← Mockito unit tests
    ├── producer/
    │   └── OrderProducerTest.java        ← Mockito unit tests
    └── consumer/
        └── OrderConsumerTest.java        ← Mockito unit tests
```

---

## 6. Technologies Used

| Technology | Version | Purpose |
|---|---|---|
| Spring Boot | 3.4.4 | Application framework |
| Spring JMS | (via Boot) | JMS template + listener container |
| Apache ActiveMQ Artemis | (via Boot) | Embedded JMS broker |
| Spring Data JPA | (via Boot) | ORM / repository abstraction |
| H2 Database | (via Boot) | In-memory relational database |
| Lombok | Latest | Reduce boilerplate code |
| JUnit 5 | (via Boot) | Test framework |
| Mockito | (via Boot) | Mocking framework for unit tests |
| Awaitility | 4.2.2 | Async assertion polling in tests |
| Jackson | (via Boot) | JSON serialization/deserialization |
| Java | 21 | Language |
| Gradle | 8.x | Build tool |
