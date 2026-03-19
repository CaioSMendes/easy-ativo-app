package com.example.uhf.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.uhf.R;
import com.example.uhf.activity.GeraRelatorioActivity.RelatorioItem;

import java.util.List;

public class RelatorioAdapter extends BaseAdapter {

    private final Context context;
    private final List<RelatorioItem> lista;

    public RelatorioAdapter(Context context, List<RelatorioItem> lista) {
        this.context = context;
        this.lista   = lista;
    }

    @Override public int getCount()          { return lista.size(); }
    @Override public Object getItem(int i)   { return lista.get(i); }
    @Override public long getItemId(int i)   { return i; }

    static class Holder {
        View faixa;
        TextView descricao, qtd;
    }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.item_relatorio, parent, false);
            h = new Holder();
            h.faixa    = convertView.findViewById(R.id.faixaCor);
            h.descricao = convertView.findViewById(R.id.txtResumoDescricao);
            h.qtd       = convertView.findViewById(R.id.txtResumoQtd);
            convertView.setTag(h);
        } else {
            h = (Holder) convertView.getTag();
        }

        RelatorioItem item = lista.get(pos);
        h.descricao.setText(item.descricao);
        h.qtd.setText(item.quantidade + "x");

        int[] cores = {
                0xff2196F3, 0xff4CAF50, 0xffFF9800, 0xff9C27B0, 0xff009688,
                0xffF44336, 0xff3F51B5, 0xffE91E63, 0xff00BCD4, 0xffFF5722,
                0xff8BC34A, 0xff673AB7, 0xffFFC107, 0xff607D8B, 0xff795548,
                0xff009E73, 0xffD32F2F, 0xff1976D2, 0xff388E3C, 0xffF57C00,
        };
        h.faixa.setBackgroundColor(cores[pos % cores.length]);

        return convertView;
    }
}