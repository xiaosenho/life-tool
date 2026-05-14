import { apiClient } from "./apiClient";

export interface LifeAdvice {
  summary: string;
  suggestions: string[];
  disclaimer: string;
}

export interface ChatSession {
  id: string;
  title: string;
  useLongTermMemory: boolean;
  createdAt: string;
  updatedAt?: string;
}

export interface ToolCallStatus {
  toolName: string;
  status: "pending" | "succeeded" | "failed";
}

export interface ChatMessage {
  id?: string;
  messageId?: string;
  role: "user" | "assistant";
  content: string;
  disclaimer?: string;
  toolCalls?: ToolCallStatus[];
  createdAt: string;
}

export interface MemoryItem {
  id: string;
  type: string;
  content: string;
  source: string;
  createdAt: string;
}

const defaultTools = [
  "get_focus_summary",
  "get_habit_summary",
  "get_diet_summary",
  "get_ledger_summary",
  "get_upcoming_events",
  "get_user_profile_context"
];

export const aiService = {
  getLifeAdvice(period = "last_7_days", topics = ["focus", "habits", "diet"]) {
    return apiClient.post<LifeAdvice>("/ai/life-advice", { period, topics });
  },

  createSession(title = "最近状态分析", useLongTermMemory = true) {
    return apiClient.post<ChatSession>("/ai/chat/sessions", { title, useLongTermMemory });
  },

  sendMessage(sessionId: string, content: string, enabledTools: string[] = defaultTools) {
    return apiClient.post<ChatMessage>(`/ai/chat/sessions/${sessionId}/messages`, { content, enabledTools });
  },

  getMessages(sessionId: string) {
    return apiClient.get<{ messages: ChatMessage[] }>(`/ai/chat/sessions/${sessionId}/messages`);
  },

  getMemories() {
    return apiClient.get<{ items: MemoryItem[] }>("/ai/memories");
  },

  deleteMemory(id: string) {
    return apiClient.delete<void>(`/ai/memories/${id}`);
  },

  recognizeFood(imageUrl: string, customPrompt?: string) {
    return apiClient.post<{ result: string; disclaimer: string }>("/ai/food-recognition", { imageUrl, customPrompt });
  }
};
