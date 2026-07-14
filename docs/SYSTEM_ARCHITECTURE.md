# System Design & Architecture Analysis

Based on an analysis of the repository's structure and existing documentation, the project follows a **Clean Architecture** (Hexagonal Architecture) approach, functioning as a production-grade, event-driven notification platform. It heavily utilizes **Java 21**, **Spring Boot 3**, **Apache Kafka**, **Redis**, and **PostgreSQL**.

Below are the architectural and sequence diagrams (rendered dynamically using Mermaid) that illustrate the system's design. These are visually similar to Excalidraw-style flowcharts and are perfect for a GitHub repository.

## 1. High-Level System Architecture

This diagram shows how external upstream services publish events into Kafka, which the Notification Platform consumes, processes, and dispatches via various provider strategies.

```mermaid
flowchart TB
    subgraph Clients
        WebApp[Web / Mobile App]
    end

    subgraph "Upstream Services"
        OrderSvc[Order Service]
        PaymentSvc[Payment Service]
        SocialSvc[Social Service]
        AuthSvc[Auth Service]
    end

    subgraph "Kafka Cluster"
        EventTopics["events.* topics\n(8 business events)"]
        DeliveryFlow["notification.retry\nnotification.dlq"]
    end

    subgraph "Notification Platform (K8s Deployment, 3-15 pods)"
        API[REST API\nJWT + RBAC]
        WS[WebSocket Gateway]
        Consumers[Kafka Consumers\nevent fan-out]
        Scheduler[Delayed/Scheduled\nPoller]
        Dispatcher[Delivery Dispatcher\nStrategy Pattern]
    end

    subgraph Providers
        SES[AWS SES\nEmail]
        Twilio[Twilio\nSMS]
        FCM[Firebase FCM\nPush]
    end

    subgraph Storage
        Postgres[(PostgreSQL\nNotifications, Users,\nTemplates, Preferences)]
        Redis[(Redis\nCache + Pub/Sub)]
    end

    OrderSvc & PaymentSvc & SocialSvc & AuthSvc -->|publish events| EventTopics
    EventTopics --> Consumers
    Consumers --> Postgres
    Consumers --> Dispatcher
    Scheduler --> Postgres
    Scheduler --> Dispatcher
    Dispatcher --> SES & Twilio & FCM
    Dispatcher -->|WEBSOCKET channel| Redis
    Dispatcher -->|failure| DeliveryFlow
    DeliveryFlow --> Consumers

    Redis -->|Pub/Sub fanout| WS
    WS <-->|persistent connection| WebApp
    WebApp -->|REST calls| API
    API --> Postgres
    API --> Redis
    API -.JWT validated.-> WebApp
```

## 2. Clean Architecture Layering

The codebase is split into 5 distinct modules (api, application, infrastructure, domain, common). Dependencies point strictly inward.

```mermaid
flowchart LR
    subgraph "api (Spring Boot)"
        Controllers[Controllers]
        Security[JWT + RBAC]
        WSHandler[WebSocket Handler]
    end

    subgraph "infrastructure"
        JPA[JPA Repository Adapters]
        KafkaAdapters[Kafka Producers/Consumers]
        RedisAdapters[Redis Cache/PubSub]
        Providers[SES/Twilio/FCM Strategies]
    end

    subgraph "application"
        UseCases[Use Cases]
        Ports[Ports: Repository,\nProvider, Cache, Retry]
    end

    subgraph "domain (pure business logic)"
        Entities[Entities: Notification,\nUser, Template, Preference]
        RepoPorts[Repository Interfaces]
    end

    subgraph "common"
        Exceptions[Exception Hierarchy]
        Utils[BackoffCalculator, IdGenerator]
    end

    Controllers --> UseCases
    Security --> UseCases
    WSHandler --> RedisAdapters
    UseCases --> Ports
    UseCases --> Entities
    Ports -.implemented by.-> JPA
    Ports -.implemented by.-> KafkaAdapters
    Ports -.implemented by.-> RedisAdapters
    Ports -.implemented by.-> Providers
    JPA --> RepoPorts
    Entities --> Exceptions
    UseCases --> Utils

    style Entities fill:#e8f5e9,stroke:#333,stroke-width:2px
    style RepoPorts fill:#e8f5e9,stroke:#333,stroke-width:2px
    style UseCases fill:#e3f2fd,stroke:#333,stroke-width:2px
    style Ports fill:#e3f2fd,stroke:#333,stroke-width:2px
```

## 3. Real-Time WebSocket Fan-Out via Redis

Because the platform is designed to scale horizontally across many Kubernetes pods, sticky sessions aren't used. Instead, it relies on Redis Pub/Sub to deliver live WebSocket events to whichever pod happens to hold the active user session.

```mermaid
sequenceDiagram
    participant UserA as User's Browser
    participant PodA as API Pod A (holds WS session)
    participant PodB as API Pod B (processed the event)
    participant Redis
    participant DB as PostgreSQL

    UserA->>PodA: WS connect /ws/notifications?token=<jwt>
    PodA->>PodA: WebSocketSessionRegistry.register(userId, session)

    Note over PodB: Sometime later, an event arrives on Pod B's Kafka consumer
    PodB->>DB: save Notification (channel=WEBSOCKET)
    PodB->>PodB: WebSocketNotificationProvider.send(...)
    PodB->>Redis: PUBLISH notif:realtime:{userId} {payload}

    Redis->>PodA: message delivered (Pod A subscribed)
    Redis->>PodB: message also delivered locally (no-op)
    PodA->>PodA: WebSocketSessionRegistry.sendToUser
    PodA->>UserA: WS push: {notificationId, subject, body, ...}
```

## 4. Entity-Relationship (Database) Diagram

```mermaid
erDiagram
    USERS ||--o{ USER_ROLES : has
    USERS ||--o{ NOTIFICATIONS : receives
    USERS ||--o{ USER_PREFERENCES : configures
    NOTIFICATION_TEMPLATES ||..o{ NOTIFICATIONS : "rendered from"

    USERS {
        uuid id PK
        varchar email
        varchar fcm_device_token
    }

    NOTIFICATIONS {
        uuid id PK
        uuid user_id FK
        varchar channel
        varchar status
        int attempt_count
        boolean deleted
        varchar idempotency_key
    }

    USER_PREFERENCES {
        uuid id PK
        uuid user_id FK
        boolean quiet_hours_enabled
    }

    NOTIFICATION_TEMPLATES {
        uuid id PK
        varchar code
        varchar channel
        int version
    }
```
