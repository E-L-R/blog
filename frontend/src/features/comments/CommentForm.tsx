/**
 * The text box for a new comment, a reply, or an edit. Internal to `comments`.
 *
 * It keeps its own text, starting from `initial`, and clears it after a successful submit.
 */
import { useState, type FormEvent } from 'react';
import { Alert } from '@/components/Alert';
import { Button } from '@/components/Button';
import { TextAreaField } from '@/components/Field';
import { errorMessage, serverFieldErrors } from '@/lib/errors';
import { LIMITS, validateCommentContent } from '@/lib/validation';
import styles from './Comments.module.css';

interface CommentFormProps {
  label: string;
  submitLabel: string;
  initial?: string;
  placeholder?: string;
  busy: boolean;
  error: unknown;
  /** Resolves when the comment is saved; the form then clears itself. */
  onSubmit: (content: string) => Promise<unknown>;
  onCancel?: () => void;
  autoFocus?: boolean;
}

export function CommentForm({
  label,
  submitLabel,
  initial = '',
  placeholder,
  busy,
  error,
  onSubmit,
  onCancel,
  autoFocus,
}: CommentFormProps) {
  const [content, setContent] = useState(initial);
  const [submitted, setSubmitted] = useState(false);

  const fieldError =
    (submitted ? validateCommentContent(content) : null) ??
    serverFieldErrors(error, ['content'] as const).content;
  const formError = error !== null && error !== undefined && fieldError === undefined;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitted(true);
    if (validateCommentContent(content) !== null) return;
    try {
      await onSubmit(content);
      setContent('');
      setSubmitted(false);
    } catch {
      // The mutation keeps the error, and the form shows it.
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      {formError && <Alert>{errorMessage(error)}</Alert>}
      <TextAreaField
        label={label}
        hideLabel
        placeholder={placeholder}
        value={content}
        onChange={(event) => setContent(event.target.value)}
        error={fieldError ?? undefined}
        maxLength={LIMITS.commentContentMax}
        controlClassName={styles.textarea}
        autoFocus={autoFocus}
      />
      <div className={styles.formActions}>
        {onCancel && (
          <Button variant="ghost" size="small" onClick={onCancel}>
            Cancel
          </Button>
        )}
        <Button type="submit" size="small" busy={busy}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
