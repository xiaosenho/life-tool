import { ChatMessage, ChatSession } from "@/services/aiService";

export let cachedAiSession: ChatSession | null = null;
export let cachedAiMessages: ChatMessage[] = [];

export function setCachedAiSession(session: ChatSession | null) {
  cachedAiSession = session;
}

export function setCachedAiMessages(messages: ChatMessage[]) {
  cachedAiMessages = messages;
}
