package com.example.uhf.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.uhf.R;

import java.util.List;

public class TagAdapter extends BaseAdapter {

    private Context context;
    private List<String> listaTags;

    public TagAdapter(Context context, List<String> listaTags) {
        this.context = context;
        this.listaTags = listaTags;
    }

    @Override
    public int getCount() {
        return listaTags.size();
    }

    @Override
    public Object getItem(int position) {
        return listaTags.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.item_tag, parent, false);
        }

        TextView txtTag = convertView.findViewById(R.id.txtTag);

        String tag = listaTags.get(position);

        txtTag.setText("Tag: " + tag);

        return convertView;
    }
}