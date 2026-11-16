/**
 * Deletes the current user's account, after they confirm with their password. Internal to
 * `account`.
 *
 * Deletion works the way Reddit does it (docs/DOMAIN.md#flows): posts and comments stay, shown
 * as "[deleted]", and the username is retired for good. The text says so before the user
 * commits, because it cannot be undone.
 */
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { ApiError } from '@/api/client';
import { Alert } from '@/components/Alert';
import { Button } from '@/components/Button';
import { TextField } from '@/components/Field';
import { useAuth } from '@/features/auth';
import { errorMessage } from '@/lib/errors';
import { validateRequired } from '@/lib/validation';
import { deleteAccount } from './api';
import styles from './Profile.module.css';

export function DeleteAccountForm() {
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const { logOut } = useAuth();
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const mutation = useMutation({
    mutationFn: deleteAccount,
    onSuccess: async () => {
      // Leave the page before logging out: on /account, RequireAuth would otherwise answer
      // the logout by sending the user to the login page. `flushSync` makes the new page
      // render before the token goes.
      await navigate('/', {
        replace: true,
        flushSync: true,
        state: { notice: 'Your account was deleted.' },
      });
      logOut();
      // Every cached post by this account now has a "[deleted]" author.
      await queryClient.invalidateQueries();
    },
  });

  const error = submitted ? validateRequired(password, 'your password') : null;
  const serverError = mutation.isError ? errorMessage(mutation.error) : null;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitted(true);
    if (validateRequired(password, 'your password') !== null) return;
    mutation.mutate(password);
  }

  return (
    <section className={styles.danger} aria-labelledby="delete-account-title">
      <h2 id="delete-account-title" className={styles.dangerTitle}>
        Delete account
      </h2>
      <div className={styles.dangerText}>
        <p>This can't be undone.</p>
        <ul>
          <li>Your email and password are erased, and you can't log in again.</li>
          <li>
            Your posts and comments stay, with the author shown as “[deleted]”. Delete them first if
            you want them gone.
          </li>
          <li>Your username is retired: nobody can register it again, including you.</li>
        </ul>
      </div>
      {open ? (
        <form className={styles.dangerForm} onSubmit={handleSubmit} noValidate>
          {serverError && !isPasswordError(mutation.error) && <Alert>{serverError}</Alert>}
          <TextField
            label="Confirm with your password"
            type="password"
            name="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => {
              setPassword(event.target.value);
              if (mutation.isError) mutation.reset();
            }}
            error={
              error ?? (isPasswordError(mutation.error) ? errorMessage(mutation.error) : undefined)
            }
            autoFocus
          />
          <div className={styles.dangerActions}>
            <Button type="submit" variant="danger" busy={mutation.isPending}>
              Delete my account
            </Button>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              Cancel
            </Button>
          </div>
        </form>
      ) : (
        <div>
          <Button variant="secondary" onClick={() => setOpen(true)}>
            Delete my account…
          </Button>
        </div>
      )}
    </section>
  );
}

// A wrong password belongs next to the password field; other errors go above the form.
function isPasswordError(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'INVALID_PASSWORD';
}
