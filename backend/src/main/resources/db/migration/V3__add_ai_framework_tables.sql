-- ============================================
-- LifeTool V3 迁移：AI Framework 持久化表
-- PostgreSQL 16 + pgcrypto
-- Flyway 迁移：V3__add_ai_framework_tables.sql
-- ============================================

-- ============================================
-- 1. AI 工具调用审计
-- ============================================

CREATE TABLE ai_tool_calls (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id      uuid        NOT NULL REFERENCES ai_chat_sessions(id) ON DELETE CASCADE,
    message_id      uuid        NOT NULL REFERENCES ai_chat_messages(id) ON DELETE CASCADE,
    tool_name       text        NOT NULL,
    arguments       jsonb       NOT NULL DEFAULT '{}'::jsonb,
                                  -- 工具参数，禁止包含客户端传入 userId
    result_summary  jsonb       NULL,
                                  -- 工具结果摘要，不保存过量原始明细
    status          text        NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'succeeded', 'failed')),
    latency_ms      int         NULL CHECK (latency_ms >= 0),
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_tool_calls_user_session ON ai_tool_calls (user_id, session_id);
CREATE INDEX idx_ai_tool_calls_user_message ON ai_tool_calls (user_id, message_id);
CREATE INDEX idx_ai_tool_calls_user_status  ON ai_tool_calls (user_id, status);

COMMENT ON TABLE  ai_tool_calls                   IS 'AI 工具调用审计表';
COMMENT ON COLUMN ai_tool_calls.tool_name         IS '工具名，如 get_focus_summary';
COMMENT ON COLUMN ai_tool_calls.arguments         IS '工具参数，禁止包含客户端传入 userId';
COMMENT ON COLUMN ai_tool_calls.result_summary    IS '工具结果摘要，不保存过量原始明细';
COMMENT ON COLUMN ai_tool_calls.status            IS '调用状态：pending / succeeded / failed';
COMMENT ON COLUMN ai_tool_calls.latency_ms        IS '工具执行耗时（毫秒）';

-- ============================================
-- 2. AI 长期记忆
-- ============================================

CREATE TABLE ai_memory_items (
    id              uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    memory_type     text          NOT NULL
                    CHECK (memory_type IN ('preference', 'goal', 'constraint', 'health_note', 'routine')),
    content         text          NOT NULL,
    source          text          NOT NULL DEFAULT 'assistant_suggested'
                    CHECK (source IN ('user_confirmed', 'assistant_suggested', 'system_extracted')),
    confidence      numeric(4,3)  NULL CHECK (confidence >= 0 AND confidence <= 1),
                                  -- 置信度 0.000 ~ 1.000
    enabled         boolean       NOT NULL DEFAULT true,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    deleted_at      timestamptz   NULL
);

CREATE INDEX idx_ai_memory_items_user_type    ON ai_memory_items (user_id, memory_type);
CREATE INDEX idx_ai_memory_items_user_enabled ON ai_memory_items (user_id, enabled);

COMMENT ON TABLE  ai_memory_items                  IS 'AI 长期记忆表，记录用户偏好、目标、约束条件等';
COMMENT ON COLUMN ai_memory_items.memory_type      IS '记忆类型：preference / goal / constraint / health_note / routine';
COMMENT ON COLUMN ai_memory_items.content          IS '记忆内容';
COMMENT ON COLUMN ai_memory_items.source           IS '记忆来源：user_confirmed / assistant_suggested / system_extracted';
COMMENT ON COLUMN ai_memory_items.confidence       IS '置信度，0.000 ~ 1.000';
COMMENT ON COLUMN ai_memory_items.enabled          IS '是否启用，用户可禁用不需要的记忆';

-- ============================================
-- 3. AI 会话摘要
-- ============================================

CREATE TABLE ai_session_summaries (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      uuid        NOT NULL REFERENCES ai_chat_sessions(id) ON DELETE CASCADE,
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    summary         text        NOT NULL,
                                  -- 当前会话压缩摘要
    message_count   int         NOT NULL DEFAULT 0 CHECK (message_count >= 0),
                                  -- 摘要覆盖的消息数
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_ai_session_summaries_session ON ai_session_summaries (session_id);
CREATE INDEX idx_ai_session_summaries_user ON ai_session_summaries (user_id);

COMMENT ON TABLE  ai_session_summaries              IS 'AI 会话摘要表，每个会话最多一条当前摘要';
COMMENT ON COLUMN ai_session_summaries.summary       IS '当前会话压缩摘要，用于减少 token 消耗';
COMMENT ON COLUMN ai_session_summaries.message_count IS '摘要覆盖的消息数';

-- ============================================
-- 4. AI Agent 运行审计
-- ============================================

CREATE TABLE ai_agent_runs (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id      uuid        NULL REFERENCES ai_chat_sessions(id) ON DELETE SET NULL,
                                  -- 可为空，某些 run 可能独立于会话（如定时分析）
    provider        text        NOT NULL,
                                  -- AI Provider，如 openai / mock
    model           text        NOT NULL,
                                  -- 模型名，如 gpt-4o / gpt-4o-mini
    input_tokens    int         NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),
    output_tokens   int         NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),
    tool_rounds     int         NOT NULL DEFAULT 0 CHECK (tool_rounds >= 0),
    status          text        NOT NULL DEFAULT 'succeeded'
                    CHECK (status IN ('succeeded', 'failed')),
    error_code      text        NULL,
                                  -- 错误码，失败时记录
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_agent_runs_user_session ON ai_agent_runs (user_id, session_id);
CREATE INDEX idx_ai_agent_runs_user_status  ON ai_agent_runs (user_id, status);
CREATE INDEX idx_ai_agent_runs_user_time    ON ai_agent_runs (user_id, created_at);

COMMENT ON TABLE  ai_agent_runs               IS 'AI Agent 运行审计表';
COMMENT ON COLUMN ai_agent_runs.provider      IS 'AI Provider，如 openai / mock';
COMMENT ON COLUMN ai_agent_runs.model         IS '模型名，如 gpt-4o / gpt-4o-mini';
COMMENT ON COLUMN ai_agent_runs.input_tokens  IS '输入 token 数';
COMMENT ON COLUMN ai_agent_runs.output_tokens IS '输出 token 数';
COMMENT ON COLUMN ai_agent_runs.tool_rounds   IS '工具调用轮数';
COMMENT ON COLUMN ai_agent_runs.status        IS '运行状态：succeeded / failed';
COMMENT ON COLUMN ai_agent_runs.error_code    IS '错误码，失败时记录';

-- ============================================
-- 5. 新表 updated_at 触发器
-- ============================================

CREATE TRIGGER trg_ai_memory_items_updated_at
    BEFORE UPDATE ON ai_memory_items FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_ai_session_summaries_updated_at
    BEFORE UPDATE ON ai_session_summaries FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
