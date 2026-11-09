-- Cleanup script: Remove all accounts except admin@gmail.com and prashanthannaldas453@gmail.com
-- and remove any foreign-key referencing rows related to the deleted accounts.

BEGIN;

-- 1. In technician_setup_tokens, delete tokens for users being removed
DELETE FROM technician_setup_tokens
WHERE user_id NOT IN (
    SELECT id FROM users WHERE email IN ('admin@gmail.com', 'prashanthannaldas453@gmail.com')
);

-- 2. In technician_access_requests:
-- Clear reviewed_by if reviewed by an account being removed
UPDATE technician_access_requests
SET reviewed_by = NULL
WHERE reviewed_by IS NOT NULL
  AND reviewed_by NOT IN (
    SELECT id FROM users WHERE email IN ('admin@gmail.com', 'prashanthannaldas453@gmail.com')
);

-- Delete requests not belonging to the retained accounts
DELETE FROM technician_access_requests
WHERE email NOT IN ('admin@gmail.com', 'prashanthannaldas453@gmail.com');

-- 3. In booking_history:
-- Delete history rows for bookings of users being removed
DELETE FROM booking_history
WHERE booking_id IN (
    SELECT id FROM bookings WHERE user_id NOT IN (
        SELECT id FROM users WHERE email IN ('admin@gmail.com', 'prashanthannaldas453@gmail.com')
    )
);

-- Delete history rows changed by users being removed
DELETE FROM booking_history
WHERE changed_by_id IS NOT NULL
  AND changed_by_id NOT IN (
    SELECT id FROM users WHERE email IN ('admin@gmail.com', 'prashanthannaldas453@gmail.com')
);

-- 4. In bookings:
-- Clear approver_id if approved by a user being removed
UPDATE bookings
SET approver_id = NULL
WHERE approver_id IS NOT NULL
  AND approver_id NOT IN (
    SELECT id FROM users WHERE email IN ('admin@gmail.com', 'prashanthannaldas453@gmail.com')
);

-- Delete bookings created by users being removed
DELETE FROM bookings
WHERE user_id NOT IN (
    SELECT id FROM users WHERE email IN ('admin@gmail.com', 'prashanthannaldas453@gmail.com')
);

-- 5. Delete all users except admin@gmail.com and prashanthannaldas453@gmail.com
DELETE FROM users
WHERE email NOT IN ('admin@gmail.com', 'prashanthannaldas453@gmail.com');

COMMIT;

-- Verify retained accounts
SELECT id, email, full_name, role, college_id FROM users;

