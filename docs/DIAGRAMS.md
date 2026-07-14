# Architecture & Sequence Diagrams

All diagrams are written in [Mermaid](https://mermaid.js.org/) syntax. GitHub, GitLab, and most
modern Markdown viewers render Mermaid blocks natively; to render manually use the
[Mermaid Live Editor](https://mermaid.live).

## 1. High-Level System Architecture

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

**Why this shape:** ingestion (Kafka consumers) and delivery (Dispatcher/provider strategies) are
decoupled from the REST/WebSocket read path. A slow SES/Twilio/FCM call never blocks a user
checking their inbox, and the API tier can scale independently of consumer throughput.

## 2. Clean Architecture Layering

```mermaid
flowchart LR
    subgraph "api (Spring Boot, HTTP/WebSocket boundary)"
        Controllers[Controllers]
        Security[JWT + RBAC]
        WSHandler[WebSocket Handler]
    end

    subgraph "infrastructure (framework adapters)"
        JPA[JPA Repository Adapters]
        KafkaAdapters[Kafka Producers/Consumers]
        RedisAdapters[Redis Cache/PubSub]
        Providers[SES/Twilio/FCM Strategies]
    end

    subgraph "application (use cases, framework-agnostic)"
        UseCases[Use Cases]
        Ports[Ports: Repository,\nProvider, Cache, Retry]
    end

    subgraph "domain (pure business logic, zero deps)"
        Entities[Entities: Notification,\nUser, Template, Preference]
        RepoPorts[Repository Interfaces]
    end

    subgraph "common (shared kernel)"
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

    style Entities fill:#e8f5e9
    style RepoPorts fill:#e8f5e9
    style UseCases fill:#e3f2fd
    style Ports fill:#e3f2fd
```

**Dependency rule:** arrows only point inward. `domain` has zero framework dependencies (no
Spring, no JPA annotations); `application` depends only on `domain`+`common` and defines ports
that `infrastructure` implements. This is enforced structurally by the Gradle module graph, not
just convention — `domain`'s `build.gradle.kts` doesn't even have Spring on its classpath, so a
violation is a compile error, not a code review nit.

## 3. Sequence: Event-Driven Notification Delivery (e.g. OrderPlaced)

```mermaid
sequenceDiagram
    participant Order as Order Service
    participant Kafka as Kafka (events.order.placed)
    participant Consumer as NotificationEventConsumer
    participant UseCase as ProcessIncomingEventUseCase
    participant DB as PostgreSQL
    participant Deliver as DeliverNotificationUseCase
    participant Provider as SES/FCM/Twilio Strategy
    participant Redis as Redis Pub/Sub

    Order->>Kafka: publish OrderPlaced event
    Kafka->>Consumer: @KafkaListener delivers message
    Consumer->>UseCase: execute(DomainEvent)
    UseCase->>DB: check idempotency key
    UseCase->>DB: load UserPreference (opt-in/quiet hours)
    UseCase->>DB: save Notification (per eligible channel)
    UseCase-->>Consumer: List<Notification>

    loop for each notification
        Consumer->>Deliver: execute(notification)
        Deliver->>DB: mark PROCESSING, render template
        Deliver->>Provider: send(notification, recipient)
        alt success
            Provider-->>Deliver: OK
            Deliver->>DB: mark SENT
        else transient failure
            Provider-->>Deliver: NotificationDeliveryException(retryable=true)
            Deliver->>DB: mark RETRYING
            Deliver->>Kafka: publish to notification.retry (backoff delay)
        else permanent failure / attempts exhausted
            Deliver->>DB: mark DEAD_LETTERED
            Deliver->>Kafka: publish to notification.dlq
        end
    end

    Note over Provider,Redis: For WEBSOCKET channel, "send" = publish to Redis Pub/Sub
    Provider->>Redis: PUBLISH notif:realtime:{userId}
    Redis->>Redis: any pod subscribed to notif:realtime:* receives it
```

## 4. Sequence: Retry with Exponential Backoff + Dead Letter Queue

```mermaid
sequenceDiagram
    participant Deliver as DeliverNotificationUseCase
    participant Backoff as BackoffCalculator
    participant RetryTopic as Kafka: notification.retry
    participant RetryConsumer as RetryTopicConsumer
    participant DLQ as Kafka: notification.dlq
    participant DLQConsumer as DeadLetterTopicConsumer

    Deliver->>Deliver: attempt 1 fails (transient)
    Deliver->>Backoff: computeDelay(attempt=1, base=5s, cap=30m)
    Backoff-->>Deliver: delay ≈ 2-5s (full jitter)
    Deliver->>RetryTopic: publish {notificationId, notBefore}

    RetryTopic->>RetryConsumer: consume message
    RetryConsumer->>RetryConsumer: wait until notBefore elapsed
    RetryConsumer->>Deliver: execute(notification) [attempt 2]

    Deliver->>Deliver: attempt 2 fails again
    Deliver->>Backoff: computeDelay(attempt=2, ...)
    Backoff-->>Deliver: delay ≈ 4-10s
    Deliver->>RetryTopic: publish retry message

    Note over Deliver: ... repeats up to maxAttempts (default 5) ...

    Deliver->>Deliver: attempt 5 fails, canRetry() == false
    Deliver->>DLQ: publish {notificationId, reason: "Max attempts exhausted"}
    DLQ->>DLQConsumer: consume for alerting/audit
    DLQConsumer->>DLQConsumer: log.error + (future) page on-call
```

## 5. Sequence: JWT Authentication + RBAC

```mermaid
sequenceDiagram
    participant Client
    participant AuthController
    participant AuthUseCase as AuthenticateUserUseCase
    participant Hasher as BCryptPasswordHasher
    participant DB as PostgreSQL
    participant JwtService

    Client->>AuthController: POST /api/v1/auth/login {email, password}
    AuthController->>AuthUseCase: authenticate(email, password)
    AuthUseCase->>DB: findByEmail(email)
    DB-->>AuthUseCase: User (with roles, passwordHash)
    AuthUseCase->>Hasher: matches(rawPassword, hash)
    Hasher-->>AuthUseCase: true
    AuthUseCase-->>AuthController: User
    AuthController->>JwtService: generateToken(user)
    JwtService-->>AuthController: signed JWT (sub=userId, roles claim)
    AuthController-->>Client: 200 {token, userId, roles}

    Client->>AuthController: GET /api/v1/admin/templates (Authorization: Bearer <jwt>)
    Note over AuthController: JwtAuthenticationFilter runs first
    AuthController->>JwtService: parseToken(jwt)
    JwtService-->>AuthController: AuthenticatedPrincipal{userId, roles}
    Note over AuthController: SecurityFilterChain checks hasRole('ADMIN')
    alt role matches
        AuthController-->>Client: 200 OK
    else role missing
        AuthController-->>Client: 403 Forbidden
    end
```

## 6. Sequence: Real-Time WebSocket Delivery Across Pods

```mermaid
sequenceDiagram
    participant UserA as User's Browser
    participant PodA as API Pod A (holds WS session)
    participant PodB as API Pod B (processed the event)
    participant Redis
    participant DB as PostgreSQL

    UserA->>PodA: WS connect /ws/notifications?token=<jwt>
    PodA->>PodA: WebSocketAuthInterceptor validates JWT
    PodA->>PodA: WebSocketSessionRegistry.register(userId, session)

    Note over PodB: Some time later, a LikeReceived event arrives on Pod B's Kafka consumer
    PodB->>DB: save Notification (channel=WEBSOCKET)
    PodB->>PodB: WebSocketNotificationProvider.send(...)
    PodB->>Redis: PUBLISH notif:realtime:{userId} {payload}

    Redis->>PodA: message delivered (Pod A subscribed to notif:realtime:*)
    Redis->>PodB: message also delivered to itself (no-op, no local session)
    PodA->>PodA: RedisRealtimeSubscriber -> WebSocketSessionRegistry.sendToUser
    PodA->>UserA: WS push: {notificationId, subject, body, ...}
```

**Why Redis Pub/Sub instead of sticky sessions:** the pod that processes a Kafka event is
essentially random relative to which pod holds a given user's live WebSocket connection.
Rather than requiring sticky load-balancer routing (which complicates rolling deploys and
autoscaling), every pod subscribes to the same Pub/Sub pattern and simply no-ops for users it
doesn't hold locally.

## 7. Entity-Relationship Diagram

```mermaid
erDiagram
    USERS ||--o{ USER_ROLES : has
    USERS ||--o{ NOTIFICATIONS : receives
    USERS ||--o{ USER_PREFERENCES : configures
    NOTIFICATION_TEMPLATES ||..o{ NOTIFICATIONS : "rendered from (by code+channel)"

    USERS {
        uuid id PK
        varchar email
        varchar password_hash
        varchar display_name
        varchar phone_number
        varchar fcm_device_token
        boolean enabled
        timestamptz created_at
        timestamptz updated_at
    }

    USER_ROLES {
        uuid user_id FK
        varchar role
    }

    NOTIFICATIONS {
        uuid id PK
        uuid user_id FK
        varchar event_type
        varchar channel
        varchar template_code
        jsonb payload
        varchar rendered_subject
        text rendered_body
        varchar status
        int attempt_count
        int max_attempts
        text last_error_message
        timestamptz scheduled_for
        timestamptz sent_at
        timestamptz read_at
        boolean deleted
        varchar idempotency_key
        bigint version
    }

    USER_PREFERENCES {
        uuid id PK
        uuid user_id FK
        varchar event_type
        jsonb channel_opt_in
        boolean quiet_hours_enabled
        int quiet_hours_start
        int quiet_hours_end
    }

    NOTIFICATION_TEMPLATES {
        uuid id PK
        varchar code
        varchar channel
        int version
        varchar subject_template
        text body_template
        varchar locale
        boolean active
    }
```
