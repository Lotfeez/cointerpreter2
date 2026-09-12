import express from "express";
import cors from "cors";
import rateLimit from "express-rate-limit";
import { assertRuntimeConfigForServing, loadConfig, MissingApiKeyError } from "./lib/config.js";
import { HttpOpenAiRealtimeGateway } from "./lib/openaiClient.js";
import { createSessionTokenHandler } from "./routes/sessionToken.js";
import { handleHealth } from "./routes/health.js";
import { errorHandler } from "./middleware/errorHandler.js";

const config = loadConfig();

const app = express();
app.disable("x-powered-by");
app.use(express.json({ limit: "16kb" })); // session-token bodies are tiny; reject anything larger
app.use(
  cors({
    origin: config.allowedOrigins,
  }),
);

const sessionTokenLimiter = rateLimit({
  windowMs: 60_000,
  limit: config.sessionTokenRateLimitPerMinute,
  standardHeaders: true,
  legacyHeaders: false,
});

app.get("/healthz", handleHealth);

app.post(
  "/v1/session-token",
  sessionTokenLimiter,
  (req, res, next) => {
    try {
      assertRuntimeConfigForServing(config);
      next();
    } catch (err) {
      if (err instanceof MissingApiKeyError) {
        res.status(503).json({ error: "server_not_configured" });
        return;
      }
      next(err);
    }
  },
  createSessionTokenHandler(new HttpOpenAiRealtimeGateway(config)),
);

app.use(errorHandler);

// Only start listening when this file is actually the entrypoint, so tests
// can import route handlers / the app-building logic without binding a port.
if (import.meta.url === `file://${process.argv[1]}`) {
  app.listen(config.port, () => {
    // eslint-disable-next-line no-console
    console.log(`CoInterpreter backend listening on port ${config.port}`);
  });
}

export { app };
