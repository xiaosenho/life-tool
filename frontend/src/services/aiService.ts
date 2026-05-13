import { apiClient, ApiResponse } from "./apiClient";

export interface FoodItem {
  name: string;
  estimatedGrams: number;
  estimatedCalories: number;
  confidence: number;
}

export interface FoodResult {
  items: FoodItem[];
  totalCalories: number;
  notes: string;
}

export interface FoodRecognitionResponse {
  jobId: string;
  status: string;
  result: FoodResult | null;
}

export interface LifeAdviceResponse {
  summary: string;
  suggestions: string[];
  disclaimer: string;
}

export interface ChatSession {
  id: string;
  userId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: string;
  sessionId: string;
  role: string;
  content: string;
  createdAt: string;
}

const MOCK_ENABLED = true;

export const aiService = {
  async createFoodRecognitionJob(mediaAssetId: string): Promise<{ jobId: string; status: string }> {
    if (MOCK_ENABLED) {
      return { jobId: `job_${Date.now()}`, status: "pending" };
    }
    const resp = await apiClient.post<{ jobId: string; status: string }>("/ai/food-recognition/jobs", {
      mediaAssetId,
    });
    if (!resp.success || !resp.data) throw new Error(resp.error?.message ?? "创建识别任务失败");
    return resp.data;
  },

  async getFoodRecognitionJob(jobId: string): Promise<FoodRecognitionResponse> {
    if (MOCK_ENABLED) {
      return {
        jobId,
        status: "succeeded",
        result: {
          items: [
            { name: "米饭", estimatedGrams: 150, estimatedCalories: 174, confidence: 0.85 },
            { name: "番茄炒蛋", estimatedGrams: 200, estimatedCalories: 220, confidence: 0.72 },
            { name: "清炒西兰花", estimatedGrams: 120, estimatedCalories: 42, confidence: 0.68 },
          ],
          totalCalories: 436,
          notes: "识别结果为估算值，请确认后保存。",
        },
      };
    }
    const resp = await apiClient.get<FoodRecognitionResponse>(`/ai/food-recognition/jobs/${jobId}`);
    return resp.data ?? { jobId, status: "failed", result: null };
  },

  async getLifeAdvice(period: string = "last_7_days", topics: string[] = ["focus", "habits"]): Promise<LifeAdviceResponse> {
    if (MOCK_ENABLED) {
      return {
        summary: "你最近 7 天专注时间较稳定，平均每天约 45 分钟，习惯完成率 60%。",
        suggestions: [
          "把高强度专注安排在上午，效率更高。",
          "晚餐可以适当减少碳水摄入。",
          "连续打卡已坚持 7 天，继续保持！",
        ],
        disclaimer: "AI 建议仅供参考，不构成医疗或营养诊断。",
      };
    }
    const resp = await apiClient.post<LifeAdviceResponse>("/ai/life-advice", { period, topics });
    if (!resp.success || !resp.data) throw new Error(resp.error?.message ?? "获取建议失败");
    return resp.data;
  },

  async createChatSession(title?: string): Promise<ChatSession> {
    if (MOCK_ENABLED) {
      return {
        id: `session_${Date.now()}`,
        userId: "me",
        title: title ?? "新对话",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
    }
    const resp = await apiClient.post<ChatSession>("/ai/chat/sessions", title ? { title } : undefined);
    if (!resp.success || !resp.data) throw new Error(resp.error?.message ?? "创建对话失败");
    return resp.data;
  },

  async listChatSessions(): Promise<ChatSession[]> {
    if (MOCK_ENABLED) {
      return [
        { id: "session_1", userId: "me", title: "生活建议", createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
        { id: "session_2", userId: "me", title: "饮食咨询", createdAt: new Date(Date.now() - 86400000).toISOString(), updatedAt: new Date(Date.now() - 86400000).toISOString() },
      ];
    }
    const resp = await apiClient.get<ChatSession[]>("/ai/chat/sessions");
    return resp.data ?? [];
  },

  async sendChatMessage(sessionId: string, content: string): Promise<ChatMessage> {
    if (MOCK_ENABLED) {
      return {
        id: `msg_${Date.now()}`,
        sessionId,
        role: "assistant",
        content: "这是模拟的 AI 回复。在实际对接豆包后，我会根据你的数据给出更有用的建议。你可以和我聊聊你的生活习惯、饮食情况等。",
        createdAt: new Date().toISOString(),
      };
    }
    const resp = await apiClient.post<ChatMessage>(`/ai/chat/sessions/${sessionId}/messages`, { content });
    if (!resp.success || !resp.data) throw new Error(resp.error?.message ?? "发送消息失败");
    return resp.data;
  },

  async listChatMessages(sessionId: string): Promise<ChatMessage[]> {
    if (MOCK_ENABLED) {
      return [
        { id: "msg_1", sessionId, role: "user", content: "你好，能给我一些生活建议吗？", createdAt: new Date().toISOString() },
        { id: "msg_2", sessionId, role: "assistant", content: "当然可以！从你的数据看，最近专注和习惯保持得不错。建议你可以尝试增加午间短暂运动，有助于提高下午的工作效率。", createdAt: new Date().toISOString() },
      ];
    }
    const resp = await apiClient.get<ChatMessage[]>(`/ai/chat/sessions/${sessionId}/messages`);
    return resp.data ?? [];
  },

  async deleteChatSession(sessionId: string): Promise<void> {
    if (MOCK_ENABLED) return;
    await apiClient.delete(`/ai/chat/sessions/${sessionId}`);
  },

  async waitForJobCompletion(jobId: string, maxRetries: number = 10, intervalMs: number = 2000): Promise<FoodRecognitionResponse> {
    for (let i = 0; i < maxRetries; i++) {
      const result = await this.getFoodRecognitionJob(jobId);
      if (result.status === "succeeded" || result.status === "failed") {
        return result;
      }
      await new Promise((resolve) => setTimeout(resolve, intervalMs));
    }
    return { jobId, status: "failed", result: null };
  },
};
