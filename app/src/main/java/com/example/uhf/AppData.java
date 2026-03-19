package com.example.uhf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppData {

    // Listas globais de tags
    public static List<String> listaTagsSucesso = Collections.synchronizedList(new ArrayList<>());
    public static List<String> listaTagsErro404 = Collections.synchronizedList(new ArrayList<>());

    // Construtor privado para evitar instâncias
    private AppData() {}
}
