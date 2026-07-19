# Order Processing Application — Learn JMS with Apache ActiveMQ Artemis

## 📚 Part 1: Concepts (Before We Write Code)

### What is JMS?

**JMS (Java Message Service)** is a Java API that allows applications to **send and receive messages asynchronously**. Think of it as a postal service for your Java applications — one app drops a letter (message) in a mailbox (queue), and another app picks it up later. They don't need to talk to each other directly.

```
App A  ──(sends message)──►  [Queue]  ──(receives message)──►  App B
```

**Key terms:**
- **Producer** — sends the message
- **Consumer** — receives and processes the message
- **Queue** — holds messages (point-to-point, one consumer gets each message)
- **Topic** — broadcasts messages (pub-sub, all subscribers get each message)

---

### What is Apache ActiveMQ Artemis?

**Artemis** is the **message broker** — the "post office" itself. It's the next-generation Apache ActiveMQ, built for high performance and clustering. Spring Boot has first-class support for it via `spring-boot-starter-artemis`.

In our project, Artemis will:
- Run **embedded** inside our Spring Boot app (no separate installation needed for learning!)
- Hold queues where order messages wait to be processed
- Handle retries and dead-letter queues automatically

---

### Why Use a Message Broker?

| Problem | Without Broker | With Broker |
|---|---|---|
| Service B is down | Request fails immediately | Message waits in queue, processed when B is back |
| Slow processing | User waits for response | User gets instant "Order Placed" response |
| Spike in traffic | System overloads | Queue absorbs the spike, processes at its own pace |
| Retry on failure | You build retry logic yourself | Broker retries automatically |

---

### REST vs JMS — Real-World Banking Example

**Scenario: You transfer ₹10,000 from your bank app.**

| Aspect | REST (Synchronous) | JMS (Asynchronous) |
|---|---|---|
| **How it works** | Your app calls Payment API → waits → gets response | Your app sends message to queue → gets instant acknowledgment |
| **User experience** | Spinner for 5-10 seconds | Instant "Transfer initiated" |
| **If Payment Service is down** | ❌ "Service unavailable" error | ✅ Message waits in queue, processes when service is back |
| **Real-world analogy** | Phone call (both parties must be available) | WhatsApp message (receiver reads when free) |

**Rule of thumb:** Use REST for queries ("Show my balance"). Use JMS for commands that can be processed later ("Transfer money", "Place order").

---

### Message Flow — ASCII Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        ORDER PROCESSING FLOW                                 │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  [User/Postman]                                                              │
│       │                                                                      │
│       │ POST /api/orders  { "product": "iPhone", "quantity": 2 }             │
│       ▼                                                                      │
│  ┌──────────────┐    ┌──────────────┐    ┌───────────────────┐               │
│  │   REST       │───►│   Order      │───►│   JMS Producer    │               │
│  │   Controller │    │   Service    │    │  (sends JSON msg) │               │
│  └──────────────┘    └──────────────┘    └─────────┬─────────┘               │
│                                                    │                         │
│                                          ┌─────────▼─────────┐               │
│                                          │   ARTEMIS QUEUE   │               │
│                                          │  "order-queue"    │               │
│                                          └─────────┬─────────┘               │
│                                                    │                         │
│                                          ┌─────────▼─────────┐               │
│                                          │   JMS Consumer    │               │
│                                          │ (@JmsListener)    │               │
│                                          └─────────┬─────────┘               │
│                                                    │                         │
│                              ┌─────────────────────┼─────────────────────┐   │
│                              │           TRANSACTION BOUNDARY            │   │
│                              │                     │                     │   │
│                              │          ┌──────────▼──────────┐          │   │
│                              │          │  1. Save Order      │          │   │
│                              │          └──────────┬──────────┘          │   │
│                              │          ┌──────────▼──────────┐          │   │
│                              │          │  2. Reserve Stock   │          │   │
│                              │          └──────────┬──────────┘          │   │
│                              │          ┌──────────▼──────────┐          │   │
│                              │          │  3. Process Payment │          │   │
│                              │          └──────────┬──────────┘          │   │
│                              │                     │                     │   │
│                              │              ┌──────┴──────┐              │   │
│                              │              │             │              │   │
│                              │           SUCCESS       FAILURE           │   │
│                              │              │             │              │   │
│                              │          Commit TX     Rollback TX        │   │
│                              │          Message ACK   Message REDELIVERED│   │
│                              └──────────────┼─────────────┼──────────────┘   │
│                                             │             │                  │
│                                             ▼             ▼                  │
│                                     ✅ Order Done   🔄 Retry (max 3)        │
│                                                           │                  │
│                                                    After 3 retries           │
│                                                           │                  │
│                                                    ┌──────▼──────┐           │
│                                                    │     DLQ     │           │
│                                                    │ (Dead Letter│           │
│                                                    │   Queue)    │           │
│                                                    └─────────────┘           │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

