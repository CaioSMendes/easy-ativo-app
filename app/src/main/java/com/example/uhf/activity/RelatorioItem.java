package com.example.uhf.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.uhf.R;
import com.example.uhf.activity.GeraRelatorioActivity;

import java.util.List;

public class RelatorioItem extends BaseAdapter {

    private final Context context;
    private final List<GeraRelatorioActivity.RelatorioItem> lista;

    public RelatorioItem(Context context, List<GeraRelatorioActivity.RelatorioItem> lista) {
        this.context = context;
        this.lista   = lista;
    }

    @Override public int getCount()              { return lista.size(); }
    @Override public Object getItem(int pos)     { return lista.get(pos); }
    @Override public long getItemId(int pos)     { return pos; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.item_relatorio, parent, false);
        }

        GeraRelatorioActivity.RelatorioItem item = lista.get(position);

        TextView tvDescricao  = convertView.findViewById(R.id.txtResumoDescricao);
        TextView tvQuantidade = convertView.findViewById(R.id.txtResumoQtd);

        tvDescricao.setText(item.descricao);
        tvQuantidade.setText(item.quantidade + "x");

        return convertView;
    }
}