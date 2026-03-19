package com.example.uhf.database;

import android.content.Context;

import com.example.uhf.activity.ConfigActivity;

public class DatabaseManager {

    private static com.example.uhf.database.DatabaseHelper instance;

    public static synchronized com.example.uhf.database.DatabaseHelper getDatabase(Context context) {

        if (instance == null) {
            String baseUrl = ConfigActivity.getBaseUrl(context);
            String dbName = baseUrl.contains("teste") ? "rfid_homo.db" : "rfid_prod.db";
            instance = new com.example.uhf.database.DatabaseHelper(context.getApplicationContext(), dbName);
        }

        return instance;
    }

    // Permite resetar a instância ao trocar de ambiente
    public static synchronized void resetDatabase(Context context) {
        instance = null;
    }
}