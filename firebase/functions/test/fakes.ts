import type { ClaudeClient, ClaudeMessage, CreateParams } from "../src/claude";

type Reply = ClaudeMessage | Error | ((params: CreateParams) => ClaudeMessage);

/** Fake Anthropic client: records every request and plays back canned replies. No network, no key. */
export class FakeClaude implements ClaudeClient {
  readonly calls: CreateParams[] = [];
  private readonly replies: Reply[];

  constructor(...replies: Reply[]) {
    this.replies = replies;
  }

  beta = {
    messages: {
      create: async (params: CreateParams): Promise<ClaudeMessage> => {
        this.calls.push(structuredClone(params));
        const next = this.replies.shift();
        if (next === undefined) throw new Error("FakeClaude: no reply queued");
        if (next instanceof Error) throw next;
        return typeof next === "function" ? next(params) : next;
      },
    },
  };
}

export function message(
  content: unknown[],
  opts: { model?: string; stop?: string; input?: number; output?: number; searches?: number } = {},
): ClaudeMessage {
  return {
    id: "msg_test",
    type: "message",
    role: "assistant",
    model: opts.model ?? "claude-sonnet-5-5",
    content,
    stop_reason: opts.stop ?? "end_turn",
    stop_sequence: null,
    usage: {
      input_tokens: opts.input ?? 3000,
      output_tokens: opts.output ?? 800,
      cache_creation_input_tokens: 0,
      cache_read_input_tokens: 700,
      server_tool_use: { web_search_requests: opts.searches ?? 0, web_fetch_requests: 0 },
    },
  } as unknown as ClaudeMessage;
}

export function textMessage(text: string, opts: Parameters<typeof message>[1] = {}): ClaudeMessage {
  return message([{ type: "text", text, citations: null }], opts);
}

export const FIXED_NOW = () => new Date("2026-10-23T09:00:00Z");
