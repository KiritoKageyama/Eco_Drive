package com.ecodrive.data;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class HistoryManager {

    private static final String PREFS_NAME = "EcoDriveHistory";
    private static final String KEY_SEARCHED_ROUTES = "searched_routes";
    private static final String KEY_CHAT_PROMPTS = "chat_prompts";

    public static void addSearchedRoute(Context context, String route) {
        addToList(context, KEY_SEARCHED_ROUTES, route, 10);
    }

    public static List<String> getSearchedRoutes(Context context) {
        return getList(context, KEY_SEARCHED_ROUTES);
    }

    public static void addChatPrompt(Context context, String prompt) {
        addToList(context, KEY_CHAT_PROMPTS, prompt, 15);
    }

    public static List<String> getChatPrompts(Context context) {
        return getList(context, KEY_CHAT_PROMPTS);
    }

    private static void addToList(Context context, String key, String item, int maxItems) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(key, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            JSONArray newArr = new JSONArray();
            newArr.put(item);
            // Keep up to maxItems - 1 old items
            for (int i = 0; i < arr.length() && i < maxItems - 1; i++) {
                newArr.put(arr.getString(i));
            }
            prefs.edit().putString(key, newArr.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    private static List<String> getList(Context context, String key) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(key, "[]");
        List<String> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getString(i));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }
}
