package com.lifetool.meals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class MealService {
    private static final Pattern TOTAL_CALORIES_PATTERN = Pattern.compile(
            "(?:总热量|合计|总计|总摄入|总共)[^0-9]{0,30}(\\d+(?:\\.\\d+)?)\\s*(?:千卡|大卡|kcal|Kcal|KCAL|卡路里)?");
    private static final Pattern CALORIES_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:千卡|大卡|kcal|Kcal|KCAL)");

    private final MealStore mealStore;

    public MealService(MealStore mealStore) {
        this.mealStore = mealStore;
    }

    public MealLog recordAiRecognition(String userId, String aiResult, String requestedMealType, String mediaAssetId) {
        MealLog mealLog = new MealLog();
        mealLog.setUserId(userId);
        mealLog.setMealType(normalizeMealType(requestedMealType));
        mealLog.setOccurredAt(Instant.now());
        mealLog.setTotalCalories(extractTotalCalories(aiResult).orElse(null));
        mealLog.setNote(aiResult);
        mealLog.setMediaAssetId(blankToNull(mediaAssetId));
        mealLog.setAiGenerated(true);
        return mealStore.saveAiMealLog(mealLog);
    }

    public MealSummary getSummary(String userId) {
        return mealStore.getSummary(userId);
    }

    private static String normalizeMealType(String mealType) {
        if (mealType != null) {
            String normalized = mealType.trim();
            if (normalized.equals("breakfast") || normalized.equals("lunch")
                    || normalized.equals("dinner") || normalized.equals("snack")) {
                return normalized;
            }
        }
        LocalTime now = LocalTime.now();
        if (now.isBefore(LocalTime.of(11, 0))) {
            return "breakfast";
        }
        if (now.isBefore(LocalTime.of(16, 0))) {
            return "lunch";
        }
        if (now.isBefore(LocalTime.of(21, 0))) {
            return "dinner";
        }
        return "snack";
    }

    private static Optional<BigDecimal> extractTotalCalories(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher totalMatcher = TOTAL_CALORIES_PATTERN.matcher(text);
        BigDecimal total = null;
        while (totalMatcher.find()) {
            total = new BigDecimal(totalMatcher.group(1));
        }
        if (total != null) {
            return Optional.of(total);
        }

        Matcher calorieMatcher = CALORIES_PATTERN.matcher(text);
        BigDecimal last = null;
        while (calorieMatcher.find()) {
            last = new BigDecimal(calorieMatcher.group(1));
        }
        return Optional.ofNullable(last);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
