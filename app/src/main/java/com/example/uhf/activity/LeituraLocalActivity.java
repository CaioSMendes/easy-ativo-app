package com.example.uhf.activity;

import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import com.rscja.barcode.BarcodeDecoder;
import com.rscja.barcode.BarcodeFactory;
import android.content.res.ColorStateList;
import android.graphics.Color;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.adapter.TagItemAdapter;
import com.example.uhf.database.DatabaseHelper;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LeituraLocalActivity extends AppCompatActivity {

    private static final String TAG = "LEITURA_LOCAL";

    private RFIDWithUHFUART mReader;
    private volatile boolean isReadingRFID = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService rfidExecutor = Executors.newSingleThreadExecutor();
    private ToneGenerator toneGen;

    private final List<TagItem> listaTags = new ArrayList<>();
    private TagItemAdapter adapter;

    private TextView tvTagCount, tvTotal, tvEncontrado, tvNaoEncontrado, tvMovimentado, tvNomeLocal, tvQtdLocal;
    private ListView listViewTags;

    private LinearLayout btnLerTags;
    private LinearLayout btnLimparTags;
    private LinearLayout btnConcluir;
    private LinearLayout btnRelatorio;
    private LinearLayout btnDistancia;
    private LinearLayout btnRfid;
    private LinearLayout btnCodBar;
    private RadioButton rbLoop, rbSingle;
    private JSONObject localizacaoSelecionada;
    private boolean modoSingle = false;
    private BarcodeDecoder barcodeDecoder;
    private volatile boolean isReading2D = false;
    private volatile boolean modoRfid = true;
    private volatile boolean modo2D = false;
    private final ExecutorService barcodeExecutor = Executors.newSingleThreadExecutor();
    private TextView txtRfid, txtCodBar;
    private final Map<Integer, JSONObject> mapaCampos   = new HashMap<>();
    private final Map<Integer, JSONObject> mapaNiveis   = new HashMap<>();
    private final Map<Integer, String> mapaLocais = new HashMap<>();

    // chave = epcChave (8 chars) → TagItem que está na listaTags
    private final Map<String, TagItem>    mapaTags      = new HashMap<>();

    // chave = epcChave (8 chars) → JSONObject da API
    private final Map<String, JSONObject> mapaTodosAtivos = new HashMap<>();
    private long ultimoUpdateUI = 0;
    private int tagsPendentes = 0;
    private DatabaseHelper dbHelper;

    // ===========================
    // TAG ITEM
    // ===========================
    public static class TagItem {
        public String epc;
        public String epcChave;
        public int    localizacaoId;
        public String status;
        public String descricao;
        public boolean statusConfirmado = false; // ✅ false = lido pelo RFID, true = confirmado pelo usuário

        public TagItem(String epcCompleto, int localizacaoId) {
            this.epc           = epcCompleto;
            this.epcChave      = epcCompleto.length() >= 8 ? epcCompleto.substring(0, 8) : epcCompleto;
            this.localizacaoId = localizacaoId;
            this.status        = "NO LOCAL";
            this.descricao     = "";
            this.statusConfirmado = false;
        }
    }

    // ===========================
    // onCreate
    // ===========================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.leituralocal);

        dbHelper = new DatabaseHelper(this, "uhf_db");

        tvTagCount      = findViewById(R.id.tvTagCount);
        listViewTags    = findViewById(R.id.listViewTags);
        btnLerTags      = findViewById(R.id.btnLerTags);
        btnLimparTags   = findViewById(R.id.btnLimparTags);
        btnConcluir     = findViewById(R.id.btnConcluir);
        btnRelatorio     = findViewById(R.id.btnRelatorio);
        tvEncontrado    = findViewById(R.id.tvEncontrado);
        tvNaoEncontrado = findViewById(R.id.tvNaoEncontrado);
        tvMovimentado   = findViewById(R.id.tvMovimentado);
        tvNomeLocal     = findViewById(R.id.tvNomeLocal);
        tvQtdLocal      = findViewById(R.id.tvQtdLocal);
        tvTotal         = findViewById(R.id.tvTotal);
        btnDistancia    = findViewById(R.id.btnDistancia);
        rbLoop          = findViewById(R.id.rbLoop);
        rbSingle        = findViewById(R.id.rbSingle);
        btnRfid         = findViewById(R.id.btnRfid);
        btnCodBar       = findViewById(R.id.btnCodBar);
        txtRfid         = findViewById(R.id.txtRfid);
        txtCodBar       = findViewById(R.id.txtCodBar);


        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        adapter = new TagItemAdapter(this, listaTags, mapaLocais);
        listViewTags.setAdapter(adapter);

        String localJson = getIntent().getStringExtra("LOCALIZACAO_JSON");
        if (localJson != null) {
            try {
                localizacaoSelecionada = new JSONObject(localJson);
            } catch (Exception e) {
                Log.e(TAG, "Erro parse localização", e);
            }
        }

        if (localizacaoSelecionada != null) {
            tvNomeLocal.setText("Local: " + obterNomeLocal(localizacaoSelecionada));
            salvarLocalizacaoNoBanco(localizacaoSelecionada);

            // Tudo sequenciado: banco → API → RFID liberado
            rfidExecutor.execute(() -> {
                int localId = obterUltimaLocalizacao(localizacaoSelecionada);
                carregarAtivosDoBancoSync(localId);   // passo 1
                carregarCamposEAtivosSync();            // passo 2
            });
        }

        btnLerTags.setOnClickListener(v -> alternarLeituraPrincipal());
        btnLimparTags.setOnClickListener(v -> limparTags());
        btnDistancia.setOnClickListener(v -> abrirSelecionadorDeDistancia());
        btnConcluir.setOnClickListener(v -> concluirInventario());
        listViewTags.setOnItemClickListener((parent, view, position, id) -> {
            TagItem tag = listaTags.get(position);
            mostrarOpcoesStatus(tag);
        });

        rbLoop.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) modoSingle = false;
        });

        rbSingle.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) modoSingle = true;
        });

        btnRelatorio.setOnClickListener(v -> {
            // Monta JSON só com tags lidas (ENCONTRADO ou MOVIMENTADO)
            JSONArray tagsLidas = new JSONArray();
            for (TagItem tag : listaTags) {
                if ("ENCONTRADO".equals(tag.status) || "MOVIMENTADO".equals(tag.status)) {
                    try {
                        JSONObject obj = new JSONObject();
                        obj.put("descricao", tag.descricao);
                        obj.put("status",    tag.status);
                        tagsLidas.put(obj);
                    } catch (Exception e) {
                        Log.e(TAG, "Erro montar tag relatorio", e);
                    }
                }
            }

            if (tagsLidas.length() == 0) {
                Toast.makeText(this, "Nenhuma tag lida ainda!", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(this, GeraRelatorioActivity.class);
            intent.putExtra("TAGS_LIDAS",  tagsLidas.toString());
            intent.putExtra("nomeLocal",   obterNomeLocal(localizacaoSelecionada));
            intent.putExtra("localId",     obterUltimaLocalizacao(localizacaoSelecionada));
            startActivity(intent);
        });

        btnRfid.setOnClickListener(v -> trocarModo(true));

        btnCodBar.setOnClickListener(v -> trocarModo(false));

        //inicializarRFID();
        rfidExecutor.execute(this::inicializarRFID);
        barcodeExecutor.execute(this::inicializarBarcode2D);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int triggerKeyCode = 293;
        if (event.getKeyCode() == triggerKeyCode
                && event.getAction() == KeyEvent.ACTION_DOWN) {

            if (modoSingle) {
                // Single: dispara 250ms e para
                if (!isReadingRFID && !isReading2D) {
                    alternarLeituraPrincipal();
                    mainHandler.postDelayed(this::alternarLeituraPrincipal, 250);
                }
            } else {
                alternarLeituraPrincipal();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void trocarModo(boolean paraRfid) {
        modoRfid = paraRfid;
        modo2D   = !paraRfid;

        if (modoRfid) pararLeitura2D();
        else          pararLeituraRFID();

        atualizarEstadoBotoes();
    }

    private void alternarLeituraPrincipal() {
        if (modoRfid) {
            if (isReadingRFID) pararLeituraRFID();
            else               iniciarLeituraRFID();
        } else {
            if (isReading2D) pararLeitura2D();
            else             iniciarLeitura2D();
        }
    }

    private void atualizarEstadoBotoes() {
        mainHandler.post(() -> {
            if (modoRfid) {
                btnRfid.setBackgroundTintList(ColorStateList.valueOf(0xFF0288D1));
                txtRfid.setTextColor(Color.WHITE);
                btnCodBar.setBackgroundTintList(ColorStateList.valueOf(0xFFBDBDBD));
                txtCodBar.setTextColor(Color.BLACK);
            } else {
                btnCodBar.setBackgroundTintList(ColorStateList.valueOf(0xFF388E3C));
                txtCodBar.setTextColor(Color.WHITE);
                btnRfid.setBackgroundTintList(ColorStateList.valueOf(0xFFBDBDBD));
                txtRfid.setTextColor(Color.BLACK);
            }
        });
    }

    // ===== BARCODE 2D =====
    private void inicializarBarcode2D() {
        try {
            barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder();
            if (barcodeDecoder.open(this)) {
                configurarCallback2D();
            } else {
                mainHandler.post(() ->
                        Toast.makeText(this, "Falha ao abrir leitor 2D", Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro inicializando 2D", e);
        }
    }

    private void configurarCallback2D() {
        barcodeDecoder.setDecodeCallback(barcodeEntity -> {
            if (barcodeEntity.getResultCode() != BarcodeDecoder.DECODE_SUCCESS) return;

            String code     = barcodeEntity.getBarcodeData();
            String epcLida  = normalizarEpc(code);

            if (epcLida == null || epcLida.trim().isEmpty()) return;

            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100);

            // Reutiliza a mesma lógica de status do RFID
            rfidExecutor.execute(() -> processarTagLida(epcLida));
        });
    }

    private void iniciarLeitura2D() {
        if (isReading2D || barcodeDecoder == null) return;
        isReading2D = true;

        try {
            barcodeDecoder.startScan();
        } catch (Exception e) {
            Log.e(TAG, "Erro iniciando 2D", e);
            isReading2D = false;
        }
    }

    private void pararLeitura2D() {
        if (barcodeDecoder == null) return;
        isReading2D = false;

        try {
            barcodeDecoder.stopScan();
        } catch (Exception e) {
            Log.e(TAG, "Erro parando 2D", e);
        }
    }

    // ===========================
    // PASSO 1 — BANCO (síncrono no executor)
    // ===========================
    private void carregarAtivosDoBancoSync(int localIdSelecionado) {
        try {
            List<JSONObject> ativosNoLocal = dbHelper.buscarAtivosPorLocal(localIdSelecionado);

            listaTags.clear();
            mapaTags.clear();

            for (JSONObject ativoDB : ativosNoLocal) {
                String epcCompleto = normalizarEpc(ativoDB.optString("rfid", ""));
                if (epcCompleto.isEmpty()) continue;

                String chave     = epcChave(epcCompleto);
                String descricao = ativoDB.optString("descricao", "Sem descrição");

                TagItem tag = new TagItem(epcCompleto, localIdSelecionado);
                tag.descricao = descricao;
                tag.status    = "NO LOCAL";

                listaTags.add(tag);
                mapaTags.put(chave, tag);
            }

            // ✅ Monta mapaLocais com nomes reais do banco
            for (TagItem tag : listaTags) {
                if (!mapaLocais.containsKey(tag.localizacaoId)) {
                    String nome = dbHelper.buscarNomeLocalPorId(tag.localizacaoId);
                    mapaLocais.put(tag.localizacaoId, nome);
                }
            }

            Log.d(TAG, "Banco carregado: " + listaTags.size() + " itens para local " + localIdSelecionado);

            mainHandler.post(() -> {
                adapter.notifyDataSetChanged();
                atualizarContador();
                tvQtdLocal.setText("Total de itens: " + listaTags.size());
                ajustarAlturaListView();
            });

        } catch (Exception e) {
            Log.e(TAG, "Erro carregar banco", e);
        }
    }

    private void abrirSelecionadorDeDistancia() {
        String[] opcoes = {"Curta (10 dBm)", "Média (20 dBm)", "Longa (30 dBm)"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("Ajustar Distância de Leitura")
                .setItems(opcoes, (dialog, which) -> {
                    int power = which == 0 ? 10 : which == 1 ? 20 : 30;
                    rfidExecutor.execute(() -> {
                        try {
                            if (mReader != null && mReader.setPower(power)) {
                                mainHandler.post(() ->
                                        Toast.makeText(this,
                                                "Potência ajustada: " + power + " dBm",
                                                Toast.LENGTH_SHORT).show()
                                );
                            }
                        } catch (Exception e) {
                            mainHandler.post(() ->
                                    Toast.makeText(this,
                                            "Erro ao ajustar potência: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show()
                            );
                        }
                    });
                })
                .show();
    }

    // ===========================
    // PASSO 2 — API (síncrono no executor, roda depois do banco)
    // ===========================
    private void carregarCamposEAtivosSync() {
        try {
            String baseUrl = ConfigActivity.getBaseUrl(this);
            int localIdSelecionado = obterUltimaLocalizacao(localizacaoSelecionada);

            // ✅ Busca localizações da API para montar mapaLocais com nomes reais
            JSONArray localizacoesApi = lerUrl(baseUrl + "mobile/listar_localizacao");
            mapaLocais.clear();
            for (int i = 0; i < localizacoesApi.length(); i++) {
                JSONObject loc = localizacoesApi.getJSONObject(i);
                int idLoc   = loc.optInt("id", -1);
                String nome = loc.optString("nome", "Local " + idLoc);
                if (idLoc != -1) {
                    mapaLocais.put(idLoc, nome);
                    dbHelper.salvarOuAtualizarLocalizacao(loc, "LOCAL");
                }
            }

            JSONArray arrCampos = lerUrl(baseUrl + "mobile/listar_campo_multi_valor");
            JSONArray arrNiveis = lerUrl(baseUrl + "mobile/listar_nivel_campo_multi_valor");

            mapaCampos.clear();
            mapaNiveis.clear();
            for (int i = 0; i < arrCampos.length(); i++) {
                JSONObject c = arrCampos.getJSONObject(i);
                mapaCampos.put(c.getInt("id"), c);
            }
            for (int i = 0; i < arrNiveis.length(); i++) {
                JSONObject n = arrNiveis.getJSONObject(i);
                mapaNiveis.put(n.getInt("id"), n);
            }

            JSONArray ativosApi = lerUrl(baseUrl + "mobile/listar_ativo");

            mapaTodosAtivos.clear();
            for (int i = 0; i < ativosApi.length(); i++) {
                JSONObject ativo = ativosApi.getJSONObject(i);
                String epc = normalizarEpc(ativo.optString("rfid", ""));
                if (epc.length() >= 8) {
                    mapaTodosAtivos.put(epcChave(epc), ativo);
                }
            }

            Log.d(TAG, "API carregada: " + mapaTodosAtivos.size() + " ativos");

            for (Map.Entry<String, JSONObject> entry : mapaTodosAtivos.entrySet()) {
                String     chave        = entry.getKey();
                JSONObject ativo        = entry.getValue();
                int        localIdAtivo = ativo.optInt("localizacaoId", -1);
                String     epcCompleto  = normalizarEpc(ativo.optString("rfid", ""));

                JSONArray listaMulti = ativo.optJSONArray("listaIdCampoMultiValor");
                String descricao = (listaMulti != null && listaMulti.length() > 0)
                        ? descobrirDescricao(listaMulti)
                        : ativo.optString("descricao", "Sem descrição");

                JSONObject ativoDB = new JSONObject();
                ativoDB.put("id",           ativo.optInt("id"));
                ativoDB.put("rfid",         epcCompleto);
                ativoDB.put("descricao",    descricao);
                ativoDB.put("status",       "NO LOCAL");
                ativoDB.put("localizacaoId", localIdAtivo);
                dbHelper.salvarOuAtualizarAtivoSimples(ativoDB);

                if (localIdAtivo == localIdSelecionado) {
                    if (mapaTags.containsKey(chave)) {
                        mapaTags.get(chave).descricao = descricao;
                    } else {
                        TagItem tag = new TagItem(epcCompleto, localIdAtivo);
                        tag.descricao = descricao;
                        tag.status    = "NO LOCAL";
                        listaTags.add(tag);
                        mapaTags.put(chave, tag);
                        Log.d(TAG, "➕ Novo do local via API: " + chave);
                    }
                }
            }

            for (TagItem tag : listaTags) {
                if ("NO LOCAL".equals(tag.status) && !mapaTodosAtivos.containsKey(tag.epcChave)) {
                    tag.status = "NAO_ENCONTRADO";
                    Log.d(TAG, "Não existe na API → NAO ENCONTRADO: " + tag.epcChave);
                }
            }

            Log.d(TAG, "Após sync API — lista total: " + listaTags.size());

            mainHandler.post(() -> {
                adapter.notifyDataSetChanged();
                atualizarContador();
                tvQtdLocal.setText("Total de itens: " + listaTags.size());
                ajustarAlturaListView();
            });

        } catch (Exception e) {
            Log.e(TAG, "Erro carregar API", e);
        }
    }

    private void mostrarOpcoesStatus(TagItem tag) {
        String[] opcoes;
        switch (tag.status) {
            case "NO LOCAL":
                opcoes = new String[]{
                        "✅ ENCONTRADO",
                        "\uD83D\uDD04 MOVIMENTADO",
                        "❌ NÃO ENCONTRADO"
                };
                break;
            case "NAO_ENCONTRADO":
                opcoes = new String[]{
                        "✅ ENCONTRADO",
                        "\uD83D\uDD04 MOVIMENTADO"
                };
                break;
            case "MOVIMENTADO":
                opcoes = new String[]{
                        "🔄 Confirmar MOVIMENTADO", // ← confirmação explícita
                        "❌ NÃO ENCONTRADO",
                        "✅ ENCONTRADO"
                };
                break;
            case "ENCONTRADO":
                opcoes = new String[]{
                        "\uD83D\uDD04 MOVIMENTADO",
                        "❌ NÃO ENCONTRADO"
                };
                break;
            default:
                opcoes = new String[]{
                        "✅ ENCONTRADO",
                        "\uD83D\uDD04 MOVIMENTADO",
                        "❌ NÃO ENCONTRADO"
                };
                break;
        }

        new AlertDialog.Builder(this)
                .setTitle("EPC: " + tag.epc + "\n" + tag.descricao)
                .setItems(opcoes, (dialog, which) -> {
                    String opcao = opcoes[which];

                    if (opcao.contains("MOVIMENTADO")) {
                        // ✅ MOVIMENTADO exige confirmação extra
                        confirmarMovimentado(tag);
                    } else {
                        // ENCONTRADO ou NAO_ENCONTRADO — aplica direto
                        String novoStatus = (opcao.contains("ENCONTRADO") && !opcao.contains("NÃO"))
                                ? "ENCONTRADO" : "NAO_ENCONTRADO";

                        tag.status           = novoStatus;
                        tag.statusConfirmado = true;

                        Log.d(TAG, "✏️ Reclassificado: " + tag.epcChave + " → " + novoStatus);

                        salvarStatusNoBanco(tag, novoStatus);
                        adapter.notifyDataSetChanged();
                        atualizarContador();
                        ajustarAlturaListView();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmarMovimentado(TagItem tag) {
        // Nome do local de origem (onde o ativo estava cadastrado)
        String nomeLocalOrigem = mapaLocais.containsKey(tag.localizacaoId)
                ? mapaLocais.get(tag.localizacaoId)
                : "Local " + tag.localizacaoId;

        // Nome do local de destino (local que está sendo inventariado agora)
        int localDestinoId = obterUltimaLocalizacao(localizacaoSelecionada);
        String nomeLocalDestino = mapaLocais.containsKey(localDestinoId)
                ? mapaLocais.get(localDestinoId)
                : obterNomeLocal(localizacaoSelecionada);

        new AlertDialog.Builder(this)
                .setTitle("Confirmar Movimentação")
                .setMessage("Deseja realmente marcar como MOVIMENTADO?\n\n"
                        + tag.descricao + "\nEPC: " + tag.epc
                        + "\n\n📍 Local de origem: " + nomeLocalOrigem
                        + "\n➡️ Sendo encontrado em: " + nomeLocalDestino
                        + "\n\nEsta ação irá atualizar a localização do ativo.")
                .setPositiveButton("Sim, confirmar", (dialog, which) -> {
                    tag.status           = "MOVIMENTADO";
                    tag.statusConfirmado = true;

                    Log.d(TAG, "✏️ MOVIMENTADO confirmado: " + tag.epcChave);

                    salvarStatusNoBanco(tag, "MOVIMENTADO");
                    adapter.notifyDataSetChanged();
                    atualizarContador();
                    ajustarAlturaListView();

                    Toast.makeText(this, "Item marcado como MOVIMENTADO", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void salvarStatusNoBanco(TagItem tag, String status) {
        try {
            JSONObject salvar   = new JSONObject();
            JSONObject ativoApi = mapaTodosAtivos.get(tag.epcChave);
            if (ativoApi != null) salvar.put("id", ativoApi.optInt("id"));
            salvar.put("rfid",          tag.epc);
            salvar.put("descricao",     tag.descricao);
            // ✅ Só salva MOVIMENTADO se confirmado
            String statusReal = ("MOVIMENTADO".equals(status) && !tag.statusConfirmado)
                    ? "NAO_ENCONTRADO" : status;
            salvar.put("status",        statusReal);
            salvar.put("localizacaoId", tag.localizacaoId);
            dbHelper.salvarOuAtualizarAtivoSimples(salvar);
        } catch (Exception e) {
            Log.e(TAG, "Erro salvar status no banco", e);
        }
    }

    // ===========================
    // LEITURA RFID
    // ===========================
    private void inicializarRFID() {
        rfidExecutor.execute(() -> {
            try {
                mReader = RFIDWithUHFUART.getInstance();
                if (mReader != null && mReader.init(this)) {
                    mReader.setPower(30);
                    mainHandler.post(() -> Toast.makeText(this, "RFID conectado", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro RFID", e);
            }
        });
    }

    private void alternarLeituraRFID() {
        if (isReadingRFID) pararLeituraRFID();
        else iniciarLeituraRFID();
    }

    private void iniciarLeituraRFID() {
        if (isReadingRFID || mReader == null) return;
        isReadingRFID = true;

        rfidExecutor.execute(() -> {
            try {
                mReader.startInventoryTag();
                while (isReadingRFID) {
                    UHFTAGInfo tagInfo = mReader.readTagFromBuffer();
                    if (tagInfo != null) {
                        String epc = normalizarEpc(tagInfo.getEPC());
                        if (!epc.isEmpty()) {
                            processarTagLida(epc);
                            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
                        }
                    }
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro leitura RFID", e);
            }
        });
        // Se modo single: para automaticamente após 250ms
        if (modoSingle) {
            mainHandler.postDelayed(this::pararLeituraRFID, 250);
        }
    }

    private void pararLeituraRFID() {
        isReadingRFID = false;
        rfidExecutor.execute(() -> {
            try {
                if (mReader != null) mReader.stopInventory();
            } catch (Exception e) {
                Log.e(TAG, "Erro parar RFID", e);
            }

            // ✅ Tudo que não foi lido fisicamente → NAO ENCONTRADO
            for (TagItem tag : listaTags) {
                if ("NO LOCAL".equals(tag.status)) {
                    tag.status = "NAO_ENCONTRADO";
                    Log.d(TAG, "Não lido → NAO ENCONTRADO: " + tag.epcChave);

                    // Persiste no banco
                    try {
                        JSONObject salvar = new JSONObject();
                        JSONObject ativoApi = mapaTodosAtivos.get(tag.epcChave);
                        if (ativoApi != null) salvar.put("id", ativoApi.optInt("id"));
                        salvar.put("rfid",          tag.epc);
                        salvar.put("descricao",     tag.descricao);
                        salvar.put("status",        "NAO_ENCONTRADO");
                        salvar.put("localizacaoId", tag.localizacaoId);
                        dbHelper.salvarOuAtualizarAtivoSimples(salvar);
                    } catch (Exception e) {
                        Log.e(TAG, "Erro salvar NAO ENCONTRADO", e);
                    }
                }
            }

            mainHandler.post(() -> {
                adapter.notifyDataSetChanged();
                atualizarContador();
                ajustarAlturaListView();
            });
        });
    }

    // ===========================
    // PROCESSAR TAG LIDA — regras de status
    // ===========================
    private synchronized void processarTagLida(String epcLida) {
        String chave            = epcChave(epcLida);
        int    localSelecionado = obterUltimaLocalizacao(localizacaoSelecionada);

        JSONObject ativoApi = mapaTodosAtivos.get(chave);

        String status;
        String descricao;
        int    localBanco;

        if (ativoApi != null) {
            localBanco = ativoApi.optInt("localizacaoId", -1);

            JSONArray listaMulti = ativoApi.optJSONArray("listaIdCampoMultiValor");
            descricao = (listaMulti != null && listaMulti.length() > 0)
                    ? descobrirDescricao(listaMulti)
                    : ativoApi.optString("descricao", "Sem descrição");

            // ✅ REGRA 1: mesmo local → ENCONTRADO
            // ✅ REGRA 2: local diferente → MOVIMENTADO
            status = (localBanco == localSelecionado) ? "ENCONTRADO" : "MOVIMENTADO";

        } else {
            // ✅ REGRA 3: não existe na API → NAO ENCONTRADO
            descricao  = "TAG NÃO CADASTRADA";
            status     = "NAO_ENCONTRADO";
            localBanco = localSelecionado;
        }

        // ✅ Atualiza objeto existente na lista (sem duplicata)
        TagItem tagExistente = mapaTags.get(chave);
        if (tagExistente != null) {
            // ✅ Só atualiza status se não foi confirmado pelo usuário via dialog
            if (!tagExistente.statusConfirmado) {
                tagExistente.status = status;
            }
            tagExistente.descricao     = descricao;
            tagExistente.localizacaoId = localBanco;
            Log.d(TAG, "✅ ATUALIZADO: " + chave + " → " + tagExistente.status);
        } else {
            // Não estava na lista (ex: movimentada de outro local, ou não cadastrada)
            TagItem nova = new TagItem(chave, localBanco);
            nova.descricao        = descricao;
            nova.status           = status;
            nova.statusConfirmado = false; // ← RFID automático, nunca confirmado
            listaTags.add(nova);
            mapaTags.put(chave, nova);
            Log.d(TAG, "➕ NOVA TAG LIDA: " + chave + " → " + status);
        }

        // Persiste status atualizado no banco
        try {
            JSONObject salvar = new JSONObject();
            if (ativoApi != null) salvar.put("id", ativoApi.optInt("id"));
            salvar.put("rfid",          chave);
            salvar.put("descricao",     descricao);
            // ✅ Se MOVIMENTADO sem confirmação → salva como NAO_ENCONTRADO no banco
            String statusParaSalvar = ("MOVIMENTADO".equals(status)) ? "NAO_ENCONTRADO" : status;
            salvar.put("status",        statusParaSalvar);
            salvar.put("localizacaoId", localBanco);
            dbHelper.salvarOuAtualizarAtivoSimples(salvar);
        } catch (Exception e) {
            Log.e(TAG, "Erro salvar no banco", e);
        }

        tagsPendentes++;

        long agora = System.currentTimeMillis();
        if (agora - ultimoUpdateUI >= 300) { // atualiza no máximo a cada 300ms
            ultimoUpdateUI = agora;
            tagsPendentes  = 0;
            mainHandler.post(() -> {
                adapter.notifyDataSetChanged();
                atualizarContador();
                ajustarAlturaListView();
            });
        }
    }

    // ===========================
    // UI
    // ===========================
    private void limparTags() {
        pararLeituraRFID();
        listaTags.clear();
        mapaTags.clear();
        adapter.notifyDataSetChanged();
        atualizarContador();
        ajustarAlturaListView();
        Toast.makeText(this, "Lista limpa", Toast.LENGTH_SHORT).show();
    }

    private void ajustarAlturaListView() {
        if (adapter == null || adapter.getCount() == 0) {
            listViewTags.getLayoutParams().height = 1;
            listViewTags.requestLayout();
            return;
        }

        // Mede só o primeiro item e multiplica — muito mais rápido
        android.view.View item = adapter.getView(0, null, listViewTags);
        item.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(
                        listViewTags.getWidth() > 0 ? listViewTags.getWidth() : 1080,
                        android.view.View.MeasureSpec.EXACTLY
                ),
                android.view.View.MeasureSpec.makeMeasureSpec(
                        0,
                        android.view.View.MeasureSpec.UNSPECIFIED
                )
        );

        int alturaItem   = item.getMeasuredHeight();
        int itens        = adapter.getCount();
        int alturaTotal = (alturaItem * itens) + ((itens - 1) * 2) + 32; // ← linha corrigida

        listViewTags.getLayoutParams().height = alturaTotal;
        listViewTags.requestLayout();
    }
    private void atualizarContador() {
        int encontrado = 0, naoEncontrado = 0, movimentado = 0, noLocal = 0;
        for (TagItem tag : listaTags) {
            switch (tag.status) {
                case "ENCONTRADO":    encontrado++;    break;
                case "MOVIMENTADO":   movimentado++;   break;
                case "NAO_ENCONTRADO": naoEncontrado++; break;
                case "NO LOCAL":      noLocal++;       break;
            }
        }
        int total = listaTags.size();
        int lidas = encontrado + movimentado + naoEncontrado;
        tvTagCount.setText("Tags lidas: " + lidas);
        tvTotal.setText(String.valueOf(total));
        tvEncontrado.setText(String.valueOf(encontrado));
        tvNaoEncontrado.setText(String.valueOf(naoEncontrado));
        tvMovimentado.setText(String.valueOf(movimentado));
    }

    private void concluirInventario() {
        pararLeituraRFID();

        JSONArray tagsArray = new JSONArray();
        for (TagItem tag : listaTags) {
            if ("ENCONTRADO".equals(tag.status)
                    || "MOVIMENTADO".equals(tag.status)
                    || "NAO_ENCONTRADO".equals(tag.status)) {

                try {
                    String statusParaApi;

                    if ("MOVIMENTADO".equals(tag.status)) {
                        if (tag.statusConfirmado) {
                            // ✅ Usuário confirmou pelo popup → manda MOVIMENTADO de verdade
                            statusParaApi = "MOVIMENTADO";
                        } else {
                            // ✅ Lido pelo RFID sem confirmação → protege e manda NAO_ENCONTRADO
                            statusParaApi = "NAO_ENCONTRADO"; // 🔒 não confirmado → protege
                            tag.status    = "NAO_ENCONTRADO"; // ✅ corrige o status visual também
                            Log.d(TAG, "🔒 PROTEGIDO: " + tag.epcChave
                                    + " era MOVIMENTADO sem confirmação → NAO_ENCONTRADO");
                        }
                    } else {
                        statusParaApi = tag.status; // ENCONTRADO ou NAO_ENCONTRADO → vai como está
                    }

                    JSONObject obj = new JSONObject();
                    obj.put("epc",       tag.epc);
                    obj.put("status",    tag.status);    // status visual na tela
                    obj.put("statusApi", statusParaApi); // status que vai para API

                    tagsArray.put(obj);

                } catch (Exception e) {
                    Log.e(TAG, "Erro montar tag para envio", e);
                }
            }
        }

        Intent intent = new Intent(this, GerainventarioActivity.class);
        intent.putExtra("TAGS_LIDAS",       tagsArray.toString());
        intent.putExtra("LOCALIZACAO_JSON", localizacaoSelecionada.toString());
        startActivity(intent);
        finish();
    }

    // ===========================
    // AUXILIARES
    // ===========================

    /** Retorna os 8 primeiros chars do EPC normalizado — chave única do mapa */
    private String epcChave(String epc) {
        epc = normalizarEpc(epc);
        return epc.length() >= 8 ? epc.substring(0, 8) : epc;
    }

    private String normalizarEpc(String epc) {
        if (epc == null) return "";
        return epc.trim();
    }

    private JSONArray lerUrl(String url) throws Exception {
        return new JSONArray(
                new Scanner(new java.net.URL(url).openStream())
                        .useDelimiter("\\A").next()
        );
    }

    private void salvarLocalizacaoNoBanco(JSONObject local) {
        try {
            dbHelper.salvarOuAtualizarLocalizacao(local, "LOCAL");
        } catch (Exception e) {
            Log.e(TAG, "Erro salvar local no DB", e);
        }
    }

    private String obterNomeLocal(JSONObject local) {
        try {
            if (local.has("setor"))       return local.getJSONObject("setor").optString("nome", "");
            if (local.has("departamento")) return local.getJSONObject("departamento").optString("nome", "");
            if (local.has("unidade"))     return local.getJSONObject("unidade").optString("nome", "");
        } catch (Exception e) {
            Log.e(TAG, "Erro nome local", e);
        }
        return "Desconhecido";
    }

    private int obterUltimaLocalizacao(JSONObject local) {
        try {
            if (local == null) return -1;
            if (local.has("setor"))       return local.getJSONObject("setor").optInt("id", -1);
            if (local.has("departamento")) return local.getJSONObject("departamento").optInt("id", -1);
            if (local.has("unidade"))     return local.getJSONObject("unidade").optInt("id", -1);
        } catch (Exception e) {
            Log.e(TAG, "Erro obter localização", e);
        }
        return -1;
    }

    private String descobrirDescricao(JSONArray listaIds) {
        try {
            if (listaIds == null || listaIds.length() == 0) return "Sem descrição";
            String fallback = "";
            for (int i = 0; i < listaIds.length(); i++) {
                int        idCampo = listaIds.getInt(i);
                JSONObject campo   = mapaCampos.get(idCampo);
                if (campo == null) continue;

                int        idNivel = campo.optInt("idNivelCampoMultiValor", -1);
                JSONObject nivel   = mapaNiveis.get(idNivel);
                if (nivel == null) continue;

                boolean principal = nivel.optBoolean("principal", false);
                String  nomeNivel = nivel.optString("nome", "");
                String  nomeCampo = campo.optString("nome", "");

                if (principal || "Descrição".equalsIgnoreCase(nomeNivel)) return nomeCampo;
                if (fallback.isEmpty() && !nomeCampo.isEmpty()) fallback = nomeCampo;
            }
            if (!fallback.isEmpty()) return fallback;
        } catch (Exception e) {
            Log.e(TAG, "Erro descobrir descrição", e);
        }
        return "Sem descrição";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 1. Para as leituras primeiro
        pararLeituraRFID();
        pararLeitura2D();

        // 2. Libera os executors
        rfidExecutor.shutdownNow();
        barcodeExecutor.shutdownNow();

        // 3. Libera o hardware
        if (mReader != null) mReader.free();
        toneGen.release();

        // 4. Fecha o barcode
        try {
            if (barcodeDecoder != null) barcodeDecoder.close();
        } catch (Exception ignored) {}
    }
}