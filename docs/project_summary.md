# 🎯 Project Summary: Spring Boot + Artemis JMS Order Processing

## 1. What We Wanted to Achieve

The goal of this project was to build a simple, learning-oriented **Order Processing Application** to demonstrate how to integrate **Java Message Service (JMS)** with **Apache ActiveMQ Artemis** in a **Spring Boot 3.x** environment.

Specifically, the core objectives were:
1. **Asynchronous Processing**: Decouple the REST API layer from the heavy business logic.
2. **JSON Message Passing**: Send human-readable JSON payloads through the message queue instead of opaque Java binary objects.
3. **Transaction Management**: Implement robust "all-or-nothing" processing where multiple business steps (Save Order, Reserve Inventory, Process Payment) execute in a single JMS transaction.
4. **Resilience (Retries & DLQ)**: Prove that if a downstream service fails (like a payment gateway), the message is automatically retried using exponential backoff, and eventually moved to a Dead Letter Queue (DLQ) if all retries fail.
5. **No External Dependencies**: Run everything completely embedded within the Spring Boot application (using an embedded Artemis broker) so it works out-of-the-box without Docker or standalone server installations.

---

## 2. How We Implemented It

We achieved this by building a decoupled architecture where the REST API acts purely as a gateway, and all heavy lifting is handed off to the JMS consumer via the embedded Artemis broker.

### Architecture Flow
1. **REST Controller** (`OrderController.java`):
   - Receives an `OrderRequest` from the user (e.g., Postman/cURL).
   - Instantly returns an `HTTP 202 ACCEPTED` response, acknowledging receipt without making the user wait for processing.
2. **Service Layer (Orchestrator)** (`OrderService.java`):
   - Validates the incoming request.
   - Enriches it by generating an `orderId` and calculating the `totalAmount`.
   - Converts it into an `OrderEvent` DTO.
3. **JMS Producer** (`OrderProducer.java`):
   - Uses Spring's `JmsTemplate` to convert the `OrderEvent` into a JSON string and send it to the `order-queue`.
4. **JMS Consumer** (`OrderConsumer.java`):
   - Listens to the `order-queue` using `@JmsListener`.
   - Executes inside a **JMS Transaction** (`session-transacted=true` in `application.yml`).
   - Reads the `JMSXDeliveryCount` header to track retry attempts.
5. **Business Logic** (`OrderProcessingService.java`):
   - Sequentially executes 3 steps: `saveOrder`, `reserveInventory`, and `processPayment`.
   - If a step fails, it throws an `OrderProcessingException`.

### Key Technical Implementations

#### 1. Embedded Artemis Broker
Instead of installing ActiveMQ separately, we used `artemis-server` and `artemis-jms-server` dependencies in `build.gradle`. We configured it to run purely in-memory using `broker-url: vm://0`.

#### 2. JSON Serialization
By default, JMS sends Java binary objects. We created `JmsConfig.java` to define a `MappingJackson2MessageConverter`. This forces Spring to use Jackson to convert our `OrderEvent` DTO into a raw JSON string (with a special `_type` property attached so the consumer knows how to deserialize it back into Java).

#### 3. Retry and Dead Letter Queue (DLQ) Configuration
We created `broker.xml` to explicitly define how Artemis handles failures:
- `<max-delivery-attempts>3</max-delivery-attempts>`: Allows exactly 3 attempts (1 original + 2 retries).
- `<redelivery-delay>5000</redelivery-delay>`: Waits 5 seconds before retrying.
- `<redelivery-delay-multiplier>2.0</redelivery-delay-multiplier>`: Doubles the wait time on subsequent retries (exponential backoff).
- `<dead-letter-address>DLQ</dead-letter-address>`: Moves the message to the DLQ after the 3rd failed attempt so it isn't lost permanently.

#### 4. JMS Session Transactions
We didn't use Spring's standard `@Transactional` database annotation. Instead, we relied on JMS session transactions by setting `spring.jms.listener.session-transacted=true` in `application.yml`.
- If `OrderConsumer.receiveOrder()` completes normally, the transaction **commits** and the message is permanently removed from the queue.
- If it throws an exception (which we simulated using special product names like `"FAIL_PAYMENT"`), the transaction **rolls back**, the message goes back to the queue, and Artemis schedules it for redelivery.

### Summary
By combining Spring Boot's powerful JMS auto-configuration with an embedded Artemis broker and custom retry logic, we built a highly resilient, enterprise-grade async processing pipeline that safely handles downstream failures without losing data.
