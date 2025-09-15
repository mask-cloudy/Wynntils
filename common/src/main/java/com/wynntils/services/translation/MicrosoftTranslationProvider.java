/*
 * Copyright © Wynntils 2018-2025.
 * This file is released under LGPLv3. See LICENSE for full license details.
 */
package com.wynntils.services.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wynntils.core.components.Managers;
import com.wynntils.core.net.ApiResponse;
import com.wynntils.core.net.UrlId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A TranslationService that uses the Microsoft Translator API. This service provides
 * 2 million characters per month for free and supports over 100 languages.
 * It offers better rate limits compared to Google Translate API.
 */
public class MicrosoftTranslationProvider extends CachingTranslationProvider {
    @Override
    protected void translateNew(List<String> messageList, String toLanguage, Consumer<List<String>> handleTranslation) {
        if (toLanguage == null || toLanguage.isEmpty()) {
            handleTranslation.accept(List.copyOf(messageList));
            return;
        }

        String message = String.join("{NL}", messageList);
        Map<String, String> arguments = new HashMap<>();
        arguments.put("to", toLanguage);
        arguments.put("text", message);

        ApiResponse apiResponse = Managers.Net.callApi(UrlId.API_MICROSOFT_TRANSLATION, arguments);
        apiResponse.handleJsonArray(
                json -> {
                    StringBuilder builder = new StringBuilder();
                    // Microsoft Translator returns an array of translation objects
                    // Each object has a "translations" array with the translated text
                    if (json.size() > 0) {
                        JsonObject translationObject = json.get(0).getAsJsonObject();
                        if (translationObject.has("translations")) {
                            JsonArray translations = translationObject.getAsJsonArray("translations");
                            if (translations.size() > 0) {
                                JsonObject translation = translations.get(0).getAsJsonObject();
                                if (translation.has("text")) {
                                    builder.append(translation.get("text").getAsString());
                                }
                            }
                        }
                    }
                    
                    String translatedMessage = builder.toString();
                    if (translatedMessage.isEmpty()) {
                        // If no translation was found, return original messages
                        handleTranslation.accept(List.copyOf(messageList));
                        return;
                    }
                    
                    List<String> result = Arrays.stream(translatedMessage.split("\\{NL\\}")).toList();
                    saveTranslation(toLanguage, messageList, result);
                    handleTranslation.accept(result);
                },
                onError -> {
                    // If Microsoft Translator returns an error, display original messages
                    handleTranslation.accept(List.copyOf(messageList));
                });
    }
}