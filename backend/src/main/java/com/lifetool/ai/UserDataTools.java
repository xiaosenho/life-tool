package com.lifetool.ai;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public UserDataTools(FocusPreferenceStore focusPreferenceStore, LedgerService ledgerService, EventStore eventStore) {
        this.focusPreferenceStore = focusPreferenceStore;
        this.ledgerService = ledgerService;
        this.eventStore = eventStore;
    }

    public Map<String, Object> execute(String toolName, String userId) {
        return switch (toolName) {
            case "get_focus_summary" -> getFocusSummary(userId);
            case "get_ledger_summary" -> getLedgerSummary(userId);
            case "get_upcoming_events" -> getUpcomingEvents(userId);
            case "get_habit_summary" -> summaryOnly("habit", "习惯模块暂未接入统计服务，本次仅返回占位汇总。");
            case "get_diet_summary" -> summaryOnly("diet", "饮食模块暂未接入统计服务，本次仅返回占位汇总。");
            case "get_user_profile_context" -> summaryOnly("profile", "已使用当前登录用户上下文，未暴露用户原始资料。");
            default -> throw new AiException("VALIDATION_ERROR", "Unsupported AI tool: " + toolName);
        };
    }

    private Map<String, Object> getFocusSummary(String userId) {
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

    private Map<String, Object> getLedgerSummary(String userId) {
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

    private Map<String, Object> getUpcomingEvents(String userId) {
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

    private static Map<String, Object> summaryOnly(String domain, String summary) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("domain", domain);
        result.put("summary", summary);
        return result;
    }
}
