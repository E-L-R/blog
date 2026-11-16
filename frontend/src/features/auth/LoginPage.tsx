/**
 * `/login`. After logging in, the user goes back to the page that sent them here
 * (`?redirect=`), or to the home page. Part of the `auth` feature's public API.
 */
import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router';
import { Alert } from '@/components/Alert';
import { Button } from '@/components/Button';
import { TextField } from '@/components/Field';
import { errorMessage } from '@/lib/errors';
import { collectErrors, hasErrors, validateEmail, validateRequired } from '@/lib/validation';
import { login } from './api';
import { useAuth } from './AuthContext';
import { AuthForm, AuthLayout } from './AuthLayout';
import { safeRedirect } from './redirect';

/** What the registration page passes on, in the navigation state. */
export interface LoginPageState {
  registeredEmail?: string;
}

export function LoginPage() {
  const { state, logIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const registeredEmail = (location.state as LoginPageState | null)?.registeredEmail;
  const redirect = safeRedirect(searchParams.get('redirect'));

  const [email, setEmail] = useState(registeredEmail ?? '');
  const [password, setPassword] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (response) => {
      logIn(response.accessToken, response.expiresIn);
      navigate(redirect, { replace: true });
    },
  });

  // Errors appear after the first attempt to submit, then update as the user types.
  const errors = submitted
    ? collectErrors({
        email: validateEmail(email),
        password: validateRequired(password, 'your password'),
      })
    : {};

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitted(true);
    const current = collectErrors({
      email: validateEmail(email),
      password: validateRequired(password, 'your password'),
    });
    if (hasErrors(current)) return;
    mutation.mutate({ email: email.trim(), password });
  }

  if (state.status === 'authenticated' && !mutation.isSuccess) {
    return <Navigate to={redirect} replace />;
  }

  return (
    <AuthLayout
      title="Welcome back"
      subtitle="Log in to write posts and join the conversation."
      footer={
        <>
          New here? <Link to="/register">Create an account</Link>
        </>
      }
    >
      {registeredEmail && !mutation.isError && (
        <Alert tone="success">Your account is ready. Log in to start writing.</Alert>
      )}
      {mutation.isError && <Alert>{errorMessage(mutation.error)}</Alert>}
      <AuthForm onSubmit={handleSubmit}>
        <TextField
          label="Email"
          type="email"
          name="email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={errors.email}
          autoFocus={!registeredEmail}
        />
        <TextField
          label="Password"
          type="password"
          name="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={errors.password}
          autoFocus={!!registeredEmail}
        />
        <Button type="submit" block busy={mutation.isPending}>
          Log in
        </Button>
      </AuthForm>
    </AuthLayout>
  );
}
