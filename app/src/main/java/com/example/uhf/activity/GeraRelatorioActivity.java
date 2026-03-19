package com.example.uhf.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.database.DatabaseHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class GeraRelatorioActivity extends AppCompatActivity {

    private static final String TAG = "GeraRelatorio";

    private DatabaseHelper dbHelper;
    private com.example.uhf.adapter.RelatorioAdapter adapter;
    private List<RelatorioItem> listaRelatorio = new ArrayList<>();

    private TextView tvNomeLocal, tvTotalItens, tvTotalDescricoes;
    private ListView listViewRelatorio;

    private int localId;
    private String nomeLocal;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ===========================
    // MODELO
    // ===========================
    public static class RelatorioItem {
        public String descricao;
        public int quantidade;

        public RelatorioItem(String descricao, int quantidade) {
            this.descricao = descricao;
            this.quantidade = quantidade;
        }
    }

    // ===========================
    // onCreate
    // ===========================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_relatorio);

        dbHelper  = new DatabaseHelper(this, "uhf_db");
        localId   = getIntent().getIntExtra("localId", -1);
        nomeLocal = getIntent().getStringExtra("nomeLocal");
        if (nomeLocal == null) nomeLocal = "Local " + localId;

        tvNomeLocal       = findViewById(R.id.tvNomeLocal);
        tvTotalItens      = findViewById(R.id.tvTotalItens);
        tvTotalDescricoes = findViewById(R.id.tvTotalDescricoes);
        listViewRelatorio = findViewById(R.id.listViewRelatorio);

        tvNomeLocal.setText(nomeLocal);

        adapter = new com.example.uhf.adapter.RelatorioAdapter(this, listaRelatorio);
        listViewRelatorio.setAdapter(adapter);

        findViewById(R.id.btnEnviarEmail).setOnClickListener(v -> pedirEmailParaEnvio(false));
        findViewById(R.id.btnExportarPDF).setOnClickListener(v -> gerarPDFEPerguntar());

        carregarRelatorio();
    }

    // ===========================
    // CARREGAR DO BANCO
    // ===========================
    private void carregarRelatorio() {
        executor.execute(() -> {
            try {
                String tagsJson = getIntent().getStringExtra("TAGS_LIDAS");

                if (tagsJson == null || tagsJson.isEmpty()) {
                    mainHandler.post(() ->
                            Toast.makeText(this, "Nenhuma tag recebida!", Toast.LENGTH_SHORT).show());
                    return;
                }

                JSONArray tagsArray = new JSONArray(tagsJson);

                // Agrupa por descrição
                Map<String, Integer> mapa = new LinkedHashMap<>();
                for (int i = 0; i < tagsArray.length(); i++) {
                    JSONObject tag  = tagsArray.getJSONObject(i);
                    String desc = tag.optString("descricao", "Sem descrição").trim();
                    if (desc.isEmpty()) desc = "Sem descrição";
                    mapa.put(desc, mapa.getOrDefault(desc, 0) + 1);
                }

                List<RelatorioItem> lista = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : mapa.entrySet()) {
                    lista.add(new RelatorioItem(entry.getKey(), entry.getValue()));
                }
                lista.sort((a, b) -> b.quantidade - a.quantidade);

                int totalItens      = tagsArray.length();
                int totalDescricoes = lista.size();

                mainHandler.post(() -> {
                    listaRelatorio.clear();
                    listaRelatorio.addAll(lista);
                    adapter.notifyDataSetChanged();
                    tvTotalItens.setText("Itens lidos: " + totalItens);
                    tvTotalDescricoes.setText("Descrições distintas: " + totalDescricoes);
                });

            } catch (Exception e) {
                Log.e(TAG, "Erro carregar relatório", e);
            }
        });
    }

    // ===========================
    // PDF PROFISSIONAL
    // ===========================
    private void gerarPDFEPerguntar() {
        if (listaRelatorio.isEmpty()) {
            Toast.makeText(this, "Nenhum item para exportar!", Toast.LENGTH_SHORT).show();
            return;
        }
        executor.execute(() -> {
            File arquivo = gerarPDF();
            if (arquivo != null) {
                mainHandler.post(() -> pedirEmailParaEnvio(true, arquivo));
            }
        });
    }

    private File gerarPDF() {
        try {
            PdfDocument pdf = new PdfDocument();
            String dataGeracao = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date());
            String anoAtual    = new SimpleDateFormat("yyyy").format(new Date());

            // ── Tintas reutilizáveis ──
            Paint pBranco = new Paint(); pBranco.setColor(Color.WHITE);
            Paint pPreto  = new Paint(); pPreto.setColor(Color.BLACK);
            Paint pCinza  = new Paint(); pCinza.setColor(Color.rgb(220, 220, 220));
            Paint pZebra  = new Paint(); pZebra.setColor(Color.rgb(245, 245, 245));
            Paint pLinha  = new Paint(); pLinha.setColor(Color.BLACK); pLinha.setStrokeWidth(1.5f);
            Paint pRodape = new Paint(); pRodape.setColor(Color.GRAY);  pRodape.setTextSize(12f);

            Paint pTituloCapa = new Paint();
            pTituloCapa.setColor(Color.WHITE); pTituloCapa.setTextSize(26f); pTituloCapa.setFakeBoldText(true);

            Paint pInfoCapa = new Paint();
            pInfoCapa.setColor(Color.WHITE); pInfoCapa.setTextSize(16f);

            Paint pTituloTabela = new Paint();
            pTituloTabela.setColor(Color.BLACK); pTituloTabela.setTextSize(20f); pTituloTabela.setFakeBoldText(true);

            Paint pTexto = new Paint();
            pTexto.setColor(Color.BLACK); pTexto.setTextSize(14f);

            Paint pBold = new Paint();
            pBold.setColor(Color.BLACK); pBold.setTextSize(14f); pBold.setFakeBoldText(true);

            Paint pFundoAzul = new Paint();
            pFundoAzul.setColor(Color.rgb(37, 37, 37));

            // ─────────────────────────────
            // PÁGINA 1 — CAPA
            // ─────────────────────────────
            PdfDocument.PageInfo capaInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page capaPage = pdf.startPage(capaInfo);
            Canvas c = capaPage.getCanvas();

            c.drawRect(0, 0, 595, 842, pFundoAzul);

            try {
                Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.ic_logo_bsb_rfid);
                Bitmap logoG = Bitmap.createScaledBitmap(logo, 260, 84, true);
                c.drawBitmap(logoG, (595 - logoG.getWidth()) / 2f, 110, null);
            } catch (Exception ignored) {}

            c.drawText("Relatório de Inventário Patrimonial", 45, 260, pTituloCapa);

            int y = 340;
            c.drawText("Local: " + nomeLocal, 60, y, pInfoCapa); y += 30;
            c.drawText("Gerado em: " + dataGeracao, 60, y, pInfoCapa); y += 30;

            // totais na capa
            int totalItens = 0;
            for (RelatorioItem item : listaRelatorio) totalItens += item.quantidade;
            c.drawText("Total de itens: " + totalItens, 60, y, pInfoCapa); y += 30;
            c.drawText("Descrições distintas: " + listaRelatorio.size(), 60, y, pInfoCapa);

            pRodape.setColor(Color.WHITE);
            c.drawText("Brasília RFID © " + anoAtual, 230, 815, pRodape);
            pdf.finishPage(capaPage);

            // ─────────────────────────────
            // PÁGINA 2+ — TABELA
            // ─────────────────────────────
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 2).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            c = page.getCanvas();
            int paginaAtual = 2;
            y = 70;

            c.drawText("Resumo do Inventário — " + nomeLocal, 40, y, pTituloTabela);
            y += 10;
            c.drawLine(40, y, 555, y, pLinha);
            y += 30;

            // cabeçalho da tabela
            c.drawRect(40, y - 18, 555, y + 8, pCinza);
            c.drawText("Qtd", 50, y, pBold);
            c.drawText("Descrição", 130, y, pBold);
            y += 12;
            c.drawLine(40, y, 555, y, pLinha);
            y += 22;

            boolean zebra = false;
            int totalGeral = 0;

            for (RelatorioItem item : listaRelatorio) {
                // nova página se necessário
                if (y > 760) {
                    pRodape.setColor(Color.GRAY);
                    c.drawText("Página " + paginaAtual, 500, 825, pRodape);
                    pdf.finishPage(page);
                    paginaAtual++;
                    page = pdf.startPage(new PdfDocument.PageInfo.Builder(595, 842, paginaAtual).create());
                    c = page.getCanvas();
                    y = 60;
                    zebra = false;
                }

                if (zebra) c.drawRect(40, y - 16, 555, y + 8, pZebra);
                zebra = !zebra;

                totalGeral += item.quantidade;

                String desc = item.descricao.length() > 55
                        ? item.descricao.substring(0, 55) + "..."
                        : item.descricao;

                c.drawText(String.valueOf(item.quantidade), 50, y, pTexto);
                c.drawText(desc, 130, y, pTexto);
                y += 26;
            }

            // linha total
            y += 10;
            c.drawLine(40, y, 555, y, pLinha);
            y += 25;
            c.drawText("TOTAL GERAL DE ITENS: " + totalGeral, 40, y, pTituloTabela);

            pRodape.setColor(Color.GRAY);
            c.drawText("Página " + paginaAtual, 500, 825, pRodape);
            pdf.finishPage(page);

            // ── Salvar ──
            File pasta = new File(getExternalFilesDir(null), "relatorios");
            if (!pasta.exists()) pasta.mkdirs();
            String data = new SimpleDateFormat("dd-MM-yy_HH-mm").format(new Date());
            File arquivo = new File(pasta, "relatorio_" + localId + "_" + data + ".pdf");
            FileOutputStream fos = new FileOutputStream(arquivo);
            pdf.writeTo(fos);
            fos.close();
            pdf.close();

            mainHandler.post(() ->
                    Toast.makeText(this, "PDF gerado:\n" + arquivo.getAbsolutePath(), Toast.LENGTH_LONG).show());

            return arquivo;

        } catch (Exception e) {
            Log.e(TAG, "Erro gerar PDF", e);
            mainHandler.post(() ->
                    Toast.makeText(this, "Erro ao gerar PDF: " + e.getMessage(), Toast.LENGTH_LONG).show());
            return null;
        }
    }

    // ===========================
    // EMAIL — CSV
    // ===========================
    private void pedirEmailParaEnvio(boolean isPDF) {
        pedirEmailParaEnvio(isPDF, null);
    }

    private void pedirEmailParaEnvio(boolean isPDF, File arquivoPDF) {
        if (listaRelatorio.isEmpty()) {
            Toast.makeText(this, "Nenhum item para enviar!", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText inputEmail = new EditText(this);
        inputEmail.setHint("Digite o e-mail do destinatário");
        inputEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        new android.app.AlertDialog.Builder(this)
                .setTitle(isPDF ? "Enviar PDF" : "Enviar Relatório CSV")
                .setMessage("Informe o e-mail de destino:")
                .setView(inputEmail)
                .setPositiveButton("Enviar", (dialog, which) -> {
                    String email = inputEmail.getText().toString().trim();
                    if (!email.isEmpty()) {
                        if (isPDF && arquivoPDF != null) enviarArquivo(arquivoPDF, email);
                        else gerarCSVEEnviar(email);
                    } else {
                        Toast.makeText(this, "E-mail não informado!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void gerarCSVEEnviar(String destinatario) {
        executor.execute(() -> {
            try {
                File pasta = new File(getExternalFilesDir(null), "relatorios");
                if (!pasta.exists()) pasta.mkdirs();
                String data = new SimpleDateFormat("dd-MM-yy_HH-mm").format(new Date());
                File arquivo = new File(pasta, "relatorio_" + localId + "_" + data + ".csv");
                FileOutputStream fos = new FileOutputStream(arquivo);

                fos.write(("Local: " + nomeLocal + "\n").getBytes());
                fos.write(("Data: " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()) + "\n\n").getBytes());
                fos.write("Descrição;Quantidade\n".getBytes());
                for (RelatorioItem item : listaRelatorio) {
                    fos.write((item.descricao + ";" + item.quantidade + "\n").getBytes());
                }
                fos.close();

                enviarArquivo(arquivo, destinatario);

            } catch (Exception e) {
                Log.e(TAG, "Erro gerar CSV", e);
                mainHandler.post(() ->
                        Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void enviarArquivo(File arquivo, String destinatario) {
        executor.execute(() -> {
            String usuario = "smartmailbuilding@gmail.com";
            String senha   = "ebzzwrvykwihempj";
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.port", "465");

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(usuario, senha);
                    }
                });

                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(usuario));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
                message.setSubject("Relatório de Inventário — " + nomeLocal);

                MimeBodyPart texto = new MimeBodyPart();
                texto.setText("Segue em anexo o relatório de inventário do local: " + nomeLocal);

                MimeBodyPart anexo = new MimeBodyPart();
                anexo.setDataHandler(new DataHandler(new FileDataSource(arquivo)));
                anexo.setFileName(arquivo.getName());

                MimeMultipart multipart = new MimeMultipart();
                multipart.addBodyPart(texto);
                multipart.addBodyPart(anexo);
                message.setContent(multipart);
                Transport.send(message);

                mainHandler.post(() ->
                        Toast.makeText(this, "E-mail enviado com sucesso!", Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                Log.e(TAG, "Erro enviar e-mail", e);
                mainHandler.post(() ->
                        Toast.makeText(this, "Erro ao enviar: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}