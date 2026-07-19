# Task List — Order Processing with JMS & Artemis

## Step 1 — Project Setup (Gradle + Dependencies)
- [x] Create `build.gradle` with all dependencies
- [x] Create `settings.gradle`
- [x] Create Gradle wrapper files
- [x] Create main application class `ArtemisJmsApplication.java`

## Step 2 — Configuration Layer
- [x] Create `application.yml`
- [x] Create `broker.xml` (Artemis broker config with DLQ + retries)
- [x] Create `JmsConfig.java` (Jackson message converter)

## Step 3 — DTOs
- [x] Create `OrderRequest.java`
- [x] Create `OrderEvent.java`
- [x] Create `OrderResponse.java`

## Step 4 — Producer
- [x] Create `OrderProducer.java`

## Step 5 — Service Layer
- [x] Create `OrderService.java`
- [x] Create `OrderProcessingService.java`

## Step 6 — Consumer (with Transactions)
- [x] Create `OrderConsumer.java`

## Step 7 — REST Controller
- [x] Create `OrderController.java`

## Step 8 — Exception Handling
- [x] Create `OrderProcessingException.java`
- [x] Create `InventoryException.java`
- [x] Create `PaymentException.java`
- [x] Create `GlobalExceptionHandler.java`

## Step 9 — Testing & Verification
- [x] Build the project
- [x] Run and test happy path
- [x] Test failure + retry + DLQ

## Step 10 — Learnings & Best Practices
- [x] Create walkthrough with learnings
