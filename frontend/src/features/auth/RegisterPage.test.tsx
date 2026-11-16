import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { db, server } from '@/mocks/server';
import { createLoggedInUser, createUser, renderApp } from '@/test/render';

async function fillIn(email: string, username: string, password: string) {
  const user = userEvent.setup();
  if (email) await user.type(screen.getByLabelText('Email'), email);
  if (username) await user.type(screen.getByLabelText('Username'), username);
  if (password) await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Create account' }));
}

describe('RegisterPage', () => {
  it('creates the account and leads to the login page with the email filled in', async () => {
    const { router } = renderApp('/register');

    await fillIn('New@Example.com', 'newcomer', 'correct horse');

    expect(await screen.findByText(/your account is ready/i)).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    // The API stores emails in lower case and returns the stored value.
    expect(screen.getByLabelText('Email')).toHaveValue('new@example.com');
    expect(db.state.users.some((u) => u.username === 'newcomer')).toBe(true);
  });

  it('checks the fields in the browser before sending anything', async () => {
    let requests = 0;
    server.events.on('request:start', () => requests++);
    renderApp('/register');

    await fillIn('not-an-email', 'ab', 'short');

    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(/valid email/i);
    expect(screen.getByLabelText('Username')).toHaveAccessibleDescription(/3 to 30/i);
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription(/at least 8/i);
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
    expect(requests).toBe(0);
  });

  it('shows a taken email next to the email field, ignoring case', async () => {
    const existing = createUser();
    renderApp('/register');

    await fillIn(existing.email!.toUpperCase(), 'someone-new', 'correct horse');

    expect(await screen.findByLabelText('Email')).toHaveAccessibleDescription(/already exists/i);
  });

  it("treats the bot's username as taken", async () => {
    renderApp('/register');

    await fillIn('bot-fan@example.com', 'Blog-AI', 'correct horse');

    expect(await screen.findByLabelText('Username')).toHaveAccessibleDescription(/taken/i);
  });

  it("shows the server's validation errors next to their fields", async () => {
    server.use(
      http.post('*/api/auth/register', () =>
        HttpResponse.json(
          {
            status: 400,
            code: 'VALIDATION_FAILED',
            errors: [{ field: 'email', message: 'must be a well-formed email address' }],
          },
          { status: 400 },
        ),
      ),
    );
    renderApp('/register');

    await fillIn('odd@address', 'someone', 'correct horse');

    expect(await screen.findByLabelText('Email')).toHaveAccessibleDescription(
      'Must be a well-formed email address.',
    );
  });

  it('sends a logged-in user home', async () => {
    createLoggedInUser();
    const { router } = renderApp('/register');

    await screen.findByRole('heading', { name: 'Latest posts' });
    expect(router.state.location.pathname).toBe('/');
  });
});
