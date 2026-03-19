package com.example.uhf.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final int VERSION = 3;

    public DatabaseHelper(Context context, String dbName) {
        super(context, dbName, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL("CREATE TABLE IF NOT EXISTS usuario (" +
                "id INTEGER PRIMARY KEY," +
                "nome TEXT," +
                "email TEXT," +
                "login TEXT," +
                "perfil TEXT," +
                "senha_hash TEXT," +
                "ultimo_login INTEGER" +
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS localizacao (" +
                "id INTEGER PRIMARY KEY," +
                "idLocalizacaoPai INTEGER," +
                "nome TEXT," +
                "nivel TEXT," +
                "json TEXT" +
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS nivel_localizacao (" +
                "id INTEGER PRIMARY KEY," +
                "nome TEXT," +
                "idNivelLocalizacaoPai INTEGER" +
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS ativo (" +
                "id INTEGER PRIMARY KEY," +
                "epc TEXT," +
                "descricao TEXT," +
                "status TEXT," +
                "localizacaoId INTEGER" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS usuario");
        db.execSQL("DROP TABLE IF EXISTS localizacao");
        db.execSQL("DROP TABLE IF EXISTS nivel_localizacao");
        db.execSQL("DROP TABLE IF EXISTS ativo");
        onCreate(db);
    }

    /* =========================
       NÍVEL DE LOCALIZAÇÃO
       ========================= */
    public void salvarOuAtualizarNivel(JSONObject nivel) {
        try {
            int id = nivel.optInt("id");
            String nome = nivel.optString("nome");
            Integer idPai = nivel.has("idNivelLocalizacaoPai") && !nivel.isNull("idNivelLocalizacaoPai")
                    ? nivel.optInt("idNivelLocalizacaoPai") : null;

            SQLiteDatabase db = getWritableDatabase();

            Cursor cursor = db.rawQuery("SELECT id FROM nivel_localizacao WHERE id = ?", new String[]{String.valueOf(id)});
            boolean exists = cursor.moveToFirst();
            cursor.close();

            ContentValues values = new ContentValues();
            values.put("nome", nome);
            values.put("idNivelLocalizacaoPai", idPai);

            if (exists) db.update("nivel_localizacao", values, "id = ?", new String[]{String.valueOf(id)});
            else {
                values.put("id", id);
                db.insert("nivel_localizacao", null, values);
            }

            db.close();
        } catch (Exception e) {
            Log.e("DB", "Erro salvar/atualizar nível", e);
        }
    }

    public JSONObject buscarNivelPorId(int id) {
        JSONObject obj = null;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM nivel_localizacao WHERE id = ?", new String[]{String.valueOf(id)});
        if (cursor.moveToFirst()) {
            try {
                obj = new JSONObject();
                obj.put("id", cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                obj.put("nome", cursor.getString(cursor.getColumnIndexOrThrow("nome")));
                int pai = cursor.getInt(cursor.getColumnIndexOrThrow("idNivelLocalizacaoPai"));
                if (!cursor.isNull(cursor.getColumnIndexOrThrow("idNivelLocalizacaoPai"))) {
                    obj.put("idNivelLocalizacaoPai", pai);
                } else {
                    obj.put("idNivelLocalizacaoPai", JSONObject.NULL);
                }
            } catch (Exception e) {
                Log.e("DB", "Erro JSON", e);
            }
        }
        cursor.close();
        db.close();
        return obj;
    }

    /* =========================
       LOCALIZAÇÃO
       ========================= */
    public void salvarOuAtualizarLocalizacao(JSONObject obj, String nivel) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            int id = obj.optInt("id");
            Cursor cursor = db.rawQuery("SELECT id, nome FROM localizacao WHERE id = ?", new String[]{String.valueOf(id)});
            boolean existe = cursor.moveToFirst();
            String nomeAtual = existe ? cursor.getString(cursor.getColumnIndexOrThrow("nome")) : "";
            cursor.close();

            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("idLocalizacaoPai", obj.optInt("idLocalizacaoPai", -1));
            values.put("nome", obj.optString("nome"));
            values.put("nivel", nivel);
            values.put("json", obj.toString());

            if (existe) {
                if (!nomeAtual.equals(obj.optString("nome"))) db.update("localizacao", values, "id = ?", new String[]{String.valueOf(id)});
            } else db.insert("localizacao", null, values);

        } catch (Exception e) {
            Log.e("DB", "Erro salvar/atualizar localizacao", e);
        } finally {
            db.close();
        }
    }

    public ArrayList<JSONObject> buscarLocalizacaoPorPai(int idPai) {
        ArrayList<JSONObject> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM localizacao WHERE idLocalizacaoPai = ?",
                new String[]{String.valueOf(idPai)}
        );
        while (cursor.moveToNext()) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                obj.put("idLocalizacaoPai", cursor.getInt(cursor.getColumnIndexOrThrow("idLocalizacaoPai")));
                obj.put("nome", cursor.getString(cursor.getColumnIndexOrThrow("nome")));
                lista.add(obj);
            } catch (Exception e) {
                Log.e("DB", "Erro JSON localizacao", e);
            }
        }
        cursor.close();
        db.close();
        return lista;
    }

    public List<JSONObject> buscarAtivosPorLocalEStatus(int localId, String status) {
        List<JSONObject> lista = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.query(
                    "ativo",
                    null,
                    "localizacaoId = ? AND status = ?",
                    new String[]{String.valueOf(localId), status},
                    null, null, null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int idxEpc   = cursor.getColumnIndexOrThrow("epc");
                int idxDesc  = cursor.getColumnIndexOrThrow("descricao");
                int idxStatus = cursor.getColumnIndexOrThrow("status");
                int idxLocal = cursor.getColumnIndexOrThrow("localizacaoId");

                do {
                    JSONObject ativo = new JSONObject();
                    ativo.put("rfid",         cursor.getString(idxEpc));
                    ativo.put("descricao",    cursor.getString(idxDesc));
                    ativo.put("status",       cursor.getString(idxStatus));
                    ativo.put("localizacaoId", cursor.getInt(idxLocal));
                    lista.add(ativo);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("DB_HELPER", "Erro buscarAtivosPorLocalEStatus", e);
        } finally {
            if (cursor != null) cursor.close();
        }

        return lista;
    }

    /* =========================
       ATIVO
       ========================= */
    public void salvarOuAtualizarAtivoSimples(JSONObject ativo) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            int id = ativo.optInt("id");
            String epc = ativo.optString("rfid", ativo.optString("epc", ""));
            String descricao = ativo.optString("descricao", "Sem descrição");
            String status = ativo.optString("status", "NO LOCAL");
            int localizacaoId = ativo.optInt("localizacaoId", -1);

            Cursor cursor = db.rawQuery("SELECT id FROM ativo WHERE id = ?", new String[]{String.valueOf(id)});
            boolean exists = cursor.moveToFirst();
            cursor.close();

            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("epc", epc);
            values.put("descricao", descricao);
            values.put("status", status);
            values.put("localizacaoId", localizacaoId);

            if (exists) db.update("ativo", values, "id = ?", new String[]{String.valueOf(id)});
            else db.insert("ativo", null, values);

        } catch (Exception e) {
            Log.e("DB", "Erro salvar/atualizar ativo", e);
        } finally {
            db.close();
        }
    }

    public boolean ativoExiste(String epc) {
        if (epc == null || epc.length() < 8) return false;
        String epc8 = epc.substring(0, 8);
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT id FROM ativo WHERE substr(epc, 1, 8) = ?",
                new String[]{epc8}
        );
        boolean existe = cursor.getCount() > 0;
        cursor.close();
        return existe;
    }

    // Dentro do DatabaseHelper
    public JSONObject buscarAtivo(String epc) {
        if (epc == null || epc.length() < 8) return null;
        String epc8 = epc.substring(0, 8); // Pega os 8 primeiros dígitos

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    "ativo",             // tabela
                    null,                // todas as colunas
                    "substr(epc,1,8) = ?", // WHERE usando os 8 primeiros dígitos
                    new String[]{epc8},     // argumento
                    null, null, null
            );

            if (cursor != null && cursor.moveToFirst()) {
                JSONObject ativo = new JSONObject();
                ativo.put("rfid", cursor.getString(cursor.getColumnIndexOrThrow("epc")));
                ativo.put("descricao", cursor.getString(cursor.getColumnIndexOrThrow("descricao")));
                ativo.put("status", cursor.getString(cursor.getColumnIndexOrThrow("status")));
                ativo.put("localizacaoId", cursor.getInt(cursor.getColumnIndexOrThrow("localizacaoId")));

                Log.d("DB_HELPER", "Ativo encontrado no DB: EPC completo="
                        + ativo.getString("rfid") + " | EPC comparacao=" + epc8);

                return ativo;
            } else {
                Log.d("DB_HELPER", "Ativo NÃO encontrado no DB: EPC comparacao=" + epc8);
            }
        } catch (Exception e) {
            Log.e("DB_HELPER", "Erro buscarAtivo", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    public List<JSONObject> buscarAtivosPorLocal(int localId) {
        List<JSONObject> lista = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.query(
                    "ativo",
                    null,
                    "localizacaoId = ?",      // filtro pelo local
                    new String[]{String.valueOf(localId)},
                    null, null, null
            );

            if (cursor != null && cursor.moveToFirst()) {
                // índices das colunas para melhorar performance
                int idxEpc = cursor.getColumnIndexOrThrow("epc");
                int idxDesc = cursor.getColumnIndexOrThrow("descricao");
                int idxStatus = cursor.getColumnIndexOrThrow("status");
                int idxLocal = cursor.getColumnIndexOrThrow("localizacaoId");

                do {
                    JSONObject ativo = new JSONObject();
                    ativo.put("rfid", cursor.getString(idxEpc));
                    ativo.put("descricao", cursor.getString(idxDesc));
                    ativo.put("status", cursor.getString(idxStatus));
                    ativo.put("localizacaoId", cursor.getInt(idxLocal));
                    lista.add(ativo);
                } while (cursor.moveToNext());
            }

            Log.d("DB_HELPER", "Total ativos encontrados para local " + localId + ": " + lista.size());

        } catch (Exception e) {
            Log.e("DB_HELPER", "Erro buscarAtivosPorLocal", e);
        } finally {
            if (cursor != null) cursor.close();
        }

        return lista;
    }
}