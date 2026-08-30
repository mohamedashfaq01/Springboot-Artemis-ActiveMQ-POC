# 🗄️ H2 Database Console Guide

The application uses **H2** — an embedded, in-memory relational database — for persisting orders during the runtime of the application. Spring Boot automatically creates the schema on startup and drops it on shutdown.

H2 ships with a built-in browser-based SQL console that lets you inspect data in real time while the app is running.

---

## 1. Prerequisites

The H2 console must be enabled in your application configuration. Verify that your `application.yml` contains:

```yaml
spring:
  h2:
    console:
      enabled: true
      path: /h2-console
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: update
```

> **Important:** Do NOT enable the H2 console in production (`spring.h2.console.enabled=false`). It is for development/learning only.

---

## 2. Accessing the Console

### Step 1 — Start the Application
```bash
.\gradlew.bat bootRun        # Windows
./gradlew bootRun            # Mac/Linux
```
Wait until you see: `Started ArtemisJmsApplication in X.XX seconds`

### Step 2 — Open the Console in Your Browser
Navigate to:
```
http://localhost:8080/h2-console
```

### Step 3 — Connect to the Database

Fill in the login form exactly as shown:

| Field | Value |
|---|---|
| **Saved Settings** | Generic H2 (Embedded) |
| **Setting Name** | Generic H2 (Embedded) |
| **Driver Class** | `org.h2.Driver` |
| **JDBC URL** | `jdbc:h2:mem:testdb` |
| **User Name** | `sa` |
| **Password** | *(leave completely blank)* |

> ⚠️ **Common Mistake:** The default JDBC URL shown on the page is usually `jdbc:h2:~/test`. You **must** change it to `jdbc:h2:mem:testdb` (or whatever is in your `application.yml`) otherwise you'll connect to a different, empty database.

Click **Connect**.

---

## 3. Exploring the Orders Table

Once connected, you'll see the **H2 Console** — a SQL workbench on the left panel and a table explorer on the right.

### View All Orders
```sql
SELECT * FROM ORDERS;
```

### View Orders with Key Columns (Recommended)
```sql
SELECT 
    ORDER_ID,
    PRODUCT_NAME,
    QUANTITY,
    PRICE,
    TOTAL_AMOUNT,
    CUSTOMER_NAME,
    STATUS,
    ORDER_DATE
FROM ORDERS
ORDER BY ORDER_DATE DESC;
```

### Count Orders by Status
```sql
SELECT STATUS, COUNT(*) AS TOTAL
FROM ORDERS
GROUP BY STATUS;
```

### Find a Specific Order
```sql
SELECT * FROM ORDERS WHERE ORDER_ID = 'ORD-A2127BF3';
```

---

## 4. Understanding the Data After Each Test Scenario

After you fire the API requests from the [Run & Test Guide](run_and_test_guide.md), here's what you'll see in the DB:

### After a Happy Path order:
| ORDER_ID | PRODUCT_NAME | STATUS | TOTAL_AMOUNT |
|---|---|---|---|
| `ORD-XXXXXXXX` | iPhone 15 Pro | **COMPLETED** | 1999.98 |

The order moves from `PROCESSING` → `COMPLETED` almost instantly.

### After a FAIL_PAYMENT order:
| ORDER_ID | PRODUCT_NAME | STATUS | TOTAL_AMOUNT |
|---|---|---|---|
| `ORD-XXXXXXXX` | FAIL_PAYMENT | **PROCESSING** | 500.00 |

The order is saved at Step 1 (`PROCESSING`) but never reaches COMPLETED because Steps 2 and 3 fail during retries. The final retry exhausts and routes to `DEAD_LETTER_QUEUE`, but the DB record remains `PROCESSING`.

### After a FATAL_ERROR order:
No row in the DB at all. The `InvalidDataException` is thrown **before** Step 1 (Save Order) runs, so nothing is persisted.

---

## 5. Schema Definition

The `ORDERS` table is auto-created by Hibernate based on `OrderEntity.java`. Here is the schema:

```sql
CREATE TABLE ORDERS (
    ID           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ORDER_ID     VARCHAR(255) NOT NULL UNIQUE,  -- Business ID (e.g. ORD-A1B2C3D4)
    PRODUCT_NAME VARCHAR(255) NOT NULL,
    QUANTITY     INT          NOT NULL,
    PRICE        DOUBLE       NOT NULL,
    TOTAL_AMOUNT DOUBLE       NOT NULL,
    CUSTOMER_NAME VARCHAR(255) NOT NULL,
    ORDER_DATE   TIMESTAMP    NOT NULL,
    STATUS       VARCHAR(255) NOT NULL           -- PROCESSING | COMPLETED
);
```

The `UNIQUE` constraint on `ORDER_ID` is key for **idempotency** — it prevents duplicate rows when a JMS message is retried.

---

## 6. Important Notes

### ⚠️ Data is Ephemeral
Because H2 runs **in-memory** (`jdbc:h2:mem:testdb`), all data is destroyed when the application stops. Every restart gives you a fresh, empty database. This is by design for a learning/dev environment.

### ✅ Real-time Updates
You can fire API requests in one window and refresh the H2 console query in another to see the order status change live (e.g., watch it go from `PROCESSING` to `COMPLETED`).

### 🔒 Not for Production
H2 is an excellent in-memory database for learning, testing, and prototyping. For a real-world deployment, replace H2 with PostgreSQL or MySQL by swapping the dependency and updating the `application.yml` datasource URL.

---

## 7. Switching to a Persistent H2 File (Optional)

If you want data to survive restarts during development (without switching to a real DB), change the JDBC URL to a file-based H2 database:

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/orderdb    # Saves to ./data/orderdb.mv.db
```

Then connect in the console using:
```
jdbc:h2:file:./data/orderdb
```

> Note: You'll also need to add `./data/` to your `.gitignore`.