### How JMS Transactions Work

When the consumer picks up a message:

1. **Transaction starts** automatically (Spring JMS wraps each `@JmsListener` call in a JMS transaction)
2. Consumer processes the message (Save → Reserve → Pay)
3. **If ALL steps succeed** → Transaction commits → Message is acknowledged → Removed from queue
4. **If ANY step fails** (exception thrown) → Transaction rolls back → Message goes back to queue → **Redelivered**
5. After max retries (we'll set 3) → Message goes to **DLQ (Dead Letter Queue)** — a "graveyard" for failed messages

```
Attempt 1: Save ✅ → Reserve ✅ → Pay ❌  →  ROLLBACK → Retry
Attempt 2: Save ✅ → Reserve ✅ → Pay ❌  →  ROLLBACK → Retry
Attempt 3: Save ✅ → Reserve ✅ → Pay ❌  →  ROLLBACK → Retry
Attempt 4: MAX RETRIES EXCEEDED → Move to DLQ
```

> [!IMPORTANT]
> The JMS transaction ensures the **message acknowledgment** is tied to successful processing. This is different from a database `@Transactional` — JMS transactions control whether the **message is removed from the queue or redelivered**.

---

## 🏗️ Part 2: Project Structure

```
src/main/java/com/learnwithashfaq/artemis/
├── ArtemisJmsApplication.java              # Main application class
├── config/
│   ├── JmsConfig.java                      # JMS + Jackson message converter
│   └── ArtemisConfig.java                  # Artemis broker + queue + DLQ config
├── controller/
│   └── OrderController.java                # REST endpoint
├── service/
│   ├── OrderService.java                   # Orchestrates flow
│   └── OrderProcessingService.java         # Simulates Save/Reserve/Pay steps
├── producer/
│   └── OrderProducer.java                  # Sends message to queue
├── consumer/
│   └── OrderConsumer.java                  # Listens to queue, processes orders
├── dto/
│   ├── OrderRequest.java                   # REST request DTO
│   ├── OrderEvent.java                     # JMS message DTO
│   └── OrderResponse.java                  # REST response DTO
└── exception/
    ├── OrderProcessingException.java       # Custom exception
    ├── InventoryException.java             # Inventory-specific exception
    ├── PaymentException.java               # Payment-specific exception
    └── GlobalExceptionHandler.java         # @RestControllerAdvice

src/main/resources/
├── application.yml                         # Artemis + JMS configuration
└── broker.xml                              # Artemis broker configuration (DLQ, retries)
```

---

## 🔧 Part 3: Implementation Steps

### Step 1 — Project Setup (Gradle + Dependencies)
- Initialize Gradle project with Spring Boot 3.x, Java 21
- Add dependencies: `spring-boot-starter-artemis`, `spring-boot-starter-web`, `lombok`, `jackson`
- Create `build.gradle`, `settings.gradle`, main application class

### Step 2 — Configuration Layer
- **`application.yml`** — Artemis embedded broker config, JMS settings
- **`broker.xml`** — Artemis broker descriptor with DLQ and retry settings
- **`ArtemisConfig.java`** — Programmatic queue and address configuration
- **`JmsConfig.java`** — Jackson-based message converter for JSON serialization

### Step 3 — DTOs
- **`OrderRequest.java`** — What the REST API accepts
- **`OrderEvent.java`** — What gets sent through JMS (serialized as JSON)
- **`OrderResponse.java`** — What the REST API returns to the user

### Step 4 — Producer
- **`OrderProducer.java`** — Uses `JmsTemplate` to send `OrderEvent` as JSON to `order-queue`

### Step 5 — Service Layer
- **`OrderService.java`** — Receives REST request, creates event, calls producer
- **`OrderProcessingService.java`** — Simulates the 3-step transactional process (Save → Reserve → Pay) with deliberate failure scenarios

### Step 6 — Consumer (with Transactions)
- **`OrderConsumer.java`** — `@JmsListener` that receives JSON, deserializes, calls processing service
- Transaction management: if any step throws, the JMS transaction rolls back → message redelivered
- Retry tracking via `JMSXDeliveryCount` header
- After max retries → message auto-moves to DLQ

### Step 7 — REST Controller
- **`OrderController.java`** — `POST /api/orders` endpoint

### Step 8 — Exception Handling
- Custom exceptions for each failure type
- `GlobalExceptionHandler` for REST errors
- Proper error logging in consumer

### Step 9 — Testing & Verification
- Start the application
- Send order via `curl`/Postman
- Observe logs showing the complete flow
- Trigger a payment failure to see retry + DLQ in action

### Step 10 — Learnings & Best Practices
- When to use JMS vs REST
- Common mistakes
- Enterprise (banking) best practices

---

## 📦 Key Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST API |
| `spring-boot-starter-artemis` | JMS + embedded Artemis broker |
| `artemis-server` | Embedded broker support |
| `artemis-jms-server` | JMS server for embedded mode |
| `jackson-databind` | JSON serialization of messages |
| `lombok` | Reduce boilerplate |

---

## ⚙️ Transaction Strategy

We use **JMS Session Transacted Mode** (`spring.jms.listener.session-transacted=true`):

- Spring JMS automatically wraps each `@JmsListener` invocation in a JMS local transaction
- On success → `session.commit()` → message acknowledged
- On exception → `session.rollback()` → message redelivered
- Artemis tracks delivery count and moves to DLQ after max-delivery-attempts (configured in `broker.xml`)

> [!NOTE]
> We are NOT using `@Transactional` (Spring's DB transaction). We're using JMS session transactions, which control message acknowledgment. In a real banking app, you'd combine both with a JTA transaction manager (like Atomikos) — but that's an advanced topic for later.

---

## Verification Plan

### Manual Verification
1. Start the Spring Boot app — confirm embedded Artemis starts
2. **Happy Path**: `POST /api/orders` with valid order → logs show Save ✅ → Reserve ✅ → Pay ✅
3. **Failure Path**: Send order with `product: "FAIL_PAYMENT"` → logs show retries → DLQ
4. Observe delivery count incrementing in logs across retries

### Automated Tests
- Not in scope for this learning exercise, but I'll add notes on how to test JMS in a follow-up

---

## Open Questions

> [!IMPORTANT]
> **Embedded vs External Artemis?**  
> I'll use an **embedded Artemis broker** (runs inside your Spring Boot app) so you don't need to install anything separately. This is perfect for learning. For production, you'd use an external broker. Should I proceed with embedded, or do you have Artemis/Docker installed?

> [!NOTE]
> **Failure Simulation**: I'll simulate failures using product name conventions (e.g., `"FAIL_PAYMENT"`, `"FAIL_INVENTORY"`). This lets you trigger different failure scenarios from Postman without needing real databases. Is this approach okay?
