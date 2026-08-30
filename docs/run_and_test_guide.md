# 🚀 How to Run, Test, and Package the Application

This guide explains how to run the app, test all scenarios manually, and package it for deployment.

---

## 1. Running the Application

### Option A: Run from source (Development)
```bash
# Windows
.\gradlew.bat bootRun

# Mac/Linux
./gradlew bootRun
```
The app will start on **`http://localhost:8080`**.

### Option B: Package and run as a JAR (Production-like)
```bash
# Step 1: Build
.\gradlew.bat build        # Windows
./gradlew build            # Mac/Linux

# Step 2: Run the JAR
java -jar build/libs/Springboot-Artemis-JMS-ActiveMQ-Example-1.0.0.jar
```
> **Tip:** Use `.\gradlew.bat build -x test` to skip tests during the build.

---

## 2. Running the Tests

### Run All Tests (Unit + Integration)
```bash
.\gradlew.bat test         # Windows
./gradlew test             # Mac/Linux
```

### Run Only Unit Tests
```bash
.\gradlew.bat test --tests "com.learnwithashfaq.artemis.controller.*"
.\gradlew.bat test --tests "com.learnwithashfaq.artemis.service.*"
.\gradlew.bat test --tests "com.learnwithashfaq.artemis.producer.*"
.\gradlew.bat test --tests "com.learnwithashfaq.artemis.consumer.*"
```

### Run Only Integration Tests
```bash
.\gradlew.bat test --tests "com.learnwithashfaq.artemis.integration.*"
```

### View Test Report
After running tests, open the HTML report:
```
build/reports/tests/test/index.html
```

**Expected: 24 tests, 0 failures.** See [`testing_guide.md`](testing_guide.md) for a full breakdown of all tests.

---

## 3. Accessing the H2 Database Console

The embedded H2 database has a browser-based console you can use to inspect order data in real time.

> **See [`h2_console_guide.md`](h2_console_guide.md) for a detailed walkthrough.**

Quick access:
1. Start the application.
2. Open: **`http://localhost:8080/h2-console`**
3. Enter:
   - **JDBC URL:** `jdbc:h2:mem:testdb`
   - **Username:** `sa`
   - **Password:** *(leave blank)*
4. Click **Connect**.
5. Run: `SELECT * FROM ORDERS;`

---

## 4. Manual Testing Scenarios (via Postman or cURL)

The API endpoint is: `POST http://localhost:8080/api/orders`

All requests return `HTTP 202 ACCEPTED` immediately. The actual processing (success or failure) happens asynchronously in the background — watch your **application logs** and the **H2 console** to see the result.

---

### ✅ Scenario A: Happy Path (Full Success)

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

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/orders" `
  -Method POST `
  -ContentType "application/json" `
  -Body '{"productName":"iPhone 15 Pro","quantity":2,"price":999.99,"customerName":"Ashfaq"}'
```

**Expected Response:**
```json
{
  "message": "Order placed successfully! It will be processed in the background.",
  "orderId": "ORD-A2127BF3",
  "status": "ACCEPTED",
  "timestamp": "2026-08-30T22:53:12"
}
```

**What happens in the background:**
```
📝 STEP 1/3 — ✅ Order saved to database (status: PROCESSING)
📦 STEP 2/3 — ✅ Inventory reserved
💳 STEP 3/3 — ✅ Payment processed
✅ ALL 3 STEPS COMPLETED! (status: COMPLETED)
```
→ DB Status: **COMPLETED**

---

### ❌ Scenario B: Payment Failure (Triggers Retries → DLQ)

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

**What happens in the background:**
```
📝 STEP 1/3 — ✅ Order saved
📦 STEP 2/3 — ✅ Inventory reserved
💳 STEP 3/3 — ❌ PAYMENT FAILURE! Routing to ERROR_QUEUE (retry 1/3)...

(ErrorConsumer waits 500ms → re-queues to order-queue)

💳 STEP 3/3 — ❌ PAYMENT FAILURE! Routing to ERROR_QUEUE (retry 2/3)...
💳 STEP 3/3 — ❌ PAYMENT FAILURE! MAX RETRIES EXHAUSTED → DEAD_LETTER_QUEUE
```
→ DB Status: **PROCESSING** (never reached COMPLETED)

---

### 📦 Scenario C: Inventory Failure (Triggers Retries → DLQ)

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

**What happens:** Same retry flow as payment failure, but fails at Step 2 (Inventory).

→ DB Status: **PROCESSING**

---

### 💀 Scenario D: Fatal Error (Straight to DLQ — No Retries)

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "productName": "FATAL_ERROR",
    "quantity": 1,
    "price": 100.00,
    "customerName": "Ashfaq"
  }'
```

**What happens:**
```
❌ FATAL DATA CORRUPTION DETECTED → InvalidDataException thrown
❌ FATAL ERROR: Moving DIRECTLY to DEAD_LETTER_QUEUE (no retries)
```
→ DB Status: **No record** (fatal error fires before Step 1 saves)

---

### ❌ Scenario E: Validation Errors (HTTP 400)

```bash
# Missing product name
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productName":"","quantity":2,"price":999.99,"customerName":"Ashfaq"}'

# Invalid quantity
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productName":"iPhone","quantity":0,"price":999.99,"customerName":"Ashfaq"}'

# Missing customer
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productName":"iPhone","quantity":2,"price":999.99,"customerName":""}'
```

**Expected Response (HTTP 400):**
```json
{
  "error": "Bad Request",
  "message": "Product name is required!"
}
```
These fail at the service layer before reaching JMS at all.

---

## 5. Useful SQL Queries for H2 Console

```sql
-- See all orders
SELECT ORDER_ID, PRODUCT_NAME, STATUS, TOTAL_AMOUNT, CUSTOMER_NAME, ORDER_DATE
FROM ORDERS
ORDER BY ORDER_DATE DESC;

-- Count by status
SELECT STATUS, COUNT(*) AS COUNT
FROM ORDERS
GROUP BY STATUS;

-- Find a specific order
SELECT * FROM ORDERS WHERE ORDER_ID = 'ORD-A2127BF3';

-- Find all completed orders
SELECT * FROM ORDERS WHERE STATUS = 'COMPLETED';

-- Find orders stuck in PROCESSING (failed retries)
SELECT * FROM ORDERS WHERE STATUS = 'PROCESSING';
```

---

## 6. Viewing DLQ Messages

Since we use an embedded in-memory broker, DLQ contents are **not visible in the H2 console** — messages in `DEAD_LETTER_QUEUE` are held in Artemis memory, not in the database.

To inspect them in a real enterprise environment, you would:
1. Use an **external Artemis broker** with its built-in Web Console at `http://localhost:8161/console`.
2. Browse to `DEAD_LETTER_QUEUE`.
3. View, retry, or purge the failed messages.

For this embedded application, the best way to confirm DLQ routing is by watching the **application logs** in the terminal.
