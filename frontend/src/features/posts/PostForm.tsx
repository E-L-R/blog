/**
 * The title and content fields of a post, for writing a new one and for editing. Internal to
 * `posts`.
 *
 * The form keeps its own state, starting from `initial`. Pages that edit a post render it
 * with `key={post.id}`, so it starts over for a different post.
 */
import { useState, type FormEvent } from 'react';
import type { PostInput } from '@/api/types';
import { Alert } from '@/components/Alert';
import { Button, ButtonLink } from '@/components/Button';
import { TextAreaField, TextField } from '@/components/Field';
import { errorMessage, serverFieldErrors } from '@/lib/errors';
import {
  collectErrors,
  hasErrors,
  LIMITS,
  validatePostContent,
  validateTitle,
} from '@/lib/validation';
import styles from './PostForm.module.css';

const FIELDS = ['title', 'content'] as const;

interface PostFormProps {
  initial?: PostInput;
  submitLabel: string;
  cancelTo: string;
  busy: boolean;
  error: unknown;
  onSubmit: (input: PostInput) => void;
}

export function PostForm({ initial, submitLabel, cancelTo, busy, error, onSubmit }: PostFormProps) {
  const [title, setTitle] = useState(initial?.title ?? '');
  const [content, setContent] = useState(initial?.content ?? '');
  const [submitted, setSubmitted] = useState(false);

  const validate = () =>
    collectErrors({ title: validateTitle(title), content: validatePostContent(content) });
  const errors = { ...serverFieldErrors(error, FIELDS), ...(submitted ? validate() : {}) };
  const formError = error !== null && error !== undefined && !hasErrors(errors);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitted(true);
    if (hasErrors(validate())) return;
    onSubmit({ title: title.trim(), content });
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      {formError && <Alert>{errorMessage(error)}</Alert>}
      <TextField
        label="Title"
        hideLabel
        placeholder="Title"
        name="title"
        value={title}
        onChange={(event) => setTitle(event.target.value)}
        error={errors.title}
        maxLength={LIMITS.titleMax}
        controlClassName={styles.titleInput}
        autoFocus={initial === undefined}
      />
      <TextAreaField
        label="Content"
        hideLabel
        placeholder="Tell your story…"
        name="content"
        value={content}
        onChange={(event) => setContent(event.target.value)}
        error={errors.content}
        maxLength={LIMITS.postContentMax}
        controlClassName={styles.contentInput}
      />
      <div className={styles.footer}>
        <p className={styles.note}>
          Plain text: line breaks are kept. An AI summary is written after you publish.
        </p>
        <ButtonLink to={cancelTo} variant="ghost">
          Cancel
        </ButtonLink>
        <Button type="submit" busy={busy}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
