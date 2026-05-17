package com.lifetool.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lifetool.ai.dto.FoodRecognitionRequest;
import com.lifetool.ai.dto.FoodRecognitionResponse;
import com.lifetool.media.MediaService;
import com.lifetool.meals.MealLog;
import com.lifetool.meals.MealService;

@ExtendWith(MockitoExtension.class)
class AiServiceFoodRecognitionRetryTest {

    @Mock private AiChatStore chatStore;
    @Mock private AiMemoryStore memoryStore;
    @Mock private UserDataTools userDataTools;
    @Mock private AiAssistantClient assistantClient;
    @Mock private MealService mealService;
    @Mock private MediaService mediaService;

    private static AiProperties testProperties() {
        AiProperties properties = new AiProperties();
        properties.setMockEnabled(true);
        properties.setMaxToolRounds(3);
        properties.setDisclaimer("disclaimer");
        return properties;
    }

    @Test
    void recognizeFoodRetriesOnceWhenCosSignedUrlExpired() {
        AiService service = new AiService(
                chatStore,
                memoryStore,
                userDataTools,
                testProperties(),
                assistantClient,
                mealService,
                mediaService);

        FoodRecognitionRequest request = new FoodRecognitionRequest(null, null, "dinner", "asset-1");
        when(mediaService.generateReadUrl("u1", "asset-1", "meal_photo"))
                .thenReturn("https://cos/old", "https://cos/new");
        when(assistantClient.chatWithImage(any(), any(), eq("https://cos/old"), any()))
                .thenThrow(new RuntimeException("HTTP 400 - Error while downloading: https://cos/old, status code: 403"));
        when(assistantClient.chatWithImage(any(), any(), eq("https://cos/new"), any()))
                .thenReturn("总热量约 520 千卡");
        MealLog mealLog = new MealLog();
        mealLog.setId("m1");
        mealLog.setUserId("u1");
        mealLog.setOccurredAt(Instant.now());
        mealLog.setMealType("dinner");
        mealLog.setNote("总热量约 520 千卡");
        mealLog.setTotalCalories(new BigDecimal("520"));
        when(mealService.recordAiRecognition(eq("u1"), eq("总热量约 520 千卡"), eq("dinner"), eq("asset-1")))
                .thenReturn(mealLog);

        FoodRecognitionResponse response = service.recognizeFood("u1", request);

        assertEquals("总热量约 520 千卡", response.result());
        assertEquals("m1", response.mealLogId());
        assertEquals(new BigDecimal("520"), response.totalCalories());
        verify(mediaService, times(2)).generateReadUrl("u1", "asset-1", "meal_photo");
    }

    @Test
    void recognizeFoodThrowsWhenAssistantFailsWithNon403() {
        AiService service = new AiService(
                chatStore,
                memoryStore,
                userDataTools,
                testProperties(),
                assistantClient,
                mealService,
                mediaService);

        FoodRecognitionRequest request = new FoodRecognitionRequest(null, null, "lunch", "asset-2");
        when(mediaService.generateReadUrl("u1", "asset-2", "meal_photo"))
                .thenReturn("https://cos/ok");
        when(assistantClient.chatWithImage(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("HTTP 400 - Invalid image"));

        AiException ex = assertThrows(AiException.class, () -> service.recognizeFood("u1", request));
        assertEquals("AI_RECOGNITION_FAILED", ex.getCode());
        verify(mediaService, times(1)).generateReadUrl("u1", "asset-2", "meal_photo");
    }

    @Test
    void recognizeFoodDoesNotPersistWhenAiSaysNonFoodOrZeroCalories() {
        AiService service = new AiService(
                chatStore,
                memoryStore,
                userDataTools,
                testProperties(),
                assistantClient,
                mealService,
                mediaService);

        FoodRecognitionRequest request = new FoodRecognitionRequest(null, null, "snack", "asset-3");
        when(mediaService.generateReadUrl("u1", "asset-3", "meal_photo"))
                .thenReturn("https://cos/non-food");
        when(assistantClient.chatWithImage(any(), any(), eq("https://cos/non-food"), any()))
                .thenReturn("这不是食物，总热量约 0 千卡。");

        FoodRecognitionResponse response = service.recognizeFood("u1", request);

        assertEquals("这不是食物，总热量约 0 千卡。", response.result());
        assertEquals(null, response.mealLogId());
        assertEquals(BigDecimal.ZERO, response.totalCalories());
        verify(mealService, never()).recordAiRecognition(any(), any(), any(), any());
    }

    @Test
    void shouldPersistFoodRecognitionRequiresPositiveCaloriesAndFoodSignal() {
        assertTrue(MealService.shouldPersistAiRecognition("鸡蛋一份。总热量约 80 千卡。"));
        assertFalse(MealService.shouldPersistAiRecognition("画面较模糊，无法估算。"));
        assertFalse(MealService.shouldPersistAiRecognition("未识别到食物，不会记录饮食。总热量约 0 千卡"));
    }
}
