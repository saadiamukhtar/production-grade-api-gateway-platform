# Gateflow API Gateway

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/SpringBoot-3.x-brightgreen)
![Spring Cloud Gateway](https://img.shields.io/badge/SpringCloud-Gateway-blue)
![Redis](https://img.shields.io/badge/Redis-RateLimiter-red)
![Docker](https://img.shields.io/badge/Docker-Ready-blue)
![License](https://img.shields.io/badge/License-MIT-green)

## Features

- Distributed rate limiting using Redis
- Sliding window algorithm
- Fail-open resilience strategy
- Reactive architecture
- Micrometer metrics
- Configurable rate limits
- Docker deployment

---

## System Architecture

```
                +-------------+
                |   Client    |
                +-------------+
                       |
                       v
            +----------------------+
            |  Gateflow API Gateway |
            +----------------------+
               |         |        |
               v         v        v
        +---------+ +---------+ +---------+
        | Service | | Service | | Service |
        |    A    | |    B    | |    C    |
        +---------+ +---------+ +---------+

                |
                v
          +-------------+
          |    Redis    |
          | RateLimiter |
          +-------------+
```

Client → Gateway → Services  
Gateway → Redis

---

## Request Flow

1. Client sends request to the API Gateway
2. Gateway intercepts the request using global filters
3. Rate limiter checks request count in Redis
4. If request limit exceeded → return **HTTP 429 Too Many Requests**
5. If allowed → gateway forwards request to backend service
6. Response is returned back to the client

---

## Quick Start

### Run Redis

```bash
docker run -p 6379:6379 redis
```

### Run Gateway

```bash
docker run -p 8080:8080 gateflow/api-gateway
```

---

## Example Request

Open in browser:

```
http://localhost:8080/users/test
```

Or use curl:

```bash
curl http://localhost:8080/users/test
```

Expected response:

```
User Service Working
```

---

## Docker Deployment

### Build Docker Image

```bash
docker build -t gateflow/api-gateway .
```

### Run Gateway Container

```bash
docker run -p 8080:8080 gateflow/api-gateway
```

Gateway will start on:

```
http://localhost:8080
```

---

## Docker Compose Deployment (Recommended)

You can start **Redis and the Gateway together** using Docker Compose.

Create a file named **docker-compose.yml**

```yaml
version: "3.8"

services:

  redis:
    image: redis:7
    container_name: gateflow-redis
    ports:
      - "6379:6379"

  api-gateway:
    build: .
    container_name: gateflow-gateway
    ports:
      - "8080:8080"
    depends_on:
      - redis
```

Run both services:

```bash
docker compose up
```

This will start:

- Redis on **localhost:6379**
- Gateway on **localhost:8080**

---

## Metrics

Gateflow exposes metrics using **Micrometer**.

Example metrics:

```
gateway.requests.total
gateway.rate_limit.exceeded
```

These metrics can be integrated with monitoring systems such as:

- Prometheus
- Grafana

---

## Future Improvements

- API key authentication
- per-user rate limiting
- dynamic route configuration
- Prometheus monitoring
- Grafana dashboards
- circuit breaker support

---

## License

MIT License