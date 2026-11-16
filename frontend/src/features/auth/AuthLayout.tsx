/**
 * The centred card that the login and registration pages share. Internal to `auth`.
 */
import type { FormEvent, ReactNode } from 'react';
import { PageTitle } from '@/components/PageTitle';
import styles from './AuthLayout.module.css';

interface AuthLayoutProps {
  title: string;
  subtitle: string;
  footer: ReactNode;
  children: ReactNode;
}

export function AuthLayout({ title, subtitle, footer, children }: AuthLayoutProps) {
  return (
    <div className={styles.page}>
      <PageTitle>{title}</PageTitle>
      <section className={styles.card}>
        <header className={styles.heading}>
          <h1 className={styles.title}>{title}</h1>
          <p className={styles.subtitle}>{subtitle}</p>
        </header>
        {children}
        <p className={styles.footer}>{footer}</p>
      </section>
    </div>
  );
}

/** A form that leaves validation to the app: `noValidate` turns off the browser's own bubbles. */
export function AuthForm({
  onSubmit,
  children,
}: {
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  children: ReactNode;
}) {
  return (
    <form className={styles.form} onSubmit={onSubmit} noValidate>
      {children}
    </form>
  );
}
