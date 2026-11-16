/**
 * The app's buttons, and links that look like buttons. Shared component.
 */
import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Link, type LinkProps } from 'react-router';
import styles from './Button.module.css';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';

interface StyleProps {
  variant?: Variant;
  size?: 'medium' | 'small';
  block?: boolean;
}

function classes({ variant = 'primary', size = 'medium', block = false }: StyleProps): string {
  return [styles.button, styles[variant], size === 'small' && styles.small, block && styles.block]
    .filter(Boolean)
    .join(' ');
}

interface ButtonProps extends StyleProps, ButtonHTMLAttributes<HTMLButtonElement> {
  /** Shows a spinner and disables the button while an action runs. */
  busy?: boolean;
  children: ReactNode;
}

export function Button({
  variant,
  size,
  block,
  busy = false,
  disabled,
  type = 'button',
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={classes({ variant, size, block })}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      {...rest}
    >
      {busy && <span className={styles.spinner} aria-hidden="true" />}
      {children}
    </button>
  );
}

interface ButtonLinkProps extends StyleProps, LinkProps {}

export function ButtonLink({ variant, size, block, ...rest }: ButtonLinkProps) {
  return <Link className={classes({ variant, size, block })} {...rest} />;
}
