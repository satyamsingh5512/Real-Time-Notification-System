-- V2__seed_default_templates.sql
-- Seed a default IN_APP + EMAIL template per event type so the platform is demoable
-- out of the box without requiring an admin to configure templates first.

INSERT INTO notification_templates (id, code, channel, version, subject_template, body_template, locale, active, created_at) VALUES
    (gen_random_uuid(), 'ORDER_PLACED', 'IN_APP', 1, NULL, 'Your order {{orderId}} has been placed successfully.', 'en-US', TRUE, now()),
    (gen_random_uuid(), 'ORDER_PLACED', 'EMAIL', 1, 'Your order is confirmed', 'Hi, your order {{orderId}} for {{amount}} has been placed. We will notify you once it ships.', 'en-US', TRUE, now()),
    (gen_random_uuid(), 'ORDER_DELIVERED', 'IN_APP', 1, NULL, 'Your order {{orderId}} has been delivered.', 'en-US', TRUE, now()),
    (gen_random_uuid(), 'ORDER_DELIVERED', 'SMS', 1, NULL, 'Your order {{orderId}} was delivered. Enjoy!', 'en-US', TRUE, now()),
    (gen_random_uuid(), 'PAYMENT_SUCCESS', 'IN_APP', 1, NULL, 'Payment of {{amount}} received for order {{orderId}}.', 'en-US', TRUE, now()),
    (gen_random_uuid(), 'PAYMENT_SUCCESS', 'EMAIL', 1, 'Payment received', 'We received your payment of {{amount}} for order {{orderId}}. Thank you!', 'en-US', TRUE, now()),
    (gen_random_uuid(), 'COMMENT_ADDED', 'IN_APP', 1, NULL, '{{actorName}} commented on your post: "{{commentText}}"', 'en-US', TRUE, now()),
    (gen_random_uuid(), 'LIKE_RECEIVED', 'IN_APP', 1, NULL, '{{actorName}} liked your post.', 'en-US', TRUE, now()),
    (gen_random_uuid(), 'MENTIONED', 'IN_APP', 1, NULL, '{{actorName}} mentioned you in a post.', 'en-US', TRUE, now()),
    (gen_random_uuid(), 'PASSWORD_RESET', 'EMAIL', 1, 'Reset your password', 'Click the link to reset your password: {{resetLink}}. This link expires in 15 minutes.', 'en-US', TRUE, now()),
    (gen_random_uuid(), 'PASSWORD_RESET', 'SMS', 1, NULL, 'Your password reset code is {{otp}}. Valid for 15 minutes.', 'en-US', TRUE, now()),
    (gen_random_uuid(), 'OTP_GENERATED', 'SMS', 1, NULL, 'Your verification code is {{otp}}. Do not share this with anyone.', 'en-US', TRUE, now()),
    (gen_random_uuid(), 'OTP_GENERATED', 'EMAIL', 1, 'Your verification code', 'Your one-time verification code is {{otp}}. It expires in 5 minutes.', 'en-US', TRUE, now());
