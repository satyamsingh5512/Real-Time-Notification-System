"""
Kafka event-ingestion load test.

Publishes synthetic business events (OrderPlaced, PaymentSuccess, CommentAdded, etc.)
directly onto the Kafka topics the platform consumes, at a configurable rate, to measure
end-to-end fan-out latency (event published -> Notification row visible via REST API)
and consumer-group throughput under load. Complements the k6 REST API load test, which
only exercises read-path endpoints.

Requires: pip install kafka-python

Usage:
    python load-testing/kafka_event_producer_load_test.py \
        --bootstrap-servers localhost:29092 \
        --events-per-second 200 \
        --duration-seconds 120
"""
import argparse
import json
import random
import time
import uuid
from datetime import datetime, timezone

from kafka import KafkaProducer

TOPIC_BY_EVENT_TYPE = {
    "ORDER_PLACED": "events.order.placed",
    "ORDER_DELIVERED": "events.order.delivered",
    "PAYMENT_SUCCESS": "events.payment.success",
    "COMMENT_ADDED": "events.social.comment-added",
    "LIKE_RECEIVED": "events.social.like-received",
    "MENTIONED": "events.social.mentioned",
    "PASSWORD_RESET": "events.auth.password-reset",
    "OTP_GENERATED": "events.auth.otp-generated",
}

SAMPLE_ATTRIBUTES = {
    "ORDER_PLACED": lambda: {"orderId": str(uuid.uuid4())[:8], "amount": f"${random.randint(10, 500)}.00"},
    "ORDER_DELIVERED": lambda: {"orderId": str(uuid.uuid4())[:8]},
    "PAYMENT_SUCCESS": lambda: {"orderId": str(uuid.uuid4())[:8], "amount": f"${random.randint(10, 500)}.00"},
    "COMMENT_ADDED": lambda: {"actorName": "LoadTestBot", "commentText": "Nice post!"},
    "LIKE_RECEIVED": lambda: {"actorName": "LoadTestBot"},
    "MENTIONED": lambda: {"actorName": "LoadTestBot"},
    "PASSWORD_RESET": lambda: {"resetLink": "https://example.com/reset/" + str(uuid.uuid4())},
    "OTP_GENERATED": lambda: {"otp": str(random.randint(100000, 999999))},
}


def build_event(event_type: str, user_id: str) -> dict:
    return {
        "eventId": str(uuid.uuid4()),
        "eventType": event_type,
        "userId": user_id,
        "attributes": SAMPLE_ATTRIBUTES[event_type](),
        "occurredAt": datetime.now(timezone.utc).isoformat(),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--bootstrap-servers", default="localhost:29092")
    parser.add_argument("--events-per-second", type=int, default=100)
    parser.add_argument("--duration-seconds", type=int, default=60)
    parser.add_argument("--user-pool-size", type=int, default=100,
                         help="Number of distinct userIds to spread events across (should match seeded test users).")
    args = parser.parse_args()

    producer = KafkaProducer(
        bootstrap_servers=args.bootstrap_servers,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        acks="all",
        retries=3,
    )

    user_pool = [str(uuid.uuid4()) for _ in range(args.user_pool_size)]
    event_types = list(TOPIC_BY_EVENT_TYPE.keys())

    interval = 1.0 / args.events_per_second
    end_time = time.time() + args.duration_seconds
    sent = 0

    print(f"Publishing ~{args.events_per_second} events/sec for {args.duration_seconds}s "
          f"to {args.bootstrap_servers}...")

    while time.time() < end_time:
        event_type = random.choice(event_types)
        topic = TOPIC_BY_EVENT_TYPE[event_type]
        user_id = random.choice(user_pool)
        event = build_event(event_type, user_id)

        producer.send(topic, key=event["eventId"].encode("utf-8"), value=event)
        sent += 1
        if sent % 500 == 0:
            print(f"  sent {sent} events...")
        time.sleep(interval)

    producer.flush()
    print(f"Done. Published {sent} events total.")


if __name__ == "__main__":
    main()
