# 📓 Notes: JMS Transactions vs @Transactional — Deep Dive

> This is a focused FAQ-style notes file on one of the most confusing topics in Spring JMS:
> **Why do we use `session-transacted: true` instead of (or alongside) `@Transactional`?**
> 
> Understanding the difference between **JMS transactions** and **database transactions** is critical
> to building resilient messaging systems.

---

## The Core Problem to Understand

Imagine your `@JmsListener` method does **two things**:
1. Saves an order to the database (DB write)
2. Acknowledges the JMS message (removes it from the queue)

The question is: **what happens if Step 1 succeeds but Step 2 fails?**
Or what if the JVM crashes between them?

This is the **dual-write problem**, and it's the root of why transactions in JMS systems are complex.

---

## FAQ 1: What is `session-transacted: true` and what does it actually do?

**Location in this project:**
```yaml
# application.yml
spring:
  jms:
    listener:
      session-transacted: true   # ← This line
```

### What it does:

It wraps every single call to your `@JmsListener` method inside a **JMS local transaction** on the broker side.

```
┌──────────────────────────────────────────────────────────────┐
│                      JMS Session Transaction                  │
│                                                              │
│  1. Broker delivers message to your @JmsListener            │
│  2. Your code runs (OrderConsumer.receiveOrder() executes)   │
│  3a. Method completes normally → session.commit()            │
│       → message is permanently REMOVED from the queue       │
│  3b. Method throws an UNCAUGHT exception → session.rollback()│
│       → message is PUT BACK in the queue for retry          │
└──────────────────────────────────────────────────────────────┘
```

**Key rule:** It only watches the `@JmsListener` method boundary. It knows nothing about your database.

### In plain English:

> "If my listener method crashes unexpectedly (unhandled exception), the message is NOT lost.
> The broker will redeliver it. If my method finishes cleanly, the message is consumed forever."

---

## FAQ 2: What is `@Transactional` and what does IT do?

`@Transactional` is a **Spring/database transaction** annotation. It wraps code in a JDBC transaction, meaning it controls **database operations** (commit/rollback of SQL statements).

```java
@Transactional  // Controls: INSERT, UPDATE, DELETE in H2/MySQL/PostgreSQL
public void saveOrder(OrderEvent event) {
    orderRepository.save(entity); // This SQL is inside a DB transaction
}
```

```
┌──────────────────────────────────────────────────────────────┐
│                    @Transactional (DB Transaction)            │
│                                                              │
│  1. Method called → connection.setAutoCommit(false)          │
│  2. SQL operations run (INSERT, UPDATE, DELETE)              │
│  3a. Method returns → connection.commit()                    │
│       → data is permanently written to the database         │
│  3b. Exception thrown → connection.rollback()                │
│       → ALL SQL changes from this method are undone         │
└──────────────────────────────────────────────────────────────┘
```

It knows **nothing** about JMS or message queues. It only talks to the database.

---

## FAQ 3: What's the actual difference? A side-by-side comparison

| Feature | `session-transacted: true` (JMS) | `@Transactional` (DB) |
|---|---|---|
| **What it controls** | Whether a JMS message is acknowledged/requeued | Whether SQL data is committed/rolled back |
| **What it protects** | Message queue state (queue) | Database state (DB rows) |
| **Trigger for rollback** | Uncaught exception exits the `@JmsListener` method | Any exception exits the `@Transactional` method |
| **Implemented by** | JMS Broker (Artemis) | JDBC Driver / Spring's `PlatformTransactionManager` |
| **Annotation needed?** | No — configured via `application.yml` | Yes — you add `@Transactional` to your method/class |
| **Scope** | The entire `@JmsListener` method call | The annotated method (and all called methods) |
| **Visible in** | Broker queue state | Database table rows |

### Analogy:

Think of it like a package delivery:

- **JMS Transaction** = the delivery system's tracking. "Was the package successfully handed off to the recipient? If not, try again."
- **@Transactional** = the warehouse's inventory system. "Did we correctly log what went in/out of the warehouse shelves?"

They track completely different systems. You can fail one without failing the other.

---

## FAQ 4: Why did we NOT use `@Transactional` on `OrderConsumer`?

In our `OrderConsumer.receiveOrder()`, we **intentionally do NOT add `@Transactional`** for a very specific reason:

### The reason: We catch and handle our own exceptions

Look at our consumer:

```java
@JmsListener(destination = "order-queue")
public void receiveOrder(@Payload OrderEvent orderEvent, ...) {
    try {
        orderProcessingService.processOrder(orderEvent);
        // ✅ Success → method returns normally → JMS transaction COMMITS
        
    } catch (InvalidDataException ex) {
        // Fatal → route to DLQ manually
        jmsTemplate.convertAndSend(DEAD_LETTER_QUEUE, orderEvent, ...);
        // ✅ Method still returns normally → JMS transaction COMMITS (removes from order-queue)
        
    } catch (OrderProcessingException ex) {
        // Recoverable → route to ERROR_QUEUE manually
        jmsTemplate.convertAndSend(ERROR_QUEUE, orderEvent, ...);
        // ✅ Method still returns normally → JMS transaction COMMITS (removes from order-queue)
    }
    
    // KEY: We NEVER rethrow. Method always returns cleanly.
    // This means: the original message is ALWAYS committed out of order-queue.
    // We manually moved it to ERROR_QUEUE or DLQ before committing.
}
```

