/**
 * Asks for an AI reply and waits until the worker has dealt with it. Internal to `ai`.
 *
 * The backend writes the reply in the background (docs/ARCHITECTURE.md#async-ai-processing),
 * so the request is asked about again and again until its status is final, as docs/API.md
 * advises. The first check is immediate; then every {@link POLL_INTERVAL_MS}.
 */
import type { AiRequest, AiRequestStatus } from '@/api/types';
import { getAiRequest, requestReply, type AiTarget } from './api';

export const POLL_INTERVAL_MS = 2000;

/** How long to keep asking. A slow local model with retries can take a while. */
export const GIVE_UP_AFTER_MS = 5 * 60_000;

const FINAL: ReadonlySet<AiRequestStatus> = new Set(['COMPLETED', 'FAILED', 'CANCELLED']);

/**
 * Resolves with the request once it is final, or with its latest state (still `PENDING` or
 * `IN_PROGRESS`) after {@link GIVE_UP_AFTER_MS}.
 */
export async function askAndWait(
  target: AiTarget,
  now: () => number = Date.now,
  sleep: (ms: number) => Promise<void> = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
): Promise<AiRequest> {
  const started = now();
  let request = await requestReply(target);
  while (!FINAL.has(request.status)) {
    if (now() - started > GIVE_UP_AFTER_MS) return request;
    request = await getAiRequest(request.id);
    if (FINAL.has(request.status)) break;
    await sleep(POLL_INTERVAL_MS);
  }
  return request;
}
