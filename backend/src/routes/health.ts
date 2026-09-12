import type { Request, Response } from "express";

/** GET /healthz -- liveness probe only, reveals no configuration details. */
export function handleHealth(_req: Request, res: Response): void {
  res.status(200).json({ status: "ok" });
}
