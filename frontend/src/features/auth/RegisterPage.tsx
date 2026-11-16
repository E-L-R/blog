/**
 * `/register`. Registering does not log the user in (docs/API.md#auth), so a successful
 * registration leads to the login page, with the email filled in. Part of the `auth`
 * feature's public API.
 */
import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import { Alert } from '@/components/Alert';
import { Button } from '@/components/Button';
import { TextField } from '@/components/Field';
import { conflictField, errorMessage, serverFieldErrors } from '@/lib/errors';
import {
  collectErrors,
  hasErrors,
  LIMITS,
  validateEmail,
  validateNewPassword,
  validateUsername,
} from '@/lib/validation';
import { register } from './api';
import { useAuth } from './AuthContext';
import { AuthForm, AuthLayout } from './AuthLayout';
import type { LoginPageState } from './LoginPage';

const FIELDS = ['email', 'username', 'password'] as const;

export function RegisterPage() {
  const { state } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const mutation = useMutation({
    mutationFn: register,
    onSuccess: (me) => {
      const loginState: LoginPageState = { registeredEmail: me.email };
      navigate('/login', { state: loginState });
    },
  });

  const validate = () =>
    collectErrors({
      email: validateEmail(email),
      username: validateUsername(username),
      password: validateNewPassword(password),
    });

  // A conflict (`EMAIL_TAKEN`, `USERNAME_TAKEN`) belongs to one field, and so do the server's
  // own validation errors. The browser's checks come first.
  const conflict = conflictField(mutation.error);
  const errors = {
    ...serverFieldErrors(mutation.error, FIELDS),
    ...(conflict ? { [conflict]: errorMessage(mutation.error) } : {}),
    ...(submitted ? validate() : {}),
  };
  const formError = mutation.isError && conflict === null && !hasErrors(errors);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitted(true);
    if (hasErrors(validate())) return;
    mutation.mutate({ email: email.trim(), username, password });
  }

  // Editing a field hides the server's verdict on the old value.
  function edit(setter: (value: string) => void) {
    return (event: { target: { value: string } }) => {
      setter(event.target.value);
      if (mutation.isError) mutation.reset();
    };
  }

  if (state.status === 'authenticated') return <Navigate to="/" replace />;

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Write posts, get an AI summary of each one, and ask the AI to reply."
      footer={
        <>
          Already have an account? <Link to="/login">Log in</Link>
        </>
      }
    >
      {formError && <Alert>{errorMessage(mutation.error)}</Alert>}
      <AuthForm onSubmit={handleSubmit}>
        <TextField
          label="Email"
          type="email"
          name="email"
          autoComplete="email"
          value={email}
          onChange={edit(setEmail)}
          error={errors.email}
          autoFocus
        />
        <TextField
          label="Username"
          name="username"
          autoComplete="username"
          value={username}
          onChange={edit(setUsername)}
          error={errors.username}
          hint={`${LIMITS.usernameMin}–${LIMITS.usernameMax} characters: letters, digits, _ and -. It can't be changed later.`}
        />
        <TextField
          label="Password"
          type="password"
          name="password"
          autoComplete="new-password"
          value={password}
          onChange={edit(setPassword)}
          error={errors.password}
          hint={`At least ${LIMITS.passwordMin} characters.`}
        />
        <Button type="submit" block busy={mutation.isPending}>
          Create account
        </Button>
      </AuthForm>
    </AuthLayout>
  );
}
