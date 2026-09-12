import type { NextFunction, Request, Response } from "express";

/**
 * Final error handler: never leaks stack traces, internal paths, or raw
 * upstream error bodies to the client (spec §15 "sanitize server errors").
 */
// eslint-disable-next-line @typescript-eslint/no-unused-vars -- Express requires a 4-arg signature to recognize this as an error handler.
export function errorHandler(err: unknown, _req: Request, res: Response, _next: NextFunction): void {
  // eslint-disable-next-line no-console
  console.error("Unhandled backend error:", err instanceof Error ? err.name : typeof err);
  if (res.headersSent) return;
  res.status(500).json({ error: "internal_error" });
}