The JMS transaction commits in **all cases** because we catch every exception.
This is intentional — we don't want the message to stay in `order-queue` after we've routed it elsewhere.

If we had re-thrown the exception:
1. JMS session would rollback → message goes back to `order-queue`
2. **AND** the message is already in `ERROR_QUEUE` (we sent it there before rethrowing)
3. Result: **Duplicate message in two queues!**

---

## FAQ 5: So when WOULD a JMS rollback happen in our system?

In our current design, the JMS transaction rolls back **only if a truly unexpected/unhandled exception bubbles out** of the `receiveOrder()` method — something we didn't catch.

For example, an `OutOfMemoryError` or a `NullPointerException` that slips through our catch blocks.

If that happens:
1. The JMS session rolls back.
2. Artemis puts the message back in `order-queue`.
3. The message will be redelivered after a short delay.

---

## FAQ 6: When should I add `@Transactional` to `OrderProcessingService`?

You would add `@Transactional` to `OrderProcessingService.processOrder()` if you want **all DB writes inside that method to be atomic** — i.e., either ALL succeed or ALL are rolled back together.

Currently, our 3-step pipeline does 3 separate DB saves:
1. `saveOrder()` → save with status `PROCESSING`
2. (Inventory step — no DB write in our simulation)
3. `updateOrderStatus("COMPLETED")` → update status

**Without `@Transactional` on processOrder:**
- If Step 3 fails after Step 1 succeeded, the DB row stays with `PROCESSING` status.
- This is actually our desired behavior — we want the order saved in `PROCESSING` state even on failure.

**With `@Transactional` on processOrder:**
- If Step 3 fails, ALL DB changes in that method call are rolled back — the entire row disappears.
- This might NOT be what you want — you lose visibility that the order ever existed.

> **Conclusion for our project:** We intentionally do NOT put `@Transactional` on `processOrder()` because we WANT the partial state (`PROCESSING`) to be visible in the database even after failures.

---

## FAQ 7: Can we use BOTH? Is that possible?

**Yes, absolutely.** You can use both simultaneously. They operate on completely independent systems and don't conflict.

### Example: Using Both Together

```java
// In application.yml:
spring.jms.listener.session-transacted: true  ← JMS transaction always active

// In your service:
@Transactional  ← DB transaction on top of it
public void processOrder(OrderEvent event) {
    orderRepository.save(entity);        // DB write 1
    inventoryService.reserve(event);     // DB write 2 (in another service)
    paymentService.process(event);       // DB write 3 (in another service)
    entity.setStatus("COMPLETED");
    orderRepository.save(entity);        // DB write 4
}
```

Here's what happens on failure:

| What fails | JMS session | DB transaction | Result |
|---|---|---|---|
| Uncaught exception from `processOrder()` | Rolls back → message requeued | Rolls back → no DB writes persist | Message retried, DB is clean |
| Caught exception in consumer (our design) | Commits → message removed from queue | Depends on where exception was caught | Controlled routing to DLQ |

### When is using BOTH the right choice?

In a banking or payments system, you'd typically use both to guarantee:
1. No duplicate DB writes on retry (DB transaction rollback ensures clean state).
2. No lost messages on crash (JMS transaction rollback ensures redelivery).

### The Gold Standard: JTA / XA Transactions

For true atomic coordination between JMS and DB (the "dual-write problem"), you need an **XA transaction manager** like **Atomikos** or **Narayana**:

```
┌─────────────────────────────────────────────────┐
│              XA (2-Phase Commit)                 │
│                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │
│  │  Artemis │  │  H2 / PG │  │ Other Resource│  │
│  │  (JMS)   │  │  (DB)    │  │ (optional)   │  │
│  └────┬─────┘  └────┬─────┘  └──────┬───────┘  │
│       │             │               │            │
│  ┌────▼─────────────▼───────────────▼────────┐  │
│  │         XA Transaction Manager             │  │
│  │   "ALL commit together or ALL rollback"    │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

This is an **advanced topic** beyond the scope of this project — but it's the enterprise solution for environments where you absolutely cannot lose a message AND cannot have a duplicate DB write.

---

## FAQ 8: How does `session-transacted` behave in our Integration Tests?

This is an excellent point. In our `OrderFlowIntegrationTest`, we use:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
```

This loads the **full Spring application context**, including:
- The real embedded Artemis broker.
- The real embedded H2 database.
- The real `JmsListenerContainerFactory` with `session-transacted: true`.

