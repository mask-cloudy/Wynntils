/*
 * Copyright © Wynntils 2022-2024.
 * This file is released under LGPLv3. See LICENSE for full license details.
 */
package com.wynntils.services.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wynntils.core.WynntilsMod;
import com.wynntils.core.components.Services;
import com.wynntils.core.net.UrlId;
import com.wynntils.utils.mc.McUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;
import java.util.Random;
import net.minecraft.network.chat.Component;

public class BaiduTranslationProvider extends CachingTranslationProvider {
    private static final String BAIDU_APPID = "20250916002455435";
    private static final String BAIDU_SECRET_KEY = "EEg57D5aEBpLn1GrGCvV";
    
    @Override
    protected CompletableFuture<Component> translateNew(Component message, String toLanguage) {
        String messageString = message.getString();
        String salt = String.valueOf(new Random().nextInt(100000));
        String sign = generateSign(messageString, salt);
        
        return Services.Net.callApi(UrlId.API_BAIDU_TRANSLATION, messageString, toLanguage, salt, sign)
                .handleAsync((response, throwable) -> {
                    if (throwable != null) {
                        WynntilsMod.warn("Failed to translate message using Baidu API: " + throwable.getMessage());
                        return message;
                    }

                    try {
                        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
                        
                        // Check for error
                        if (jsonResponse.has("error_code")) {
                            String errorCode = jsonResponse.get("error_code").getAsString();
                            String errorMsg = jsonResponse.has("error_msg") ? 
                                jsonResponse.get("error_msg").getAsString() : "Unknown error";
                            WynntilsMod.warn("Baidu translation API error: " + errorCode + " - " + errorMsg);
                            return message;
                        }
                        
                        // Parse translation result
                        if (jsonResponse.has("trans_result")) {
                            JsonArray transResult = jsonResponse.getAsJsonArray("trans_result");
                            if (transResult.size() > 0) {
                                JsonObject firstResult = transResult.get(0).getAsJsonObject();
                                if (firstResult.has("dst")) {
                                    String translatedText = firstResult.get("dst").getAsString();
                                    Component translatedComponent = Component.literal(translatedText);
                                    
                                    // Cache the translation
                                    cacheTranslation(messageString, toLanguage, translatedComponent);
                                    
                                    return translatedComponent;
                                }
                            }
                        }
                        
                        WynntilsMod.warn("Baidu translation API returned unexpected response format");
                        return message;
                        
                    } catch (Exception e) {
                        WynntilsMod.warn("Failed to parse Baidu translation response: " + e.getMessage());
                        return message;
                    }
                }, McUtils.mc()::execute);
    }
    
    private String generateSign(String query, String salt) {
        try {
            String signStr = BAIDU_APPID + query + salt + BAIDU_SECRET_KEY;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(signStr.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            WynntilsMod.warn("Failed to generate Baidu translation sign: " + e.getMessage());
            return "";
        }
    }
}