package com.example.uhf.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LOGIN_APP";

    EditText edtEmail, edtSenha;
    Button btnLogin;
    ImageView btnConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🔥 AUTO LOGIN
        if (isLogado(this)) {
            Log.i(TAG, "Usuário já logado");
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        edtEmail = findViewById(R.id.edtEmail);
        edtSenha = findViewById(R.id.edtSenha);
        btnLogin = findViewById(R.id.btnLogin);
        btnConfig = findViewById(R.id.btnConfig);

        btnLogin.setOnClickListener(v -> {

            String email = edtEmail.getText().toString().trim();
            String senha = edtSenha.getText().toString().trim();

            if (email.isEmpty() || senha.isEmpty()) {
                Toast.makeText(this, "Informe email e senha", Toast.LENGTH_LONG).show();
                return;
            }

            fazerLogin(email, senha);
        });

        btnConfig.setOnClickListener(v ->
                startActivity(new Intent(this, ConfigActivity.class))
        );
    }

    private void fazerLogin(String login, String senha) {

        new Thread(() -> {

            try {

                SharedPreferences prefs = getSharedPreferences("config", MODE_PRIVATE);
                String baseUrl = prefs.getString("BASE_URL", "https://brasiliarfid.com.br/assejus/");

                String senhaHash = gerarHash(senha);

                String endpoint = baseUrl + "mobile/logar?login=" + login + "&senhaCriptografada=" + senhaHash;

                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int statusCode = conn.getResponseCode();

                if (statusCode == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream())
                    );

                    StringBuilder response = new StringBuilder();
                    String linha;
                    while ((linha = reader.readLine()) != null) {
                        response.append(linha);
                    }

                    reader.close();
                    JSONObject user = new JSONObject(response.toString());

                    int id = user.getInt("id");
                    String nome = user.getString("nome");
                    String perfil = user.getString("perfil");
                    String loginResp = user.getString("login");
                    String emailResp = user.getString("email");
                    // 🔥 SALAR USUÁRIO
                    SharedPreferences prefsUser = getSharedPreferences("usuario", MODE_PRIVATE);
                    prefsUser.edit()
                            .putInt("id", id)
                            .putString("nome", nome)
                            .putString("perfil", perfil)
                            .putString("login", loginResp)
                            .putString("email", emailResp)
                            .apply();
                    // 🔥 SALVAR SESSÃO
                    salvarSessao(LoginActivity.this, id);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Bem-vindo " + nome, Toast.LENGTH_LONG).show();
                        startActivity(new Intent(this, DashboardActivity.class));
                        finish();
                    });
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Erro no login", Toast.LENGTH_LONG).show()
                    );
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Erro login -> ", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "Erro ao conectar na API", Toast.LENGTH_LONG).show()
                );
            }

        }).start();
    }

    // ================= SESSION =================

    public static void salvarSessao(Context context, int userId) {
        SharedPreferences prefs = context.getSharedPreferences("sessao", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("logado", true)
                .putInt("user_id", userId)
                .apply();
    }

    public static boolean isLogado(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("sessao", MODE_PRIVATE);
        return prefs.getBoolean("logado", false);
    }

    public static void limparSessao(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("sessao", MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    // ================= HASH =================

    private String gerarHash(String senha) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] bytes = md.digest(senha.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}