-- Expand token_prefix to VARCHAR(32) for future flexibility
ALTER TABLE personal_access_tokens ALTER COLUMN token_prefix TYPE VARCHAR(32);
