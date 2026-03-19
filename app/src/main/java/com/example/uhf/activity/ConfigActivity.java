package com.example.uhf.activity;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.sync.SyncManager;

public class ConfigActivity extends AppCompatActivity {

    private static final String TAG = "CONFIG_APP";
    private static final String PROD_URL = "https://brasiliarfid.com.br/assejus/";
    private static final String HOMO_URL = "https://brasiliarfid.com.br/teste/";

    private Switch switchAmbiente;
    private TextView txtUrl, txtAmbiente;
    private Button btnLogout, btnSync;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        switchAmbiente = findViewById(R.id.switchAmbiente);
        txtUrl = findViewById(R.id.txtUrl);
        txtAmbiente = findViewById(R.id.txtAmbiente);
        ImageView iconSettings = findViewById(R.id.iconSettings);
        btnLogout = findViewById(R.id.btnLogout);
        btnSync = findViewById(R.id.btnSync);

        iniciarAnimacao(iconSettings);
        carregarAmbiente();
        configurarSwitch();
        configurarLogout();

        // 🔥 Chama o sync passando a URL correta
        String baseUrl = getBaseUrl(this);
        syncAPI(baseUrl);
    }

    private void iniciarAnimacao(ImageView icon) {
        ObjectAnimator rotate = ObjectAnimator.ofFloat(icon, "rotation", 0f, 360f);
        rotate.setDuration(2000);
        rotate.setRepeatCount(ValueAnimator.INFINITE);
        rotate.start();
    }

    private void syncAPI(String baseUrl) {
        SyncManager.syncLocalizacoes(this, baseUrl, new SyncManager.SyncCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ConfigActivity.this, "Sincronização concluída!", Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "Sincronização concluída!");
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(ConfigActivity.this, "Erro na sincronização", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Erro na sincronização", e);
                });
            }
        });
    }

    private void carregarAmbiente() {
        String baseUrl = getBaseUrl(this);
        txtUrl.setText(baseUrl);

        if (baseUrl.equals(HOMO_URL)) {
            switchAmbiente.setChecked(true);
            txtAmbiente.setText("Ambiente: HOMOLOGAÇÃO");
            Log.i(TAG, "App iniciado em HOMOLOGAÇÃO -> " + baseUrl);
        } else {
            switchAmbiente.setChecked(false);
            txtAmbiente.setText("Ambiente: PRODUÇÃO");
            Log.i(TAG, "App iniciado em PRODUÇÃO -> " + baseUrl);
        }
    }

    private void configurarSwitch() {
        switchAmbiente.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (LoginActivity.isLogado(this)) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Atenção")
                        .setMessage("Você precisa sair da conta antes de trocar o ambiente.")
                        .setPositiveButton("OK", null)
                        .show();

                // Volta o switch para o estado anterior
                switchAmbiente.setOnCheckedChangeListener(null);
                switchAmbiente.setChecked(!isChecked);
                configurarSwitch(); // reativa o listener
                return;
            }

            // Atualiza o ambiente
            setEnvironment(this, isChecked);
            String baseUrl = getBaseUrl(this);
            txtUrl.setText(baseUrl);

            if (isChecked) {
                txtAmbiente.setText("Ambiente: HOMOLOGAÇÃO");
                txtAmbiente.setTextColor(Color.parseColor("#FF9800")); // laranja
            } else {
                txtAmbiente.setText("Ambiente: PRODUÇÃO");
                txtAmbiente.setTextColor(Color.parseColor("#D32F2F")); // vermelho
            }

            Log.i(TAG, "Ambiente alterado -> " + baseUrl);
        });
    }

    private void configurarLogout() {
        btnLogout.setOnClickListener(v -> {
            LoginActivity.limparSessao(this);
            getSharedPreferences("usuario", MODE_PRIVATE).edit().clear().apply();
            Toast.makeText(this, "Logout realizado", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finishAffinity();
        });
    }

    // =========================
    // MÉTODOS DE CONFIG DA API
    // =========================

    public static String getBaseUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        return prefs.getString("BASE_URL", PROD_URL);
    }

    public static void setEnvironment(Context context, boolean homologacao) {
        SharedPreferences prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (homologacao) {
            editor.putString("BASE_URL", HOMO_URL);
        } else {
            editor.putString("BASE_URL", PROD_URL);
        }
        editor.apply();
        Log.i(TAG, "Ambiente alterado sem limpar banco");
    }
}