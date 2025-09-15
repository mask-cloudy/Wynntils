/*
 * Copyright © Wynntils 2022-2024.
 * This file is released under LGPLv3. See LICENSE for full license details.
 */
package com.wynntils.services.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wynntils.core.WynntilsMod;
import com.wynntils.core.components.Managers;
import com.wynntils.core.net.UrlId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

public class BaiduTranslationProvider extends CachingTranslationProvider {
    private static final String BAIDU_APPID = "20250916002455435";
    private static final String BAIDU_SECRET_KEY = "EEg57D5aEBpLn1GrGCvV";
    
    @Override
    protected void translateNew(List<String> messageList, String toLanguage, Consumer<List<String>> handleTranslation) {
        if (toLanguage == null || toLanguage.isEmpty()) {
            handleTranslation.accept(List.copyOf(messageList));
            return;
        }
        
        String message = String.join("{NL}", messageList);
        String salt = String.valueOf(new Random().nextInt(100000));
        String sign = generateSign(message, salt);
        
        Map<String, String> arguments = new HashMap<>();
        arguments.put("q", message);
        arguments.put("from", "en");
        arguments.put("to", toLanguage);
        arguments.put("appid", BAIDU_APPID);
        arguments.put("salt", salt);
        arguments.put("sign", sign);
        
        Managers.Net.callApi(UrlId.API_BAIDU_TRANSLATION, arguments).handleJsonObject(jsonResponse -> {
            try {
                // Check for error
                if (jsonResponse.has("error_code")) {
                    String errorCode = jsonResponse.get("error_code").getAsString();
                    String errorMsg = jsonResponse.has("error_msg") ? 
                        jsonResponse.get("error_msg").getAsString() : "Unknown error";
                    WynntilsMod.warn("Baidu translation API error: " + errorCode + " - " + errorMsg);
                    handleTranslation.accept(List.copyOf(messageList));
                    return;
                }
                
                // Parse translation result
                if (jsonResponse.has("trans_result")) {
                    JsonArray transResult = jsonResponse.getAsJsonArray("trans_result");
                    if (transResult.size() > 0) {
                        JsonObject firstResult = transResult.get(0).getAsJsonObject();
                        if (firstResult.has("dst")) {
                            String translatedText = firstResult.get("dst").getAsString();
                            List<String> translatedList = List.of(translatedText.split("\{NL\}"));
                            
                            // Cache the translation
                            saveTranslation(toLanguage, messageList, translatedList);
                            
                            handleTranslation.accept(translatedList);
                            return;
                        }
                    }
                }
                
                WynntilsMod.warn("Baidu translation API returned unexpected response format");
                handleTranslation.accept(List.copyOf(messageList));
                
            } catch (Exception e) {
                WynntilsMod.warn("Failed to parse Baidu translation response: " + e.getMessage());
                handleTranslation.accept(List.copyOf(messageList));
            }
        });
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