So in integration tests, **JMS transactions behave exactly the same as in production**. This is why our integration tests are reliable — there's no mocking or stubbing of the transaction behavior.

### What Awaitility does in this context:

```java
// From OrderFlowIntegrationTest
await()
    .atMost(10, TimeUnit.SECONDS)
    .until(() -> orderRepository.findByOrderId(orderId)
        .map(e -> "COMPLETED".equals(e.getStatus()))
        .orElse(false));
```

This polling loop is necessary because:
1. The REST API returns `202 ACCEPTED` immediately.
2. The JMS consumer picks up the message **asynchronously** on a different thread.
3. The JMS transaction commits (and DB is updated) some milliseconds later.

Without Awaitility, if you checked the DB immediately after the API call, the order might still be `PROCESSING` (or not exist yet). Awaitility waits until the transaction completes.

### Scenario: Integration test with `FAIL_PAYMENT`

In `testPaymentFailure_OrderNotCompleted()`:

```
Test Thread                    Consumer Thread (JMS transaction)
     │                                   │
     │ POST /api/orders                  │
     │ (productName=FAIL_PAYMENT)        │
     │                                   │
     │ ← HTTP 202 Accepted              │
     │                                   │ ← JMS delivers message
     │                                   │   processOrder() throws PaymentException
     │                                   │   Consumer catches it → routes to ERROR_QUEUE
     │                                   │   Consumer returns normally
     │                                   │   JMS transaction COMMITS (removes from order-queue)
     │                                   │
     │                                   │ ← ErrorConsumer picks up from ERROR_QUEUE
     │                                   │   (wait 500ms, re-send to order-queue)
     │                                   │   (... repeat 3 times ...)
     │                                   │
     │                                   │ ← After 3 retries, routes to DEAD_LETTER_QUEUE
     │                                   │
     │ await().until(                    │
     │   orderStatus != COMPLETED,       │
     │   5 seconds timeout               │
     │ )                                 │
     │                                   │
     │ assertFalse(status==COMPLETED) ✅ │
```

### Why the DLQ test uses `jmsTemplate.receive()` instead of Awaitility:

```java
// From OrderFlowIntegrationTest — testInvalidData_GoesDirectlyToDLQ
jmsTemplate.setReceiveTimeout(5000);
jakarta.jms.Message dlqMessage = jmsTemplate.receive("DEAD_LETTER_QUEUE");
assertTrue(dlqMessage != null);
```

This is because the DLQ message lives in the JMS broker (Artemis memory), NOT in the H2 database. We can't query it with `orderRepository`. We have to use `jmsTemplate.receive()` which is a synchronous blocking call that waits up to 5 seconds for a message to appear in the queue.

---

## FAQ 9: What happens if I turn `session-transacted: false`?

If you set:
```yaml
spring.jms.listener.session-transacted: false
```

The JMS listener runs in **auto-acknowledge mode** — the message is acknowledged immediately when it's delivered, **before your code even runs**.

This means:
- If your `@JmsListener` method crashes halfway through, the message is already gone.
- **No retry, no DLQ, no recovery. The message is permanently lost.**

This is why `session-transacted: true` is critical for any production-grade JMS system.

---

## FAQ 10: Summary — When to Use What

| Scenario | What to Use |
|---|---|
| Guarantee message is not lost if consumer crashes | `session-transacted: true` |
| Ensure all DB writes in a method succeed or fail together | `@Transactional` |
| Route specific error types to DLQ without retry | Catch exception in consumer, call `jmsTemplate.send(DLQ)`, return normally |
| Ensure JMS + DB are atomically consistent (enterprise grade) | XA Transaction Manager (Atomikos/Narayana) — advanced |
| Async assertion in integration tests (wait for JMS processing) | Awaitility |
| Assert message ended up in DLQ | `jmsTemplate.receive("DEAD_LETTER_QUEUE")` with timeout |

---

## Quick Reference Card

```
session-transacted: true
═══════════════════════
Scope  → JMS message lifecycle (queue)
Rollback → Exception escapes @JmsListener method
Commit   → Method returns normally
Effect   → Message is retried OR permanently consumed


@Transactional
═══════════════════════
Scope  → Database writes (SQL)
Rollback → Exception escapes annotated method
Commit   → Method returns normally
Effect   → DB rows written OR all rolled back


Using BOTH
═══════════════════════
Both operate independently.
JMS tx controls the queue.
DB tx controls the database.
Neither knows about the other.
(Use XA for atomic coordination across both)


Our Design Choice (OrderConsumer)
═══════════════════════════════════
✅ session-transacted: true   (always active via yml)
❌ @Transactional NOT used on consumer  (we handle errors manually)
❌ @Transactional NOT on processOrder   (we WANT partial state in DB on failure)
✅ Manual routing to ERROR_QUEUE / DLQ  (full programmatic control)
```
