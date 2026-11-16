/**
 * The frame around every page: the header with the navigation, and the footer. Also shows a
 * one-off notice that a page can pass when it navigates, such as "Post deleted."
 */
import { Link, Outlet, ScrollRestoration, useLocation } from 'react-router';
import { Alert } from '@/components/Alert';
import { Avatar } from '@/components/Avatar';
import { Button, ButtonLink } from '@/components/Button';
import { useAuth } from '@/features/auth';
import styles from './Layout.module.css';

export function Layout() {
  const location = useLocation();
  const notice = (location.state as { notice?: string } | null)?.notice;

  return (
    <>
      <a href="#main" className={styles.skip}>
        Skip to content
      </a>
      <header className={styles.header}>
        <div className={styles.bar}>
          <Link to="/" className={styles.brand}>
            <Logo />
            Blog
          </Link>
          <Navigation />
        </div>
      </header>
      <main id="main" className={styles.main}>
        {notice && (
          <div className={`reading ${styles.notice}`}>
            <Alert tone="success">{notice}</Alert>
          </div>
        )}
        <Outlet />
      </main>
      <footer className={styles.footer}>Posts are plain text. Summaries are written by AI.</footer>
      <ScrollRestoration />
    </>
  );
}

function Navigation() {
  const { state, logOut } = useAuth();

  if (state.status === 'loading') return <nav className={styles.nav} aria-label="Account" />;

  if (state.status === 'anonymous') {
    return (
      <nav className={styles.nav} aria-label="Account">
        <ButtonLink to="/login" variant="ghost">
          Log in
        </ButtonLink>
        <ButtonLink to="/register">Sign up</ButtonLink>
      </nav>
    );
  }

  return (
    <nav className={styles.nav} aria-label="Account">
      <ButtonLink to="/posts/new">
        <span aria-hidden="true">✎</span> Write
      </ButtonLink>
      <Link
        to="/account"
        className={styles.account}
        aria-label={`Your account (${state.me.username})`}
      >
        <Avatar author={{ username: state.me.username, bot: false }} size={30} />
        <span className={styles.username}>{state.me.username}</span>
      </Link>
      <Button variant="ghost" onClick={logOut}>
        Log out
      </Button>
    </nav>
  );
}

function Logo() {
  return (
    <svg className={styles.logo} viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="8" fill="var(--accent)" />
      <path
        d="M11 8h5.5a4 4 0 0 1 0 8H11zM11 16h6.5a4 4 0 0 1 0 8H11z"
        fill="none"
        stroke="var(--on-accent)"
        strokeWidth="2.6"
        strokeLinejoin="round"
      />
    </svg>
  );
}
