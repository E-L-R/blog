import { describe, expect, it } from 'vitest';
import { ApiError } from '@/api/client';
import { conflictField, errorMessage, isNotFound, serverFieldErrors } from './errors';

describe('error messages', () => {
  it('picks the message by code, not by the server text', () => {
    const error = new ApiError(409, 'EDIT_CONFLICT', 'some server wording');

    expect(errorMessage(error)).toMatch(/at the same moment/);
  });

  it('falls back to a general message for unknown codes and other errors', () => {
    expect(errorMessage(new ApiError(500, 'INTERNAL_ERROR', ''))).toMatch(/something went wrong/i);
    expect(errorMessage(new Error('boom'))).toMatch(/something went wrong/i);
  });

  it('keeps the first server error of each field the form shows', () => {
    const error = new ApiError(400, 'VALIDATION_FAILED', '', [
      { field: 'password', message: 'must be at least 8 characters' },
      { field: 'password', message: 'must be at most 72 bytes' },
      { field: 'other', message: 'ignored' },
    ]);

    expect(serverFieldErrors(error, ['email', 'password'])).toEqual({
      password: 'Must be at least 8 characters.',
    });
  });

  it('knows which field a conflict is about', () => {
    expect(conflictField(new ApiError(409, 'EMAIL_TAKEN', ''))).toBe('email');
    expect(conflictField(new ApiError(409, 'USERNAME_TAKEN', ''))).toBe('username');
    expect(conflictField(new ApiError(409, 'EDIT_CONFLICT', ''))).toBeNull();
  });

  it('recognises 404', () => {
    expect(isNotFound(new ApiError(404, 'NOT_FOUND', ''))).toBe(true);
    expect(isNotFound(new ApiError(403, 'NOT_AUTHOR', ''))).toBe(false);
  });
});
