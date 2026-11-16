/**
 * The route table: which page each URL shows. Tests render the same table, so they exercise
 * the real routing.
 */
import type { RouteObject } from 'react-router';
import { AccountPage, UserPage } from '@/features/account';
import { LoginPage, RegisterPage, RequireAuth } from '@/features/auth';
import { EditPostPage, NewPostPage, PostListPage, PostPage } from '@/features/posts';
import { Layout } from './Layout';
import { NotFoundPage } from './NotFoundPage';
import { RouteError } from './RouteError';

export const routes: RouteObject[] = [
  {
    element: <Layout />,
    errorElement: <RouteError />,
    children: [
      { index: true, element: <PostListPage /> },
      { path: 'posts/:postId', element: <PostPage /> },
      { path: 'users/:userId', element: <UserPage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
      {
        element: <RequireAuth />,
        children: [
          { path: 'posts/new', element: <NewPostPage /> },
          { path: 'posts/:postId/edit', element: <EditPostPage /> },
          { path: 'account', element: <AccountPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
