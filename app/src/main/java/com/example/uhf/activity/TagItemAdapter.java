package com.example.uhf.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.uhf.R;
import com.example.uhf.activity.LeituraLocalActivity;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class TagItemAdapter extends ArrayAdapter<LeituraLocalActivity.TagItem> {

    private final Map<Integer, String> mapaLocais;

    public TagItemAdapter(Context context, List<LeituraLocalActivity.TagItem> tags) {
        super(context, 0, tags);
        this.mapaLocais = new HashMap<>(); // mapa vazio como fallback
    }

    public TagItemAdapter(Context context, List<LeituraLocalActivity.TagItem> tags, Map<Integer, String> mapaLocais) {
        super(context, 0, tags);
        this.mapaLocais = mapaLocais;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View itemView = convertView;

        if (itemView == null) {
            itemView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_tag_inventario, parent, false);
        }

        LeituraLocalActivity.TagItem tag = getItem(position);

        TextView tvEpc         = itemView.findViewById(R.id.tvEpcInventario);
        TextView tvDescricao   = itemView.findViewById(R.id.tvDescricaoInventario);
        TextView tvStatus      = itemView.findViewById(R.id.tvStatusInventario);
        TextView tvLocal       = itemView.findViewById(R.id.tvLocal); // ← era convertView, corrigido para itemView
        ImageView imgPatrimonio = itemView.findViewById(R.id.imgPatrimonioInventario);

        if (tag != null) {

            String epcCurto = tag.epc.length() > 8
                    ? tag.epc.substring(tag.epc.length() - 8)
                    : tag.epc;
            tvEpc.setText("EPC: " + epcCurto);

            tvDescricao.setText(tag.descricao != null && !tag.descricao.trim().isEmpty()
                    ? tag.descricao : "Sem descrição");

            // ✅ Nome do local via mapa, fallback para ID
            String nomeLocal = mapaLocais.containsKey(tag.localizacaoId)
                    ? mapaLocais.get(tag.localizacaoId)
                    : "Local " + tag.localizacaoId;
            tvLocal.setText(nomeLocal);

            switch (tag.status) {
                case "ENCONTRADO":    tvStatus.setText("Encontrado");     break;
                case "MOVIMENTADO":   tvStatus.setText("Movimentado");    break;
                case "NAO_ENCONTRADO": tvStatus.setText("Não encontrado"); break;
                case "NO LOCAL":      tvStatus.setText("No Local");       break;
                default:              tvStatus.setText(tag.status);       break;
            }

            tvStatus.setBackgroundResource(R.drawable.status_padrao);
            switch (tag.status) {
                case "ENCONTRADO":    tvStatus.setBackgroundResource(R.drawable.status_encontrado);     break;
                case "MOVIMENTADO":   tvStatus.setBackgroundResource(R.drawable.status_movimentado);    break;
                case "NAO_ENCONTRADO": tvStatus.setBackgroundResource(R.drawable.status_nao_encontrado); break;
                case "NO LOCAL":      tvStatus.setBackgroundResource(R.drawable.status_no_local);       break;
            }

            switch (tag.status) {
                case "ENCONTRADO":    imgPatrimonio.setImageResource(R.drawable.ic_ok);      break;
                case "MOVIMENTADO":   imgPatrimonio.setImageResource(R.drawable.ic_alerta);  break;
                case "NAO_ENCONTRADO": imgPatrimonio.setImageResource(R.drawable.ic_erro);   break;
                default:              imgPatrimonio.setImageResource(R.drawable.ic_etiqueta); break;
            }
        }

        return itemView;
    }
}