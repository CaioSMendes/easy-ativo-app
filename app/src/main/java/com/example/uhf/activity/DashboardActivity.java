package com.example.uhf.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

public class DashboardActivity extends AppCompatActivity {

    TextView txtOla;
    LinearLayout btnSettings; // botão que abre a LocalizacaoActivity
    LinearLayout btnInventario;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        txtOla = findViewById(R.id.txtOla);

        SharedPreferences prefs = getSharedPreferences("usuario", MODE_PRIVATE);

        String login = prefs.getString("login","");
        String nome = prefs.getString("nome","");

        txtOla.setText("Olá, " + login);

        // Botão Localização
        btnSettings = findViewById(R.id.btnSettings);
        btnInventario = findViewById(R.id.btnInventario);

        btnSettings.setOnClickListener(v -> {
            // Abrir a LocalizacaoActivity
            Intent intent = new Intent(DashboardActivity.this, ConfigActivity.class);
            startActivity(intent);
        });

        btnInventario.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, LocalizacaoActivity.class);
            intent.putExtra("MÓDULO", "INVENTARIO");
            startActivity(intent);
        });
    }
}