package com.example.uhf.activity;

import static androidx.fragment.app.FragmentManager.TAG;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
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

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.adapter.TagAdapter;
import com.rscja.barcode.BarcodeDecoder;
import com.rscja.barcode.BarcodeFactory;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Easyleituratag extends AppCompatActivity {

    private RFIDWithUHFUART mReader;
    private BarcodeDecoder barcodeDecoder;

    private volatile boolean isReadingRFID = false;
    private volatile boolean isReading2D = false;
    private volatile boolean modoRfid = true;
    private volatile boolean modo2D = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService rfidExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService barcodeExecutor = Executors.newSingleThreadExecutor();

    private ToneGenerator toneGen;

    private List<String> listaTags = new ArrayList<>();
    private List<String> tagsLidas = new ArrayList<>();

    private TagAdapter adapter;

    private TextView tvTagCount;
    private ListView listViewTags;

    private LinearLayout btnLerTags;
    private LinearLayout btnLimparTags;
    private LinearLayout btnDistancia;
    private LinearLayout btnRfid;
    private LinearLayout btnCodBar;
    private LinearLayout btnConcluir;
    private TextView txtBotao;
    private TextView txtRfid;
    private TextView txtCodBar;

    private RadioButton rbLoop;
    private RadioButton rbSingle;

    private long ultimoUpdateUI = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_easyleituratag);
        inicializarComponentes();
        inicializarLeitores();
        configurarListeners();
    }

    private void inicializarComponentes() {

        tvTagCount = findViewById(R.id.tvTagCount);
        listViewTags = findViewById(R.id.listViewTags);

        btnLerTags = findViewById(R.id.btnLerTags);
        btnLimparTags = findViewById(R.id.btnLimparTags);
        btnDistancia = findViewById(R.id.btnDistancia);
        btnConcluir = findViewById(R.id.btnConcluir);

        btnRfid = findViewById(R.id.btnRfid);
        btnCodBar = findViewById(R.id.btnCodBar);

        txtBotao = btnLerTags.findViewById(R.id.txtTituloBotao);
        txtRfid = findViewById(R.id.txtRfid);
        txtCodBar = findViewById(R.id.txtCodBar);

        rbLoop = findViewById(R.id.rbLoop);
        rbSingle = findViewById(R.id.rbSingle);

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        adapter = new TagAdapter(this, listaTags);
        listViewTags.setAdapter(adapter);

    }

    private void concluirInventario() {

        if(listaTags.isEmpty()){
            Toast.makeText(this,"Nenhuma tag lida",Toast.LENGTH_LONG).show();
            return;
        }

        try {

            // pegar localização salva
            SharedPreferences prefs = getSharedPreferences("config", MODE_PRIVATE);
            String localizacaoJson = prefs.getString("LOCALIZACAO_SELECIONADA", "{}");

            // converter lista de tags para JSONArray
            org.json.JSONArray jsonTags = new org.json.JSONArray();

            for(String tag : listaTags){
                jsonTags.put(tag);
            }

            Intent intent = new Intent(Easyleituratag.this, GerainventarioActivity.class);

            intent.putExtra("TAGS_LIDAS", jsonTags.toString());
            intent.putExtra("LOCALIZACAO_JSON", localizacaoJson);

            startActivity(intent);

        } catch (Exception e){

            Log.e("INVENTARIO","Erro enviar dados",e);

        }
    }

    private void inicializarLeitores() {
        rfidExecutor.execute(this::inicializarRFID);
        barcodeExecutor.execute(this::inicializarBarcode2D);
    }

    private void inicializarRFID() {
        try {
            mReader = RFIDWithUHFUART.getInstance();
            if (mReader != null && mReader.init(this)) {
                mReader.setPower(30);
                mainHandler.post(() ->
                        Toast.makeText(this, "RFID conectado", Toast.LENGTH_SHORT).show()
                );
            }

        } catch (Exception e) {
            Log.e("RFID", "Erro inicializando RFID", e);
        }
    }

    private void inicializarBarcode2D() {
        try {
            barcodeDecoder = BarcodeFactory.getInstance().getBarcodeDecoder();
            if (barcodeDecoder.open(this)) {
                configurarCallback2D();
            } else {
                mainHandler.post(() ->
                        Toast.makeText(this, "Falha leitor 2D", Toast.LENGTH_SHORT).show()
                );
            }

        } catch (Exception e) {
            Log.e("BARCODE", "Erro inicializando", e);
        }
    }

    private void configurarCallback2D() {
        barcodeDecoder.setDecodeCallback(barcodeEntity -> {
            if (barcodeEntity.getResultCode() != BarcodeDecoder.DECODE_SUCCESS) return;
            String code = barcodeEntity.getBarcodeData();
            if (code == null || code.trim().isEmpty()) return;
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
            rfidExecutor.execute(() -> adicionarTag(code));
        });
    }

    private void configurarListeners() {
        btnLerTags.setOnClickListener(v -> alternarLeituraPrincipal());
        btnLimparTags.setOnClickListener(v -> limparTags());
        btnDistancia.setOnClickListener(v -> abrirSelecionadorDeDistancia());
        btnRfid.setOnClickListener(v -> trocarModo(true));
        btnCodBar.setOnClickListener(v -> trocarModo(false));
        btnConcluir.setOnClickListener(v -> concluirInventario());
    }

    private void trocarModo(boolean paraRfid) {
        modoRfid = paraRfid;
        modo2D = !paraRfid;
        if (modoRfid) {
            pararLeitura2D();
        } else {
            pararLeituraRFID();
        }
        atualizarEstadoBotoes();
    }

    private void alternarLeituraPrincipal() {
        if (modoRfid) {
            if (isReadingRFID) pararLeituraRFID();
            else iniciarLeituraRFID();
        } else {
            if (isReading2D) pararLeitura2D();
            else iniciarLeitura2D();
        }
    }

    private void iniciarLeituraRFID() {
        if (isReadingRFID) return;
        isReadingRFID = true;
        mainHandler.post(() -> txtBotao.setText("Parar Leitura"));
        rfidExecutor.execute(() -> {
            try {
                mReader.startInventoryTag();
                executarLoopRFID();
            } catch (Exception e) {
                Log.e("RFID", "Erro start RFID", e);
                pararLeituraRFID();
            }
        });
    }

    private void executarLoopRFID() {
        new Thread(() -> {
            while (isReadingRFID) {
                try {
                    UHFTAGInfo tagInfo = mReader.readTagFromBuffer();
                    if (tagInfo != null) {
                        String epc = tagInfo.getEPC();
                        if (epc != null && !epc.trim().isEmpty()) {
                            adicionarTag(epc);
                            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
                        }
                    }
                } catch (Exception e) {
                    Log.e("RFID", "Erro loop leitura", e);
                }
                try {
                    Thread.sleep(80);
                } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    private synchronized void adicionarTag(String tag) {
        if (tag == null) return;
        tag = tag.trim();
        if (tag.isEmpty()) return;
        // pegar apenas os 8 primeiros caracteres
        if (tag.length() > 8) {
            tag = tag.substring(0, 8);
        }
        if (tagsLidas.contains(tag)) return;
        tagsLidas.add(tag);
        listaTags.add(0, tag);
        mainHandler.post(() -> {
            adapter.notifyDataSetChanged();
            tvTagCount.setText("Tags lidas: " + listaTags.size());
            listViewTags.smoothScrollToPosition(0);
        });
    }

    private void pararLeituraRFID() {
        isReadingRFID = false;
        mainHandler.post(() -> txtBotao.setText("Ler Tags"));
        rfidExecutor.execute(() -> {
            try {
                if (mReader != null) mReader.stopInventory();
            } catch (Exception e) {
                Log.e("RFID", "Erro stop", e);
            }
        });
    }

    private void iniciarLeitura2D() {
        if (isReading2D || barcodeDecoder == null) return;
        isReading2D = true;
        mainHandler.post(() -> txtBotao.setText("Parar Leitura"));
        try {
            barcodeDecoder.startScan();
        } catch (Exception e) {
            Log.e("BARCODE", "Erro start", e);
            isReading2D = false;
        }
    }

    private void pararLeitura2D() {
        if (barcodeDecoder == null) return;
        isReading2D = false;
        mainHandler.post(() -> txtBotao.setText("Ler Tags"));
        try {
            barcodeDecoder.stopScan();
        } catch (Exception e) {
            Log.e("BARCODE", "Erro stop", e);
        }
    }

    private void atualizarEstadoBotoes() {
        mainHandler.post(() -> {
            if (modoRfid) {
                btnRfid.setBackgroundTintList(ColorStateList.valueOf(0xFF0288D1));
                txtRfid.setTextColor(android.graphics.Color.WHITE);
                btnCodBar.setBackgroundTintList(ColorStateList.valueOf(0xFFBDBDBD));
                txtCodBar.setTextColor(android.graphics.Color.BLACK);
            } else {
                btnCodBar.setBackgroundTintList(ColorStateList.valueOf(0xFF388E3C));
                txtCodBar.setTextColor(android.graphics.Color.WHITE);
                btnRfid.setBackgroundTintList(ColorStateList.valueOf(0xFFBDBDBD));
                txtRfid.setTextColor(android.graphics.Color.BLACK);
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int triggerKeyCode = 293;
        if (event.getKeyCode() == triggerKeyCode && event.getAction() == KeyEvent.ACTION_DOWN) {
            alternarLeituraPrincipal();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void limparTags() {
        pararLeituraRFID();
        pararLeitura2D();
        synchronized (this) {
            tagsLidas.clear();
            listaTags.clear();
            adapter.notifyDataSetChanged();
            tvTagCount.setText("Tags lidas: 0");
        }

        Toast.makeText(this, "Lista limpa!", Toast.LENGTH_SHORT).show();
    }

    private void abrirSelecionadorDeDistancia() {
        String[] opcoes = {
                "Curta (10 dBm)",
                "Média (20 dBm)",
                "Longa (30 dBm)"
        };
        new android.app.AlertDialog.Builder(this)
                .setTitle("Ajustar Distância")
                .setItems(opcoes, (dialog, which) -> {
                    int power = which == 0 ? 10 : which == 1 ? 20 : 30;
                    rfidExecutor.execute(() -> {
                        try {
                            if (mReader != null && mReader.setPower(power)) {
                                mainHandler.post(() ->
                                        Toast.makeText(this, "Potência " + power + " dBm", Toast.LENGTH_SHORT).show()
                                );
                            }
                        } catch (Exception e) {
                            Log.e("RFID", "Erro potência", e);
                        }
                    });
                }).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pararLeituraRFID();
        pararLeitura2D();
        rfidExecutor.shutdown();
        barcodeExecutor.shutdown();
        try {

            if (barcodeDecoder != null) barcodeDecoder.close();

        } catch (Exception ignored) {
        }
    }
}