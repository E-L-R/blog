/**
 * Labelled text inputs and textareas, with an optional hint, character counter and error
 * message. Shared components.
 *
 * The error is linked to the control with `aria-describedby`, and the control is marked
 * `aria-invalid`, so screen readers announce both.
 */
import {
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type TextareaHTMLAttributes,
} from 'react';
import styles from './Field.module.css';

interface FieldProps {
  label: string;
  error?: string;
  hint?: string;
  /** Shows "used / max" once the value passes 80% of this many characters. */
  maxLength?: number;
  /** Hides the label visually; screen readers still read it. */
  hideLabel?: boolean;
  /** Extra class names for the control, so a page can restyle it. */
  controlClassName?: string;
}

type ControlAttributes = {
  id: string;
  className: string;
  'aria-invalid': true | undefined;
  'aria-describedby': string | undefined;
};

export function TextField({
  label,
  error,
  hint,
  maxLength,
  hideLabel,
  controlClassName,
  ...inputProps
}: FieldProps & Omit<InputHTMLAttributes<HTMLInputElement>, 'maxLength'>) {
  return (
    <FieldShell
      {...{ label, error, hint, maxLength, hideLabel, controlClassName }}
      value={inputProps.value}
    >
      {(attributes) => <input {...inputProps} {...attributes} />}
    </FieldShell>
  );
}

export function TextAreaField({
  label,
  error,
  hint,
  maxLength,
  hideLabel,
  controlClassName,
  ...textareaProps
}: FieldProps & Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'maxLength'>) {
  return (
    <FieldShell
      {...{ label, error, hint, maxLength, hideLabel, controlClassName }}
      value={textareaProps.value}
    >
      {(attributes) => <textarea {...textareaProps} {...attributes} />}
    </FieldShell>
  );
}

function FieldShell({
  label,
  error,
  hint,
  maxLength,
  hideLabel,
  controlClassName,
  value,
  children,
}: FieldProps & { value: unknown; children: (attributes: ControlAttributes) => ReactNode }) {
  const id = useId();
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const length = typeof value === 'string' ? value.length : 0;
  // The counter only appears near the limit, where it is useful. The limit is not enforced
  // with the maxLength attribute, so pasting a long text and then trimming it still works.
  const showCounter = maxLength !== undefined && length > maxLength * 0.8;

  return (
    <div className={styles.field}>
      <div className={styles.labelRow}>
        <label htmlFor={id} className={hideLabel ? 'visually-hidden' : styles.label}>
          {label}
        </label>
        {showCounter && (
          <span
            className={[styles.counter, length > maxLength && styles.counterOver]
              .filter(Boolean)
              .join(' ')}
          >
            {length.toLocaleString('en')} / {maxLength.toLocaleString('en')}
          </span>
        )}
      </div>
      {children({
        id,
        className: [styles.control, controlClassName].filter(Boolean).join(' '),
        'aria-invalid': error ? true : undefined,
        'aria-describedby': [errorId, hintId].filter(Boolean).join(' ') || undefined,
      })}
      {error && (
        <p id={errorId} className={styles.error}>
          {error}
        </p>
      )}
      {hint && !error && (
        <p id={hintId} className={styles.hint}>
          {hint}
        </p>
      )}
    </div>
  );
}
