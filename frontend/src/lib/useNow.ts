/**
 * The current time, refreshed every minute, so "5 minutes ago" stays right on a page that
 * stays open. All components share one timer.
 *
 * Reading the clock during rendering would make rendering impure: the same props could give
 * different output. React's `useSyncExternalStore` is the supported way to read a value that
 * changes outside React.
 */
import { useSyncExternalStore } from 'react';

const INTERVAL = 60_000;
let now = Date.now();
const listeners = new Set<() => void>();
let timer: ReturnType<typeof setInterval> | undefined;

function subscribe(listener: () => void) {
  listeners.add(listener);
  if (timer === undefined) {
    now = Date.now();
    timer = setInterval(() => {
      now = Date.now();
      listeners.forEach((notify) => notify());
    }, INTERVAL);
  }
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) {
      clearInterval(timer);
      timer = undefined;
    }
  };
}

function getSnapshot() {
  return now;
}

export function useNow(): number {
  return useSyncExternalStore(subscribe, getSnapshot);
}
