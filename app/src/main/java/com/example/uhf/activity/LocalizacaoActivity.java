package com.example.uhf.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.database.DatabaseHelper;
import com.example.uhf.database.DatabaseManager;
import com.example.uhf.sync.SyncManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class LocalizacaoActivity extends AppCompatActivity {

    private static final String TAG = "LOCALIZACAO_APP";

    private Spinner spinnerUnidade, spinnerDepartamento, spinnerSetor;
    private Button btnSalvar, btnLeitura;
    private TextView txtModo;

    private JSONArray todasLocalizacoes;

    private JSONObject unidadeSelecionada, departamentoSelecionado, setorSelecionado;

    private SharedPreferences prefs;
    private String baseUrl;
    private DatabaseHelper db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_localizacao);

        // --- Inicialização ---
        db = DatabaseManager.getDatabase(this);

        spinnerUnidade = findViewById(R.id.spinnerUnidade);
        spinnerDepartamento = findViewById(R.id.spinnerDepartamento);
        spinnerSetor = findViewById(R.id.spinnerSetor);
        btnSalvar = findViewById(R.id.btnSalvar);
        btnLeitura = findViewById(R.id.btnLeitura);
        txtModo = findViewById(R.id.txtModo);

        // --- Desabilita campos até sincronização ---
        spinnerUnidade.setEnabled(false);
        spinnerDepartamento.setEnabled(false);
        spinnerSetor.setEnabled(false);
        btnSalvar.setEnabled(false);

        prefs = getSharedPreferences("config", MODE_PRIVATE);
        baseUrl = prefs.getString("BASE_URL","https://brasiliarfid.com.br/teste/");
        Log.d(TAG,"BASE URL: " + baseUrl);

        // --- Botões ---
        btnSalvar.setOnClickListener(v -> salvarLocalizacao());
        btnLeitura.setOnClickListener(v -> {
            if(unidadeSelecionada == null || departamentoSelecionado == null){
                Toast.makeText(this,"Selecione Unidade e Departamento",Toast.LENGTH_SHORT).show();
                return;
            }
            salvarLocalizacao();
        });

        String modo = getIntent().getStringExtra("MÓDULO");
        if(modo != null && modo.equals("INVENTARIO")){
            txtModo.setText("Módulo Inventário");
        }

        // --- Sincroniza e carrega dados ---
        sincronizarECarregar();
    }

    private void sincronizarECarregar() {
        SyncManager.syncLocalizacoes(this, baseUrl, new SyncManager.SyncCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    todasLocalizacoes = carregarTodasLocalizacoesDoBanco();
                    iniciarHierarquia();
                    Toast.makeText(LocalizacaoActivity.this, "Localizações sincronizadas!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Sincronização concluída com sucesso!");

                    // --- Habilita campos ---
                    spinnerUnidade.setEnabled(true);
                    spinnerDepartamento.setEnabled(true);
                    spinnerSetor.setEnabled(true);
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(LocalizacaoActivity.this, "Erro na sincronização", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Erro sync", e);

                    // Mesmo com erro, tenta carregar do banco
                    todasLocalizacoes = carregarTodasLocalizacoesDoBanco();
                    iniciarHierarquia();

                    spinnerUnidade.setEnabled(true);
                    spinnerDepartamento.setEnabled(true);
                    spinnerSetor.setEnabled(true);
                });
            }
        });
    }

    private JSONArray carregarTodasLocalizacoesDoBanco() {
        JSONArray array = new JSONArray();
        try {
            ArrayList<JSONObject> unidades = db.buscarLocalizacaoPorPai(-1);
            for(JSONObject u : unidades){
                array.put(u);
                ArrayList<JSONObject> departamentos = db.buscarLocalizacaoPorPai(u.optInt("id"));
                for(JSONObject d : departamentos){
                    array.put(d);
                    ArrayList<JSONObject> setores = db.buscarLocalizacaoPorPai(d.optInt("id"));
                    for(JSONObject s : setores){
                        array.put(s);
                    }
                }
            }
        }catch(Exception e){
            Log.e(TAG,"Erro carregando localizações do banco",e);
        }
        return array;
    }

    private void iniciarHierarquia() {
        ArrayList<JSONObject> raizes = buscarRaizes();

        configurarUnidadeSpinner(raizes);
    }

    private void configurarUnidadeSpinner(ArrayList<JSONObject> unidades){
        carregarSpinner(spinnerUnidade, unidades);

        int lastUnidadeId = prefs.getInt("LAST_UNIDADE_ID", -1);
        int lastDepartamentoId = prefs.getInt("LAST_DEPARTAMENTO_ID", -1);
        int lastSetorId = prefs.getInt("LAST_SETOR_ID", -1);

        // Pré-seleção unidade
        if(lastUnidadeId != -1){
            for(int i=0;i<unidades.size();i++){
                if(unidades.get(i).optInt("id") == lastUnidadeId){
                    spinnerUnidade.setSelection(i+1);
                    break;
                }
            }
        }

        spinnerUnidade.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if(position == 0){
                    unidadeSelecionada = null;
                    carregarSpinner(spinnerDepartamento, new ArrayList<>());
                    carregarSpinner(spinnerSetor, new ArrayList<>());
                    validarCampos();
                    return;
                }

                unidadeSelecionada = unidades.get(position - 1);
                prefs.edit().putInt("LAST_UNIDADE_ID", unidadeSelecionada.optInt("id")).apply();
                departamentoSelecionado = null;
                setorSelecionado = null;

                ArrayList<JSONObject> departamentos = buscarFilhos(unidadeSelecionada.optInt("id"));
                configurarDepartamentoSpinner(departamentos, lastDepartamentoId, lastSetorId);

                validarCampos();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void configurarDepartamentoSpinner(ArrayList<JSONObject> departamentos, int lastDepartamentoId, int lastSetorId){
        carregarSpinner(spinnerDepartamento, departamentos);

        // Pré-seleção departamento
        if(lastDepartamentoId != -1){
            for(int i=0;i<departamentos.size();i++){
                if(departamentos.get(i).optInt("id") == lastDepartamentoId){
                    spinnerDepartamento.setSelection(i+1);
                    break;
                }
            }
        }

        spinnerDepartamento.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if(position == 0){
                    departamentoSelecionado = null;
                    carregarSpinner(spinnerSetor, new ArrayList<>());
                    validarCampos();
                    return;
                }

                departamentoSelecionado = departamentos.get(position - 1);
                prefs.edit().putInt("LAST_DEPARTAMENTO_ID", departamentoSelecionado.optInt("id")).apply();
                setorSelecionado = null;

                ArrayList<JSONObject> setores = buscarFilhos(departamentoSelecionado.optInt("id"));
                configurarSetorSpinner(setores, lastSetorId);

                validarCampos();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void configurarSetorSpinner(ArrayList<JSONObject> setores, int lastSetorId){
        carregarSpinner(spinnerSetor, setores);

        // Pré-seleção setor
        if(lastSetorId != -1){
            for(int i=0;i<setores.size();i++){
                if(setores.get(i).optInt("id") == lastSetorId){
                    spinnerSetor.setSelection(i+1);
                    break;
                }
            }
        }

        spinnerSetor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if(position == 0){
                    setorSelecionado = null;
                    return;
                }
                setorSelecionado = setores.get(position - 1);
                prefs.edit().putInt("LAST_SETOR_ID", setorSelecionado.optInt("id")).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private ArrayList<JSONObject> buscarRaizes() {
        ArrayList<JSONObject> lista = new ArrayList<>();
        try {
            for (int i = 0; i < todasLocalizacoes.length(); i++) {
                JSONObject obj = todasLocalizacoes.getJSONObject(i);
                if (obj.optInt("idLocalizacaoPai", -1) == -1) { // raiz se idPai == -1
                    lista.add(obj);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro buscando raízes", e);
        }
        return lista;
    }

    private ArrayList<JSONObject> buscarFilhos(int idPai) {
        ArrayList<JSONObject> lista = new ArrayList<>();
        try {
            for (int i = 0; i < todasLocalizacoes.length(); i++) {
                JSONObject obj = todasLocalizacoes.getJSONObject(i);
                if (obj.optInt("idLocalizacaoPai", -1) == idPai) {
                    lista.add(obj);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro buscando filhos", e);
        }
        return lista;
    }

    private void carregarSpinner(Spinner spinner, ArrayList<JSONObject> dados){
        ArrayList<String> nomes = new ArrayList<>();
        nomes.add("Selecione");
        for(JSONObject obj : dados){
            nomes.add(obj.optString("nome"));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                nomes
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void salvarLocalizacao(){
        try{
            JSONObject salvar = new JSONObject();

            if(unidadeSelecionada != null) salvar.put("unidade", unidadeSelecionada);
            if(departamentoSelecionado != null) salvar.put("departamento", departamentoSelecionado);
            if(setorSelecionado != null) salvar.put("setor", setorSelecionado);

            String jsonSalvar = salvar.toString();
            Log.d(TAG,"JSON LOCALIZAÇÃO:\n" + jsonSalvar);

            Intent intent = new Intent(this, LeituraLocalActivity.class);
            intent.putExtra("LOCALIZACAO_JSON", jsonSalvar);
            startActivity(intent);

        }catch(Exception e){
            Log.e(TAG,"Erro salvar localização",e);
        }
    }

    private void validarCampos(){
        btnSalvar.setEnabled(unidadeSelecionada != null && departamentoSelecionado != null);
    }
}