/**
 * Mirrors com.cointerpreter.app.model.InterpreterMode on the Android side
 * and the two engine ids from com.cointerpreter.app.engine.InterpreterEngineFactory,
 * so the backend can validate what it's being asked to mint a credential
 * for without importing Kotlin code.
 */
export const VALID_MODES = ["CONVERSATION", "ONE_WAY", "PROFESSIONAL"] as const;
export type InterpreterMode = (typeof VALID_MODES)[number];

export const VALID_ENGINES = ["gpt-realtime", "gpt-realtime-translate"] as const;
export type EngineId = (typeof VALID_ENGINES)[number];

// Conservative allow-list matching Language.EXTENDED on the Android side.
// Rejecting unknown codes here is defense in depth: it stops a modified or
// malicious client from asking OpenAI for a target language CoInterpreter's
// UI has never been reviewed against.
export const ALLOWED_TARGET_LANGUAGE_CODES = new Set([
  "ar", "en", "fr", "es", "de", "it", "pt", "tr", "ur", "fa", "hi", "zh", "ja", "ko", "ru",
]);

export function isValidMode(value: unknown): value is InterpreterMode {
  return typeof value === "string" && (VALID_MODES as readonly string[]).includes(value);
}

export function isValidEngine(value: unknown): value is EngineId {
  return typeof value === "string" && (VALID_ENGINES as readonly string[]).includes(value);
}

export function isValidTargetLanguage(value: unknown): value is string {
  return typeof value === "string" && ALLOWED_TARGET_LANGUAGE_CODES.has(value);
}
