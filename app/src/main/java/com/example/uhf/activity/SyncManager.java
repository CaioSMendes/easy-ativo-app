package com.example.uhf.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.uhf.database.DatabaseHelper;
import com.example.uhf.database.DatabaseManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SyncManager {

    private static final String TAG = "SYNC_MANAGER";

    public interface SyncCallback {
        void onSuccess();
        void onError(Exception e);
    }

    // Executor para múltiplas threads de sincronização
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);

    public static void syncLocalizacoes(Context context, String baseUrl, SyncCallback callback) {
        executor.submit(() -> {
            try {
                Log.i(TAG, "=== Iniciando sincronização de níveis e localizações ===");

                DatabaseHelper db = DatabaseManager.getDatabase(context);
                SharedPreferences prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE);

                // --- Sincronizar níveis ---
                syncNiveis(baseUrl, db);

                // --- Sincronizar localizações ---
                syncLocais(baseUrl, db);

                long serverTimestamp = System.currentTimeMillis();
                prefs.edit().putLong("LOCALIZACOES_LAST_UPDATE", serverTimestamp).apply();

                Log.i(TAG, "=== Sincronização concluída com sucesso! ===");
                if (callback != null) callback.onSuccess();

            } catch (Exception e) {
                Log.e(TAG, "Erro sincronizando localizações", e);
                if (callback != null) callback.onError(e);
            }
        });
    }

    private static void syncNiveis(String baseUrl, DatabaseHelper db) throws Exception {
        String urlNiveis = baseUrl + "mobile/listar_nivel_localizacao";
        Log.d(TAG, "GET níveis: " + urlNiveis);

        JSONArray niveisArray = getJsonArrayFromUrl(urlNiveis);
        Log.i(TAG, "Níveis recebidos: " + niveisArray.length());

        for (int i = 0; i < niveisArray.length(); i++) {
            JSONObject nivel = niveisArray.getJSONObject(i);
            db.salvarOuAtualizarNivel(nivel); // salvar no banco
            Log.d(TAG, "Salvo nível: " + nivel.optString("nome"));
        }
    }

    private static void syncLocais(String baseUrl, DatabaseHelper db) throws Exception {
        String urlLocais = baseUrl + "mobile/listar_localizacao";
        Log.d(TAG, "GET localizações: " + urlLocais);

        JSONArray locaisArray = getJsonArrayFromUrl(urlLocais);
        Log.i(TAG, "Localizações recebidas: " + locaisArray.length());

        for (int i = 0; i < locaisArray.length(); i++) {
            JSONObject obj = locaisArray.getJSONObject(i);

            // Determina nível pelo idNivelLocalizacao
            JSONObject nivel = db.buscarNivelPorId(obj.optInt("idNivelLocalizacao"));
            String nomeNivel = nivel != null ? nivel.optString("nome") : "desconhecido";

            db.salvarOuAtualizarLocalizacao(obj, nomeNivel);
            Log.d(TAG, "Salvo [" + nomeNivel + "]: " + obj.optString("nome") +
                    " (Pai: " + obj.optInt("idLocalizacaoPai") + ")");
        }
    }

    private static JSONArray getJsonArrayFromUrl(String endpoint) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int responseCode = conn.getResponseCode();
        Log.d(TAG, "Código de resposta HTTP para " + endpoint + ": " + responseCode);
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new Exception("Erro GET " + endpoint + ", código: " + responseCode);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) result.append(line);
        reader.close();

        Log.d(TAG, "Resposta JSON de " + endpoint + ": " + result.toString());
        return new JSONArray(result.toString());
    }
}