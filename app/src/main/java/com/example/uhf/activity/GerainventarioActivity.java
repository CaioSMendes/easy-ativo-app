package com.example.uhf.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GerainventarioActivity extends AppCompatActivity {

    private static final String TAG = "GERA_INVENTARIO";
    private TextView tvResumoUltimoNivel;
    private TextView tvRelatorio;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gerainventario);

        tvResumoUltimoNivel = findViewById(R.id.tvUltimoNivelNome);
        tvRelatorio = findViewById(R.id.tvRelatorio);

        Log.d(TAG, "==============================");
        Log.d(TAG, "Activity iniciada");
        Log.d(TAG, "==============================");

        receberDados();
    }

    private void receberDados() {
        try {
            Log.d(TAG, "Recebendo dados do Intent...");

            String tagsJson = getIntent().getStringExtra("TAGS_LIDAS");
            String localizacaoJson = getIntent().getStringExtra("LOCALIZACAO_JSON");

            Log.d(TAG, "JSON TAGS recebido: " + tagsJson);
            Log.d(TAG, "JSON LOCALIZAÇÃO recebido: " + localizacaoJson);

            if (tagsJson == null || tagsJson.isEmpty()) {
                Log.e(TAG, "Nenhuma TAG recebida.");
                return;
            }

            if (localizacaoJson == null || localizacaoJson.isEmpty()) {
                Log.e(TAG, "Nenhuma LOCALIZAÇÃO recebida.");
                return;
            }

            JSONArray tags = new JSONArray(tagsJson);
            JSONObject localizacao = new JSONObject(localizacaoJson);

            SharedPreferences prefsUser = getSharedPreferences("usuario", Context.MODE_PRIVATE);
            int usuarioID = prefsUser.getInt("id", -1);

            if (usuarioID == -1) {
                Log.e(TAG, "ERRO: usuarioID não encontrado");
                return;
            }

            // Chama método assíncrono para buscar ativos e enviar inventário
            buscarAtivosDaApiETomarInventario(tags, localizacao, usuarioID);

        } catch (Exception e) {
            Log.e(TAG, "ERRO recebendo dados", e);
        }
    }

    private void buscarAtivosDaApiETomarInventario(JSONArray tags, JSONObject localizacaoJson, int usuarioID) {
        new Thread(() -> {
            try {
                String baseUrl = ConfigActivity.getBaseUrl(this);
                String apiUrlAtivos = baseUrl + "mobile/listar_ativo";
                Log.d(TAG, "Buscando ativos na API: " + apiUrlAtivos);

                URL url = new URL(apiUrlAtivos);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int responseCode = conn.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    Log.e(TAG, "Erro ao buscar ativos da API, response code: " + responseCode);
                    return;
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder resposta = new StringBuilder();
                String linha;
                while ((linha = br.readLine()) != null) {
                    resposta.append(linha).append("\n");
                }
                br.close();

                JSONArray ativosApi = new JSONArray(resposta.toString());
                Log.d(TAG, "Ativos da API recebidos: " + ativosApi.length());

                // Montar JSON do inventário com todos os RFIDs lidos
                JSONObject jsonInventario = montarJsonInventario(tags, ativosApi, localizacaoJson, usuarioID);

                // Atualizar a UI
                runOnUiThread(() -> {
                    try {
                        // Atualiza nome do último nível
                        TextView tvUltimoNivelNome = findViewById(R.id.tvUltimoNivelNome);
                        tvUltimoNivelNome.setText(
                                "Itens " + jsonInventario.getString("ultimoNivelNome") +
                                        ": " + jsonInventario.getInt("countItensUltimoNivel")
                        );

                        // Atualiza contadores coloridos
                        TextView tvEncontrados = findViewById(R.id.tvEncontrados);
                        TextView tvMovimentados = findViewById(R.id.tvMovimentados);
                        TextView tvNaoEncontrados = findViewById(R.id.tvNaoEncontrados);

                        tvEncontrados.setText("ENCONTRADOS: " + jsonInventario.getInt("totalEncontrados"));
                        tvMovimentados.setText("MOVIMENTADOS: " + jsonInventario.getInt("totalMovimentados"));
                        tvNaoEncontrados.setText("NÃO ENCONTRADOS: " + jsonInventario.getInt("totalNaoEncontrados"));

                        // Atualiza o TextView de relatório com a resposta da API
                        TextView tvRelatorio = findViewById(R.id.tvRelatorio);
                        tvRelatorio.setText("Relatório gerado:\n" + resposta.toString());

                    } catch (Exception e) {
                        Log.e(TAG, "Erro atualizando a UI", e);
                    }
                });

                // Enviar inventário via POST
                enviarInventario(jsonInventario);

            } catch (Exception e) {
                Log.e(TAG, "Erro no GET de ativos ou montagem de inventário", e);
            }
        }).start();
    }

    private JSONObject montarJsonInventario(JSONArray tags, JSONArray ativosApi, JSONObject localizacaoJson, int usuarioID) {
        JSONObject jsonPrincipal = new JSONObject();
        try {
            long timestamp = System.currentTimeMillis(); // CORRETO

            // Determinar último nível (setor > departamento > unidade)
            JSONObject ultimoNivel;
            if (localizacaoJson.has("setor")) {
                ultimoNivel = localizacaoJson.getJSONObject("setor");
            } else if (localizacaoJson.has("departamento")) {
                ultimoNivel = localizacaoJson.getJSONObject("departamento");
            } else {
                ultimoNivel = localizacaoJson.getJSONObject("unidade");
            }

            int ultimoNivelId = ultimoNivel.getInt("id");
            String ultimoNivelNome = ultimoNivel.getString("nome");

            jsonPrincipal.put("dataAlteracao", timestamp);
            jsonPrincipal.put("localizacaoId", ultimoNivelId);
            jsonPrincipal.put("observacao", "Ordem de servico inventario");
            jsonPrincipal.put("tipo", "INVENTARIO");
            jsonPrincipal.put("usuarioID", usuarioID);

            JSONArray ordemServicoArray = new JSONArray();
            int countItensUltimoNivel = 0;

            // Contadores de status
            int totalEncontrados = 0;
            int totalMovimentados = 0;
            int totalNaoEncontrados = 0;

            // Substitui o loop de tags:
            for (int i = 0; i < tags.length(); i++) {
                JSONObject tagObj      = tags.getJSONObject(i);
                String rfidLido        = tagObj.optString("epc", "");
                String statusVisual    = tagObj.optString("status", "NAO_ENCONTRADO");    // o que aparece na tela
                String statusParaApi   = tagObj.optString("statusApi", "NAO_ENCONTRADO"); // o que vai para API

                int identificadorAtivo = 0;
                int localizacaoAtivo   = ultimoNivelId;

                for (int j = 0; j < ativosApi.length(); j++) {
                    JSONObject ativoApi = ativosApi.getJSONObject(j);
                    if (ativoApi.optString("rfid", "").equals(rfidLido)) {
                        identificadorAtivo = ativoApi.optInt("identificador", 0);
                        localizacaoAtivo   = ativoApi.optInt("localizacaoId", ultimoNivelId);
                        break;
                    }
                }

                // ✅ Contadores usam statusVisual (o que o usuário vê na tela)
                switch (statusVisual) {
                    case "ENCONTRADO":     totalEncontrados++;    break;
                    case "MOVIMENTADO":    totalMovimentados++;   break;  // só conta se usuário confirmou
                    case "NAO_ENCONTRADO": totalNaoEncontrados++; break;
                }

                JSONObject ativo = new JSONObject();
                ativo.put("identificador",      identificadorAtivo);
                ativo.put("localizacaoId",      localizacaoAtivo);
                ativo.put("responsavelAtualId", JSONObject.NULL);
                ativo.put("rfid",               rfidLido);
                ativo.put("vinculo",            JSONObject.NULL);

                JSONObject ordemAtivo = new JSONObject();
                ordemAtivo.put("ativo",             ativo);
                ordemAtivo.put("comoFoiRegistrado", "APLICACAO_MOBILE");
                ordemAtivo.put("dataAlteracao",     timestamp);
                ordemAtivo.put("status",            statusParaApi); // ← API recebe o status protegido
                ordemAtivo.put("usuarioID",         usuarioID);

                ordemServicoArray.put(ordemAtivo);

                Log.d(TAG, "RFID: " + rfidLido
                        + " | Visual: " + statusVisual
                        + " | API: " + statusParaApi
                        + " | Identificador: " + identificadorAtivo
                        + " | Local: " + localizacaoAtivo);
            }

            jsonPrincipal.put("ordemServicoHasAtivos", ordemServicoArray);
            jsonPrincipal.put("ultimoNivelNome", ultimoNivelNome);
            jsonPrincipal.put("countItensUltimoNivel", countItensUltimoNivel);

            // Adiciona totais por status
            jsonPrincipal.put("totalEncontrados", totalEncontrados);
            jsonPrincipal.put("totalMovimentados", totalMovimentados);
            jsonPrincipal.put("totalNaoEncontrados", totalNaoEncontrados);

            Log.d(TAG, "Último nível: " + ultimoNivelNome +
                    " | Itens: " + countItensUltimoNivel +
                    " | ENCONTRADOS: " + totalEncontrados +
                    " | MOVIMENTADOS: " + totalMovimentados +
                    " | NAO ENCONTRADOS: " + totalNaoEncontrados);

        } catch (Exception e) {
            Log.e(TAG, "Erro montando JSON inventario", e);
        }

        return jsonPrincipal;
    }

    private void enviarInventario(JSONObject json) {
        new Thread(() -> {
            try {
                String baseUrl = ConfigActivity.getBaseUrl(this);
                String apiUrl = baseUrl + "mobile/importar_ordem_servico";

                Log.d(TAG, "INICIANDO ENVIO PARA API");

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);

                // Envia o JSON
                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();

                // Lê a resposta da API
                BufferedReader br = new BufferedReader(
                        responseCode >= 200 && responseCode < 300
                                ? new InputStreamReader(conn.getInputStream())
                                : new InputStreamReader(conn.getErrorStream())
                );

                String linha;
                StringBuilder respostaAPI = new StringBuilder();
                while ((linha = br.readLine()) != null) {
                    respostaAPI.append(linha);
                }
                br.close();

                Log.d(TAG, "RESPOSTA DA API:");
                Log.d(TAG, respostaAPI.toString());

                // Atualiza o TextView na UI com a resposta da API
                runOnUiThread(() -> {
                    tvRelatorio.setText("Inventário:" + respostaAPI.toString());
                });

            } catch (Exception e) {
                Log.e(TAG, "ERRO ENVIANDO INVENTÁRIO", e);
                runOnUiThread(() -> tvRelatorio.setText("ERRO ao enviar inventário"));
            }
        }).start();
    }
}