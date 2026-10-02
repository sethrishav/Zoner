/**
 * Mapping of backend ApiError codes to clear, friendly user-facing messages.
 */
const ERROR_CODE_MESSAGES = {
  BAD_CREDENTIALS: 'The email or password you entered is incorrect.',
  DUPLICATE_EMAIL: 'An account with this email address already exists.',
  UNAUTHORIZED: 'Your session has expired. Please log in again.',
  FORBIDDEN: 'You do not have permission to perform this action.',
  NOT_FOUND: 'The requested item was not found or is no longer accessible.',
  CONFLICT: 'This action conflicts with existing data.',
  OPTIMISTIC_LOCK_CONFLICT: 'This event was modified in another session. Please reload to see the latest changes.',
  VALIDATION_FAILED: 'Please verify the submitted information.',
  INVALID_RECURRENCE_RULE: 'The recurrence rule provided is invalid.',
  SYSTEM_ERROR: 'Something went wrong on our end. Please try again shortly.',
};

/**
 * Extracts a friendly error message from an ApiError response or standard Error.
 * @param {any} error
 * @returns {string}
 */
export function getErrorMessage(error) {
  if (!error) return 'An unexpected error occurred.';

  // If server returned an ApiError object with code and message
  if (error.code && ERROR_CODE_MESSAGES[error.code]) {
    return ERROR_CODE_MESSAGES[error.code];
  }

  if (error.message) {
    return error.message;
  }

  if (typeof error === 'string') {
    return error;
  }

  return 'An unexpected error occurred. Please try again.';
}
