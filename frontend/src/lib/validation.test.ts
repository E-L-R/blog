import { describe, expect, it } from 'vitest';
import {
  collectErrors,
  hasErrors,
  utf8Length,
  validateEmail,
  validateNewPassword,
  validatePostContent,
  validateTitle,
  validateUsername,
} from './validation';

describe('validation rules (docs/API.md#validation)', () => {
  it('requires a plausible email of at most 254 characters', () => {
    expect(validateEmail('alice@example.com')).toBeNull();
    expect(validateEmail('')).not.toBeNull();
    expect(validateEmail('alice')).not.toBeNull();
    expect(validateEmail(`${'a'.repeat(243)}@example.com`)).not.toBeNull();
  });

  it('accepts usernames of 3 to 30 ASCII letters, digits, _ and -', () => {
    expect(validateUsername('ab_c-9')).toBeNull();
    expect(validateUsername('a'.repeat(30))).toBeNull();
    expect(validateUsername('ab')).not.toBeNull();
    expect(validateUsername('a'.repeat(31))).not.toBeNull();
    expect(validateUsername('with space')).not.toBeNull();
    expect(validateUsername('café')).not.toBeNull();
  });

  it('counts a new password in characters for the minimum and in UTF-8 bytes for the maximum', () => {
    expect(validateNewPassword('1234567')).not.toBeNull();
    expect(validateNewPassword('12345678')).toBeNull();
    expect(validateNewPassword('a'.repeat(72))).toBeNull();
    expect(validateNewPassword('a'.repeat(73))).not.toBeNull();
    // 24 characters, 3 bytes each: 72 bytes is fine, one more character is not.
    expect(validateNewPassword('€'.repeat(24))).toBeNull();
    expect(validateNewPassword('€'.repeat(25))).not.toBeNull();
  });

  it('requires a title of at most 200 characters that is not blank', () => {
    expect(validateTitle('Hello')).toBeNull();
    expect(validateTitle('   ')).not.toBeNull();
    expect(validateTitle('a'.repeat(200))).toBeNull();
    expect(validateTitle('a'.repeat(201))).not.toBeNull();
  });

  it('requires content of at most 50,000 characters that is not blank', () => {
    expect(validatePostContent('Text')).toBeNull();
    expect(validatePostContent('\n\n')).not.toBeNull();
    expect(validatePostContent('a'.repeat(50_001))).not.toBeNull();
  });

  it('counts UTF-8 bytes', () => {
    expect(utf8Length('a')).toBe(1);
    expect(utf8Length('é')).toBe(2);
    expect(utf8Length('€')).toBe(3);
    expect(utf8Length('😀')).toBe(4);
  });

  it('collects only the failed checks', () => {
    const errors = collectErrors({ title: null, content: 'Write something first.' });

    expect(errors).toEqual({ content: 'Write something first.' });
    expect(hasErrors(errors)).toBe(true);
    expect(hasErrors(collectErrors({ title: null }))).toBe(false);
  });
});
