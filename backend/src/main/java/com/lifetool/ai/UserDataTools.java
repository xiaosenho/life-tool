package com.lifetool.ai;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.lifetool.events.EventStore;
import com.lifetool.focus.FocusPreference;
import com.lifetool.focus.FocusPreferenceStore;
import com.lifetool.ledger.LedgerService;
import com.lifetool.ledger.dto.LedgerSummaryResponse;

@Component
public class UserDataTools {
    private final FocusPreferenceStore focusPreferenceStore;
    private final LedgerService ledgerService;
    private final EventStore eventStore;

    private static final ThreadLocal<String> CURRENT_USER_ID = new ThreadLocal<>();

    public UserDataTools(FocusPreferenceStore focusPreferenceStore, LedgerService ledgerService, EventStore eventStore) {
        this.focusPreferenceStore = focusPreferenceStore;
        this.ledgerService = ledgerService;
        this.eventStore = eventStore;
    }

    public static void setCurrentUserId(String userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static void clearCurrentUserId() {
        CURRENT_USER_ID.remove();
    }

    private String requireUserId() {
        String userId = CURRENT_USER_ID.get();
        if (userId == null || userId.isBlank()) {
            throw new AiException("VALIDATION_ERROR", "No current user context for AI tool execution");
        }
        return userId;
    }

    public Map<String, Object> execute(String toolName, String userId) {
        return switch (toolName) {
            case "get_focus_summary" -> getFocusSummary(userId);
            case "get_ledger_summary" -> getLedgerSummary(userId);
            case "get_upcoming_events" -> getUpcomingEvents(userId);
            case "get_habit_summary" -> getHabitSummary(userId);
            case "get_diet_summary" -> getDietSummary(userId);
            case "get_user_profile_context" -> getUserProfileContext(userId);
            default -> throw new AiException("VALIDATION_ERROR", "Unsupported AI tool: " + toolName);
        };
    }

    @Tool(name = "get_focus_summary", description = "查询用户近期专注总时长、默认时长偏好和趋势汇总")
    public Map<String, Object> getFocusSummaryTool() {
        return getFocusSummary(requireUserId());
    }

    @Tool(name = "get_habit_summary", description = "查询用户习惯完成率、连续打卡和薄弱习惯汇总")
    public Map<String, Object> getHabitSummaryTool() {
        return getHabitSummary(requireUserId());
    }

    @Tool(name = "get_diet_summary", description = "查询用户已确认饮食热量、餐次分布和识别草稿数量汇总")
    public Map<String, Object> getDietSummaryTool() {
        return getDietSummary(requireUserId());
    }

    @Tool(name = "get_ledger_summary", description = "查询用户月度收支、预算和分类支出汇总")
    public Map<String, Object> getLedgerSummaryTool() {
        return getLedgerSummary(requireUserId());
    }

    @Tool(name = "get_upcoming_events", description = "查询用户未来纪念日和提醒事件摘要")
    public Map<String, Object> getUpcomingEventsTool() {
        return getUpcomingEvents(requireUserId());
    }

    @Tool(name = "get_user_profile_context", description = "查询用户基础偏好、时区和隐私配置摘要")
    public Map<String, Object> getUserProfileContextTool() {
        return getUserProfileContext(requireUserId());
    }

    Map<String, Object> getFocusSummary(String userId) {
        FocusPreference preference = focusPreferenceStore.findByUserId(userId)
                .orElseGet(() -> new FocusPreference(userId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("domain", "focus");
        result.put("defaultFocusMinutes", preference.getDefaultFocusMinutes());
        result.put("shortBreakMinutes", preference.getShortBreakMinutes());
        result.put("longBreakMinutes", preference.getLongBreakMinutes());
        result.put("summary", "当前已读取专注偏好；专注历史统计将在专注记录服务接入后补充。");
        return result;
    }

    Map<String, Object> getLedgerSummary(String userId) {
        String month = YearMonth.now(ZoneOffset.UTC).toString();
        LedgerSummaryResponse summary = ledgerService.getSummary(userId, month);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("domain", "ledger");
        result.put("month", summary.month());
        result.put("income", summary.income());
        result.put("expense", summary.expense());
        result.put("balance", summary.balance());
        result.put("budget", summary.budget());
        result.put("categoryCount", summary.categoryExpenses().size());
        return result;
    }

    Map<String, Object> getUpcomingEvents(String userId) {
        List<Map<String, Object>> events = eventStore.findByUserId(userId).stream()
                .limit(10)
                .map(event -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("title", event.getTitle());
                    item.put("eventType", event.getType());
                    item.put("eventDate", event.getEventDate());
                    item.put("repeatRule", event.getRepeatRule());
                    return item;
                })
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("domain", "event");
        result.put("items", events);
        result.put("count", events.size());
        return result;
    }

    Map<String, Object> getHabitSummary(String userId) {
        return summaryOnly("habit", "习惯模块暂未接入统计服务，本次仅返回占位汇总。");
    }

    Map<String, Object> getDietSummary(String userId) {
        return summaryOnly("diet", "饮食模块暂未接入统计服务，本次仅返回占位汇总。");
    }

    Map<String, Object> getUserProfileContext(String userId) {
        return summaryOnly("profile", "已使用当前登录用户上下文，未暴露用户原始资料。");
    }

    private static Map<String, Object> summaryOnly(String domain, String summary) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("domain", domain);
        result.put("summary", summary);
        return result;
    }
}
