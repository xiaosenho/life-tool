package com.lifetool.ai;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.lifetool.common.TimeSupport;
import com.lifetool.events.EventStore;
import com.lifetool.focus.FocusPreference;
import com.lifetool.focus.FocusPreferenceStore;
import com.lifetool.habits.Habit;
import com.lifetool.habits.HabitCheckin;
import com.lifetool.habits.HabitCheckinStore;
import com.lifetool.habits.HabitStore;
import com.lifetool.ledger.LedgerService;
import com.lifetool.ledger.dto.LedgerSummaryResponse;
import com.lifetool.meals.MealService;
import com.lifetool.meals.MealSummary;

@Component
public class UserDataTools {
    private static final List<String> SUPPORTED_MEMORY_TYPES = List.of("preference");
    private static final ThreadLocal<List<Map<String, Object>>> SAVED_MEMORY_EVENTS =
            ThreadLocal.withInitial(ArrayList::new);

    private final FocusPreferenceStore focusPreferenceStore;
    private final LedgerService ledgerService;
    private final EventStore eventStore;
    private final HabitStore habitStore;
    private final HabitCheckinStore habitCheckinStore;
    private final MealService mealService;
    private final AiMemoryStore memoryStore;

    private static final ThreadLocal<String> CURRENT_USER_ID = new ThreadLocal<>();

    public UserDataTools(FocusPreferenceStore focusPreferenceStore, LedgerService ledgerService, EventStore eventStore,
                         HabitStore habitStore, HabitCheckinStore habitCheckinStore, MealService mealService,
                         AiMemoryStore memoryStore) {
        this.focusPreferenceStore = focusPreferenceStore;
        this.ledgerService = ledgerService;
        this.eventStore = eventStore;
        this.habitStore = habitStore;
        this.habitCheckinStore = habitCheckinStore;
        this.mealService = mealService;
        this.memoryStore = memoryStore;
    }

    public static void setCurrentUserId(String userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static void clearCurrentUserId() {
        CURRENT_USER_ID.remove();
    }

    public static String requireCurrentUserId() {
        String userId = CURRENT_USER_ID.get();
        if (userId == null || userId.isBlank()) {
            throw new AiException("VALIDATION_ERROR", "No current user context for AI tool execution");
        }
        return userId;
    }

    public static void resetSavedMemoryEvents() {
        SAVED_MEMORY_EVENTS.get().clear();
    }

    public static List<Map<String, Object>> consumeSavedMemoryEvents() {
        List<Map<String, Object>> events = List.copyOf(SAVED_MEMORY_EVENTS.get());
        SAVED_MEMORY_EVENTS.remove();
        return events;
    }

    private String requireUserId() {
        return requireCurrentUserId();
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

    @Tool(name = "save_long_term_memory", description = "仅当用户明确表达长期稳定偏好时调用，例如“以后都用简洁中文回答我”。不要保存临时要求、一次性上下文或模糊表述。")
    public Map<String, Object> saveLongTermMemoryTool(String memoryType, String content) {
        String userId = requireUserId();
        String normalizedType = normalizeMemoryType(memoryType);
        String normalizedContent = normalizeMemoryContent(content);

        boolean duplicated = memoryStore.findEnabledByUserId(userId).stream()
                .anyMatch(item -> Objects.equals(item.getType(), normalizedType)
                        && Objects.equals(item.getContent(), normalizedContent));
        if (duplicated) {
            return Map.of(
                    "saved", false,
                    "reason", "duplicate",
                    "memoryType", normalizedType,
                    "content", normalizedContent);
        }

        AiMemoryItem memory = memoryStore.save(new AiMemoryItem(
                userId,
                normalizedType,
                normalizedContent,
                "user_explicit"));
        SAVED_MEMORY_EVENTS.get().add(Map.of(
                "memoryId", memory.getId(),
                "memoryType", memory.getType(),
                "content", memory.getContent()));
        return Map.of(
                "saved", true,
                "memoryId", memory.getId(),
                "memoryType", memory.getType(),
                "content", memory.getContent());
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
        String month = TimeSupport.currentMonth().toString();
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
        List<Habit> habits = habitStore.findByUserId(userId).stream()
                .filter(h -> !h.isArchived())
                .toList();
        List<HabitCheckin> todayCheckins = habitCheckinStore.findByUserIdAndDate(
                userId, TimeSupport.today());
        long completedToday = todayCheckins.stream()
                .filter(c -> c.getCount() > 0)
                .map(HabitCheckin::getHabitId)
                .distinct()
                .count();
        double completionRate = habits.isEmpty() ? 0 : completedToday * 1.0 / habits.size();

        List<Map<String, Object>> recent = todayCheckins.stream()
                .sorted(Comparator.comparing(HabitCheckin::getUpdatedAt).reversed())
                .limit(5)
                .map(c -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("habitId", c.getHabitId());
                    item.put("count", c.getCount());
                    item.put("note", c.getNote());
                    return item;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("domain", "habit");
        result.put("activeHabitCount", habits.size());
        result.put("completedToday", completedToday);
        result.put("completionRate", completionRate);
        result.put("todayCheckins", recent);
        result.put("summary", "已读取今日习惯完成情况。");
        return result;
    }

    Map<String, Object> getDietSummary(String userId) {
        MealSummary summary = mealService.getSummary(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("domain", "diet");
        result.put("todayTotalCalories", summary.todayTotalCalories());
        result.put("todayMealCount", summary.todayMealCount());
        result.put("last7DaysTotalCalories", summary.last7DaysTotalCalories());
        result.put("last7DaysMealCount", summary.last7DaysMealCount());
        result.put("recentMeals", summary.recentMeals());
        result.put("summary", "已读取真实饮食记录与近 7 日热量汇总。");
        return result;
    }

    Map<String, Object> getUserProfileContext(String userId) {
        return summaryOnly("profile", "已使用当前登录用户上下文，未暴露用户原始资料。");
    }

    private static String normalizeMemoryType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (!SUPPORTED_MEMORY_TYPES.contains(normalized)) {
            throw new AiException("VALIDATION_ERROR", "Unsupported long-term memory type: " + value);
        }
        return normalized;
    }

    private static String normalizeMemoryContent(String value) {
        if (value == null || value.isBlank()) {
            throw new AiException("VALIDATION_ERROR", "Long-term memory content is required");
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 200) {
            throw new AiException("VALIDATION_ERROR", "Long-term memory content is too long");
        }
        return normalized;
    }

    private static Map<String, Object> summaryOnly(String domain, String summary) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("domain", domain);
        result.put("summary", summary);
        return result;
    }
}
