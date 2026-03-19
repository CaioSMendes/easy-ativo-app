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

public class TagItemAdapter extends ArrayAdapter<LeituraLocalActivity.TagItem> {

    public TagItemAdapter(Context context, List<LeituraLocalActivity.TagItem> tags) {
        super(context, 0, tags);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View itemView = convertView;

        if (itemView == null) {
            itemView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_tag_inventario, parent, false);
        }

        LeituraLocalActivity.TagItem tag = getItem(position);

        TextView tvEpc = itemView.findViewById(R.id.tvEpcInventario);
        TextView tvDescricao = itemView.findViewById(R.id.tvDescricaoInventario);
        TextView tvStatus = itemView.findViewById(R.id.tvStatusInventario);
        ImageView imgPatrimonio = itemView.findViewById(R.id.imgPatrimonioInventario);

        if (tag != null) {

            // 🔹 EPC (melhor: últimos dígitos)
            String epcCurto = tag.epc.length() > 8
                    ? tag.epc.substring(tag.epc.length() - 8)
                    : tag.epc;

            tvEpc.setText("EPC: " + epcCurto);

            // 🔥 DESCRIÇÃO (nunca vazia)
            if(tag.descricao != null && !tag.descricao.trim().isEmpty()){
                tvDescricao.setText(tag.descricao);
            } else {
                tvDescricao.setText("Sem descrição");
            }

            // 🔹 STATUS TEXTO (mais amigável)
            String statusTexto = tag.status;

            switch (tag.status){

                case "ENCONTRADO":
                    statusTexto = "Encontrado";
                    break;

                case "MOVIMENTADO":
                    statusTexto = "Movimentado";
                    break;

                case "NAO_ENCONTRADO":
                    statusTexto = "Não encontrado";
                    break;

                case "NO LOCAL":
                    statusTexto = "No Local";
                    break;
            }

            tvStatus.setText(statusTexto);

            // 🔥 RESET BACKGROUND (evita bug de reciclagem)
            tvStatus.setBackgroundResource(R.drawable.status_padrao);

            // 🔥 CORES POR STATUS
            switch (tag.status) {

                case "ENCONTRADO":
                    tvStatus.setBackgroundResource(R.drawable.status_encontrado);
                    break;

                case "MOVIMENTADO":
                    tvStatus.setBackgroundResource(R.drawable.status_movimentado);
                    break;

                case "NAO_ENCONTRADO":
                    tvStatus.setBackgroundResource(R.drawable.status_nao_encontrado);
                    break;

                case "NO LOCAL":
                    tvStatus.setBackgroundResource(R.drawable.status_no_local);
                    break;
            }

            // 🔥 ÍCONE DIFERENTE POR STATUS (opcional top)
            switch (tag.status){

                case "ENCONTRADO":
                    imgPatrimonio.setImageResource(R.drawable.ic_ok);
                    break;

                case "MOVIMENTADO":
                    imgPatrimonio.setImageResource(R.drawable.ic_alerta);
                    break;

                case "NAO_ENCONTRADO":
                    imgPatrimonio.setImageResource(R.drawable.ic_erro);
                    break;

                default:
                    imgPatrimonio.setImageResource(R.drawable.ic_etiqueta);
                    break;
            }
        }

        return itemView;
    }
}