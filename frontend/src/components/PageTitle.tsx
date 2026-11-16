/**
 * Sets the browser tab's title. React 19 moves a `<title>` rendered anywhere into the
 * document's `<head>`. Shared component.
 */
export function PageTitle({ children }: { children: string }) {
  return <title>{`${children} · Blog`}</title>;
}
