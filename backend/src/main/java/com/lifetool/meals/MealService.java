package com.lifetool.meals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;

import com.lifetool.ai.AiAssistantClient;
import com.lifetool.media.MediaService;
import com.lifetool.meals.dto.MealDetailResponse;

@Service
public class MealService {
    private static final Pattern TOTAL_CALORIES_PATTERN = Pattern.compile(
            "(?:总热量|合计|总计|总摄入|总共)[^0-9]{0,30}(\\d+(?:\\.\\d+)?)\\s*(?:千卡|大卡|kcal|Kcal|KCAL|卡路里)?");
    private static final Pattern CALORIES_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:千卡|大卡|kcal|Kcal|KCAL)");
    private static final String MEAL_RECOGNITION_PROMPT =
            "你是一个专业的营养师。请识别用户上传图片中的食物，估算每种食物的重量、热量（千卡）以及蛋白质/脂肪/碳水化合物含量（克），最后必须用“总热量约 N 千卡”给出总热量估算。";

    private final MealStore mealStore;
    private final MediaService mediaService;
    private final AiAssistantClient assistantClient;

    public MealService(MealStore mealStore, MediaService mediaService, @Lazy AiAssistantClient assistantClient) {
        this.mealStore = mealStore;
        this.mediaService = mediaService;
        this.assistantClient = assistantClient;
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

    public MealDetailResponse getMealDetail(String userId, String mealLogId) {
        MealLog mealLog = requireMeal(userId, mealLogId);
        String imageUrl = mealLog.getMediaAssetId() == null
                ? null
                : mediaService.generateReadUrl(userId, mealLog.getMediaAssetId(), "meal_photo");
        return new MealDetailResponse(
                mealLog.getId(),
                mealLog.getMealType(),
                mealLog.getOccurredAt(),
                mealLog.getTotalCalories(),
                mealLog.getNote(),
                mealLog.getMediaAssetId(),
                imageUrl,
                mealLog.isAiGenerated());
    }

    public void deleteMeal(String userId, String mealLogId) {
        requireMeal(userId, mealLogId);
        mealStore.delete(userId, mealLogId);
    }

    public MealDetailResponse rerunRecognition(String userId, String mealLogId) {
        MealLog mealLog = requireMeal(userId, mealLogId);
        if (mealLog.getMediaAssetId() == null || mealLog.getMediaAssetId().isBlank()) {
            throw new MealException("VALIDATION_ERROR", "Meal does not have an associated image");
        }
        String response = rerunRecognitionWithRetry(userId, mealLogId, mealLog.getMediaAssetId());
        mealLog.setNote(response);
        mealLog.setTotalCalories(extractTotalCalories(response).orElse(mealLog.getTotalCalories()));
        mealLog.setUpdatedAt(Instant.now());
        mealStore.updateAiMealLog(mealLog);
        return getMealDetail(userId, mealLogId);
    }

    private String rerunRecognitionWithRetry(String userId, String mealLogId, String mediaAssetId) {
        String userText = "请重新识别图片中的食物并估算热量";
        String firstImageUrl = mediaService.generateReadUrl(userId, mediaAssetId, "meal_photo");
        try {
            return assistantClient.chatWithImage("meal-rerun-" + mealLogId, MEAL_RECOGNITION_PROMPT, firstImageUrl, userText);
        } catch (RuntimeException firstEx) {
            if (!isCosDownload403(firstEx)) {
                throw firstEx;
            }
            String refreshedImageUrl = mediaService.generateReadUrl(userId, mediaAssetId, "meal_photo");
            return assistantClient.chatWithImage("meal-rerun-" + mealLogId, MEAL_RECOGNITION_PROMPT, refreshedImageUrl, userText);
        }
    }

    private boolean isCosDownload403(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("Error while downloading") && message.contains("status code: 403");
    }

    private MealLog requireMeal(String userId, String mealLogId) {
        MealLog mealLog = mealStore.findById(userId, mealLogId);
        if (mealLog == null) {
            throw new MealException("NOT_FOUND", "Meal not found");
        }
        return mealLog;
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
