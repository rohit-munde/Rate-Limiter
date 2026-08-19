# API Gateway & Multi-Algorithm Rate Limiter

This project is a high-performance **API Gateway (Reverse Proxy)** featuring a pluggable, multi-algorithm **Rate Limiting Middleware**. It is built in Java using Spring Boot and is designed as a practical implementation of rate-limiting algorithms described in the book ***System Design Interview – An insider's guide (Volume 1)* by Alex Xu**.

This repository is optimized for learning and experimentation, allowing developers to switch, configure, and compare different rate-limiting strategies in real-time.


## How to answer during interview
<img width="1768" height="1728" alt="image" src="https://github.com/user-attachments/assets/c53aa83a-a8db-4341-a183-34cf33885c1b" />

---

## 📖 Table of Contents
1. [System Architecture](#-system-architecture)
2. [Algorithm Comparison Table](#-algorithm-comparison-table)
3. [Rate Limiting Algorithms](#-rate-limiting-algorithms)
   - [Token Bucket](#1-token-bucket)
   - [Leaking Bucket](#2-leaking-bucket)
   - [Fixed Window Counter](#3-fixed-window-counter)
   - [Sliding Window Log](#4-sliding-window-log)
   - [Sliding Window Counter](#5-sliding-window-counter)
4. [Configuration Guide](#-configuration-guide)
5. [Getting Started & Verification](#-getting-started--verification)

---

## 🏗️ System Architecture

The project acts as an intermediate **API Gateway** positioned between clients and downstream services. It handles request authentication, header normalization, and intercepts traffic to apply rate limits.

<img width="1000" height="1000" alt="image" src="https://github.com/user-attachments/assets/c2f36ccf-4169-4184-aace-def17adf0d54" />


### Request Flow
1. **Interception**: Every incoming HTTP request (except `/gateway/health`) is intercepted by [`RateLimiterFilter.java`](file:///Users/rohitmunde/Documents/2. Coding/Java Learning/Rate-limiter/backend/src/main/java/com/example/backend/filter/RateLimiterFilter.java).
2. **State Lookup**: A `RateLimitRequest` containing the client's IP, target path, and HTTP method is constructed. This object queries the active [`RateLimiterService`](file:///Users/rohitmunde/Documents/2. Coding/Java Learning/Rate-limiter/backend/src/main/java/com/example/backend/service/RateLimiterService.java) implementation.
3. **Evaluation**:
   - **Allowed**: The filter appends custom headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`) and forwards the request to [`GatewayProxyController.java`](file:///Users/rohitmunde/Documents/2. Coding/Java Learning/Rate-limiter/backend/src/main/java/com/example/backend/controller/GatewayProxyController.java).
   - **Blocked**: The filter halts execution immediately, returning `HTTP 429 Too Many Requests` along with a `Retry-After` header indicating how long the client must wait.
4. **Proxy Forwarding**: If allowed, `GatewayProxyController` acts as a transparent reverse proxy. It forwards the request downstream via a built-in `HttpClient`, handles response mapping, cleans hop-by-hop headers, and preserves client IP transparency by appending to the `X-Forwarded-For` header chain.

---

## 📊 Algorithm Comparison Table

| Algorithm | State Storage Overhead | Accuracy | Burst Handling | Use-Cases / Characteristics |
| :--- | :--- | :--- | :--- | :--- |
| **Token Bucket** | 🟢 Low (2 variables per client) | 🟡 Good (Refill interval based) | 🟢 Yes (Up to bucket capacity) | Default standard, great for APIs with bursty traffic |
| **Leaking Bucket** | 🟢 Low (2 variables per client) | 🟢 High (Strict queue/leak rate) | 🔴 No (Smoothens to constant rate) | Great for downstream services needing stable traffic flow |
| **Fixed Window** | 🟢 Low (Counter + start timestamp) | 🔴 Poor (Double-limit boundary burst) | 🟡 Limited (Can double traffic at edges) | Simple rate-limiting where strict window boundaries are acceptable |
| **Sliding Window Log** | 🔴 High (List of timestamps per client) | 🟢 Absolute (Extremely precise) | 🟢 Yes (Within sliding window duration) | High-security endpoints (e.g. login) where precision is mandatory |
| **Sliding Window Counter**| 🟢 Low (Current/prev window counters) | 🟡 Good (Approximate calculation) | 🟡 Moderate (Smoothed calculation) | Memory-efficient rate limiter with accurate sliding behaviour |

---

## ⚙️ Rate Limiting Algorithms

### 1. Token Bucket

#### How It Works
A bucket is initialized with a maximum capacity of tokens. Tokens are added to the bucket at a constant refill rate (e.g., $r$ tokens/sec). Each request consumes exactly 1 token. If the bucket has no tokens left, requests are blocked.

<img width="1554" height="1100" alt="image" src="https://github.com/user-attachments/assets/439e633a-9053-483c-8f7a-c466fe74f4c8" />

<img width="1554" height="1300" alt="image" src="https://github.com/user-attachments/assets/d099f943-7ee1-4e9f-8f08-6b60b92850eb" />



#### Java Implementation
- **Algorithm Class**: [`TokenBucket.java`](file:///Users/rohitmunde/Documents/2. Coding/Java Learning/Rate-limiter/backend/src/main/java/com/example/backend/service/algorithm/tokenbucket/TokenBucket.java)
- **Service Handler**: [`TokenBucketRateLimiterService.java`](file:///Users/rohitmunde/Documents/2. Coding/Java Learning/Rate-limiter/backend/src/main/java/com/example/backend/service/algorithm/tokenbucket/TokenBucketRateLimiterService.java)
- **Thread Safety**: Synchronized block on `refill()` and `tryConsume()`. Refill uses elapsed time calculation since `lastRefillTimestamp` rather than a background background timer thread, which is highly efficient.

```java
public synchronized boolean tryConsume() {
    refill();
    if (tokens >= 1) {
        tokens -= 1;
        return true;
    }
    return false;
}
```

---

### 2. Leaking Bucket

#### How It Works
Requests enter a FIFO queue (the bucket) of a fixed capacity. If the queue is full, incoming requests are dropped. Requests leak out of the bucket at a constant, stable rate. It acts as a traffic shaper, turning bursty traffic into a smooth, steady stream.

<img width="1618" height="918" alt="image" src="https://github.com/user-attachments/assets/a5d6d08a-8478-45a0-8e01-70e5b3cebf7f" />


#### Java Implementation
- **Algorithm Class**: [`LeakingBucket.java`](file:///Users/rohitmunde/Documents/2. Coding/Java Learning/Rate-limiter/backend/src/main/java/com/example/backend/service/algorithm/leakingbucket/LeakingBucket.java)
- **Service Handler**: [`LeakingBucketRateLimiterService.java`](file:///Users/rohitmunde/Documents/2. Coding/Java Learning/Rate-limiter/backend/src/main/java/com/example/backend/service/algorithm/leakingbucket/LeakingBucketRateLimiterService.java)
- **Mechanics**: Instead of an actual thread-backed queue, it is modeled mathematically using `waterLevel`. When a request arrives, the algorithm calculates how much "water" has leaked out since the last request based on `leakRatePerSecond`, adjusts the `waterLevel`, and adds $1$ to `waterLevel` if it does not exceed `capacity`.

---

### 3. Fixed Window Counter

#### How It Works
The timeline is divided into fixed-size windows (e.g., 1 minute). Each window maintains an independent counter. When a request arrives, the current window is determined, and its counter is incremented. If the counter exceeds the window capacity, the request is rejected.

![Fixed Window Counter Diagram](docs/images/fixed-window.png)

#### Java Implementation
- **Algorithm Class**: [`FixedWindow.java`](file:///Users/rohitmunde/Documents/2. Coding/Java Learning/Rate-limiter/backend/src/main/java/com/example/backend/service/algorithm/fixedwindow/FixedWindow.java)
- **Service Handler**: [`FixedWindowRateLimiterService.java`](file:///Users/rohitmunde/Documents/2. Coding/Java Learning/Rate-limiter/backend/src/main/java/com/example/backend/service/algorithm/fixedwindow/FixedWindowRateLimiterService.java)
- **The Boundary Problem**: Simple to implement but vulnerable to a burst of double the maximum capacity at the edges of window resets. For example, if the limit is 5 per minute, a client can send 5 requests at 0:59 and another 5 at 1:01, successfully passing 10 requests within a 2-second period.

---

### 4. Sliding Window Log

#### How It Works
To solve the boundary issue of the Fixed Window, the Sliding Window Log records the exact timestamp of every request. When a request arrives, it removes all timestamps older than the sliding window threshold (e.g., current time minus 1 minute). The remaining timestamps represent the current request count. If this count is within capacity, the new timestamp is logged and the request is allowed.

![Sliding Window Log Diagram](docs/images/sliding-window-log.png)

#### Java Implementation
- **Algorithm Class**: [`SlidingWindow.java`](file:///Users/rohitmunde/Documents/2. Coding/Java Learning/Rate-limiter/backend/src/main/java/com/example/backend/service/algorithm/slidingwindow/SlidingWindow.java)
- **Service Handler**: [`SlidingWindowRateLimiterService.java`](file:///Users/rohitmunde/Documents/2. Coding/Java Learning/Rate-limiter/backend/src/main/java/com/example/backend/service/algorithm/slidingwindow/SlidingWindowRateLimiterService.java)
- **Mechanics**: Implemented using a Java `TreeSet` (`NavigableSet<Long>`) to keep track of sorted Unix millisecond timestamps. The `TreeSet.headSet(windowStartTimeStamp, true).clear()` method is called to efficiently discard expired timestamps.
- **Drawback**: Significant memory usage, as every single request log is kept in memory.

---

### 5. Sliding Window Counter

#### How It Works
A hybrid approach combining Fixed Window Counter efficiency with Sliding Window precision. It calculates the request rate as a weighted average between the current window's request count and the previous window's request count.

Mathematical formulation:
$$\text{Estimated Count} = \text{Current Window Count} + \text{Previous Window Count} \times \frac{\text{Remaining Time in Current Window}}{\text{Window Size}}$$

![Sliding Window Counter Diagram](docs/images/sliding-window-counter.png)

#### Java Implementation
- **Algorithm Class**: [`SlidingWindowCounter.java`](file:///Users/rohitmunde/Documents/2. Coding/Java Learning/Rate-limiter/backend/src/main/java/com/example/backend/service/algorithm/slidingwindowcounter/SlidingWindowCounter.java)
- **Service Handler**: [`SlidingWindowCounterRateLimiterService.java`](file:///Users/rohitmunde/Documents/2. Coding/Java Learning/Rate-limiter/backend/src/main/java/com/example/backend/service/algorithm/slidingwindowcounter/SlidingWindowCounterRateLimiterService.java)
- **Mechanics**: Implemented using a mapping of window index to integer counts (`Map<Long, Integer>`). It calculates the elapsed percentage of the current window to scale the weight of the previous window. This is highly memory-efficient, requiring only counters instead of full logs of timestamps.

---

## 🛠️ Configuration Guide

The active algorithm and its corresponding parameters are configured inside [`backend/src/main/resources/application.properties`](file:///Users/rohitmunde/Documents/2. Coding/Java Learning/Rate-limiter/backend/src/main/resources/application.properties).

### Selecting the Algorithm
Toggle the active algorithm using the `rate-limiter.algorithm` property:
```properties
# Options: token-bucket, fixed-window, leaking-bucket, sliding-window, sliding-window-counter
rate-limiter.algorithm=sliding-window-counter
```

### Algorithm Specific Parameters

```properties
# 1. Token Bucket Properties
rate-limiter.token-bucket.capacity=10
rate-limiter.token-bucket.refill-rate-per-second=1.0

# 2. Leaking Bucket Properties
rate-limiter.leaking-bucket.capacity=5
rate-limiter.leaking-bucket.leaking-rate=1

# 3. Fixed Window Properties
rate-limiter.fixed-window.limit=5
rate-limiter.fixed-window.window-seconds=60

# 4. Sliding Window Log Properties
rate-limiter.sliding-window.limit=5
rate-limiter.sliding-window.window-seconds=60

# 5. Sliding Window Counter Properties
rate-limiter.sliding-window-counter.limit=5
rate-limiter.sliding-window-counter.window-seconds=60

# Downstream configuration (Gateway Routing target)
gateway.target-base-url=http://localhost:8080
```

---

## 🚀 Getting Started & Verification

### Prerequisites
- JDK 17 or higher
- Maven (or use the provided wrapper `./mvnw`)
- A downstream target service running (defaulting to `http://localhost:8080` in configuration) or you can spin up a simple dummy service.

### Run the Application
Start the gateway application:
```bash
cd backend
./mvnw spring-boot:run
```

The server starts by default on port `8081`.

### Verification

#### 1. Check Health Endpoint (Bypasses Rate Limiter)
The `/gateway/health` endpoint is explicitly white-listed and will never be rate-limited.
```bash
curl -i http://localhost:8081/gateway/health
```
**Expected Response**:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{"status":"UP","service":"rate-limiter-gateway"}
```

#### 2. Test Rate Limiting
To test rate limiting on actual proxied endpoints, run requests targeting any path matching `/**` (ensure you configure `gateway.target-base-url` to a running service or mock server, or observe the block behavior).

**Successful Request Response Headers**:
```http
HTTP/1.1 200 OK
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 4
...
```

**Rate-Limited Request (Exceeded Limit)**:
When the configured quota is exceeded, the request receives an immediate block:
```bash
curl -i http://localhost:8081/some-api-endpoint
```
**Expected Response**:
```http
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 0
Retry-After: 12
Content-Type: application/json

{"message": "Rate limit exceeded. Try again in 12 seconds."}
```
