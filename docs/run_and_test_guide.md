# 🚀 How to Run and Test the Application

This guide explains how to start the application and manually test the JMS configuration, including happy paths, retries, and the Dead Letter Queue (DLQ).

---

## 1. Starting the Application

The application uses an **embedded Artemis broker**, which means the message queue starts automatically when the Spring Boot application starts. You don't need to install or run any external servers like Docker.

### Option A: Using your IDE (IntelliJ / Eclipse / VS Code)
Simply run the main class:
`com.learnwithashfaq.artemis.ArtemisJmsApplication.java`

### Option B: Using Gradle (Command Line)
Open your terminal in the project root directory and run:
```bash
# Windows
.\gradlew.bat bootRun

# Mac/Linux
./gradlew bootRun
```

Wait until you see the following log confirming the application has started:
`Started ArtemisJmsApplication in X seconds (process running for Y)`

---

## 2. Testing Scenarios

You can test the application using **Postman**, **cURL**, or any REST client. The API is available at:
`POST http://localhost:8080/api/orders`

---

### Scenario A: The "Happy Path" (Success)
This scenario simulates a completely successful order process. All 3 steps (Save, Reserve, Pay) will pass.

**Request (cURL):**
```bash
curl -X POST http://localhost:8080/api/orders \
-H "Content-Type: application/json" \
-d '{
    "productName": "iPhone 15 Pro",
    "quantity": 2,
    "price": 999.99,
    "customerName": "Ashfaq"
}'
```

**Expected Outcome:**
1. You will receive an immediate `HTTP 202 ACCEPTED` response.
2. In the application console logs, you will see:
   - `JMS PRODUCER — SENDING ORDER`
   - `JMS CONSUMER — MESSAGE RECEIVED`
   - `✅ ALL 3 STEPS COMPLETED SUCCESSFULLY!`
   - `✅ ORDER PROCESSED SUCCESSFULLY! JMS Transaction will COMMIT.`

---

### Scenario B: Inventory Failure (Triggers Retries)
This scenario simulates what happens if the Inventory service fails. We configured the `OrderProcessingService` to deliberately throw an `InventoryException` if the `productName` is `"FAIL_INVENTORY"`.

**Request (cURL):**
```bash
curl -X POST http://localhost:8080/api/orders \
-H "Content-Type: application/json" \
-d '{
    "productName": "FAIL_INVENTORY",
    "quantity": 1,
    "price": 300.00,
    "customerName": "Ashfaq"
}'
```

**Expected Outcome:**
1. The REST API still returns `HTTP 202 ACCEPTED`.
2. The consumer starts processing:
   - Step 1 (Save) ✅
   - Step 2 (Inventory) ❌ `INVENTORY FAILURE for Order...`
3. A JMS Transaction **ROLLBACK** occurs.
4. Artemis waits for the 5-second `redelivery-delay`.
5. The message is **redelivered** automatically. You will see `Attempt: 2 of 3`.
6. This repeats until `Attempt: 3 of 3` fails, and then the message is permanently moved to the **DLQ** (Dead Letter Queue).

---

### Scenario C: Payment Failure (Triggers Retries)
This scenario ensures that even if Step 1 and Step 2 succeed, a failure at the final step (Payment) rolls back the ENTIRE transaction. We trigger this using `"FAIL_PAYMENT"`.

**Request (cURL):**
```bash
curl -X POST http://localhost:8080/api/orders \
-H "Content-Type: application/json" \
-d '{
    "productName": "FAIL_PAYMENT",
    "quantity": 1,
    "price": 500.00,
    "customerName": "Ashfaq"
}'
```

**Expected Outcome:**
1. Step 1 (Save) ✅ and Step 2 (Inventory) ✅ will pass.
2. Step 3 (Payment) ❌ will fail.
3. The JMS transaction rolls back.
4. During the automatic retries (Attempt 2 and Attempt 3), **Step 1 and Step 2 will execute AGAIN**. 
   *(Note: This highlights why enterprise systems require idempotent operations, as explained in the walkthrough document).*
5. After the 3rd failed attempt, the message goes to the DLQ.

---

## 3. Viewing the DLQ (Dead Letter Queue) Messages

Because we are using an *embedded* broker running purely in-memory (`vm://0`), the DLQ contents are lost when the application stops. 

In a production scenario with an external Artemis server, you would log into the **Artemis Web Console** (usually `http://localhost:8161/console`) to manually view the failed messages sitting in the `DLQ` and choose to delete them, edit them, or push them back to the `order-queue` for reprocessing once the issue (like a broken payment gateway) is fixed.
