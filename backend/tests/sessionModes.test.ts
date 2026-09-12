import { test } from "node:test";
import assert from "node:assert/strict";
import { isValidMode, isValidEngine, isValidTargetLanguage } from "../src/model/sessionModes.js";

test("isValidMode accepts only the three documented modes", () => {
  assert.equal(isValidMode("CONVERSATION"), true);
  assert.equal(isValidMode("ONE_WAY"), true);
  assert.equal(isValidMode("PROFESSIONAL"), true);
  assert.equal(isValidMode("chat"), false);
  assert.equal(isValidMode(123), false);
  assert.equal(isValidMode(undefined), false);
});

test("isValidEngine accepts only the two known OpenAI realtime engines", () => {
  assert.equal(isValidEngine("gpt-realtime"), true);
  assert.equal(isValidEngine("gpt-realtime-translate"), true);
  assert.equal(isValidEngine("gpt-4o"), false);
  assert.equal(isValidEngine("gpt-realtime; DROP TABLE"), false);
});

test("isValidTargetLanguage rejects languages outside the reviewed allow-list", () => {
  assert.equal(isValidTargetLanguage("ar"), true);
  assert.equal(isValidTargetLanguage("en"), true);
  assert.equal(isValidTargetLanguage("fr"), true);
  assert.equal(isValidTargetLanguage("xx"), false);
  assert.equal(isValidTargetLanguage(""), false);
  assert.equal(isValidTargetLanguage(null), false);
});
