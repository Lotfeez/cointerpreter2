import type { Request, Response } from "express";
import { isValidEngine, isValidMode, isValidTargetLanguage } from "../model/sessionModes.js";
import type { OpenAiRealtimeGateway } from "../lib/openaiClient.js";
import { OpenAiGatewayError } from "../lib/openaiClient.js";
import crypto from "node:crypto";

/**
 * POST /v1/session-token
 *
 * Request body: { mode, engine, targetLanguage }
 * Response body: { ephemeralToken, expiresAt, engine }
 *
 * This is the ONLY endpoint the Android app calls before opening a realtime
 * session. It never returns the permanent OPENAI_API_KEY (spec §15), it
 * validates every field against a fixed allow-list (spec §15 "validate
 * input"), and it never logs request or transcript content (spec §15/§17:
 * only a truncated, non-identifying safety-identifier hash is derived, and
 * request bodies themselves are never written to logs by this handler).
 */
export function createSessionTokenHandler(gateway: OpenAiRealtimeGateway) {
  return async function handleSessionToken(req: Request, res: Response): Promise<void> {
    const { mode, engine, targetLanguage } = req.body ?? {};

    if (!isValidMode(mode)) {
      res.status(400).json({ error: "invalid_mode" });
      return;
    }
    if (!isValidEngine(engine)) {
      res.status(400).json({ error: "invalid_engine" });
      return;
    }
    if (!isValidTargetLanguage(targetLanguage)) {
      res.status(400).json({ error: "invalid_target_language" });
      return;
    }

    // A stable-but-anonymous per-request identifier, per OpenAI's guidance
    // to send a safety identifier without using anything personally
    // identifying (spec: never log conversation content; this hash is
    // derived from nothing but the current timestamp + random bytes, not
    // from any user-identifying data, since CoInterpreter has no accounts).
    const safetyIdentifier = crypto
      .createHash("sha256")
      .update(`${Date.now()}:${crypto.randomBytes(16).toString("hex")}`)
      .digest("hex")
      .slice(0, 32);

    try {
      const minted = await gateway.mintSessionToken({ mode, engine, targetLanguageCode: targetLanguage, safetyIdentifier });
      res.status(200).json({
        ephemeralToken: minted.ephemeralToken,
        expiresAt: minted.expiresAt,
        engine: minted.engine,
      });
    } catch (err) {
      if (err instanceof OpenAiGatewayError) {
        const status = err.upstreamStatus === 429 ? 429 : 502;
        res.status(status).json({ error: "upstream_error" });
        return;
      }
      res.status(500).json({ error: "internal_error" });
    }
  };
}
