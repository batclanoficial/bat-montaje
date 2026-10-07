package com.batclan.montaje;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

/** Solo metadatos: los MP4 permanecen en la ubicación elegida por el usuario. */
final class LocalHistoryStore {
    private final SharedPreferences preferences;

    LocalHistoryStore(Context context) {
        preferences = context.getSharedPreferences("bat_local_history_v1", Context.MODE_PRIVATE);
    }

    synchronized JSONArray entries() {
        try { return new JSONArray(preferences.getString("entries", "[]")); }
        catch (Exception exception) { return new JSONArray(); }
    }

    synchronized void add(Uri uri, String name, long fileSize) throws Exception {
        JSONArray old = entries();
        JSONArray next = new JSONArray();
        next.put(new JSONObject().put("id", UUID.randomUUID().toString())
                .put("uri", uri.toString()).put("name", name)
                .put("created_at", System.currentTimeMillis())
                .put("file_size", fileSize));
        for (int i = 0; i < old.length(); i++) next.put(old.getJSONObject(i));
        if (!preferences.edit().putString("entries", next.toString()).commit()) {
            throw new IllegalStateException("No se pudo guardar el historial.");
        }
    }

    synchronized void rename(String id, String name) throws Exception {
        String clean = name.trim();
        if (clean.isEmpty() || clean.length() > 100 || clean.matches("(?s).*\\p{Cntrl}.*")) {
            throw new IllegalArgumentException("Nombre inválido.");
        }
        JSONArray items = entries();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (id.equals(item.optString("id"))) { item.put("name", clean); break; }
        }
        if (!preferences.edit().putString("entries", items.toString()).commit()) {
            throw new IllegalStateException("No se pudo cambiar el nombre.");
        }
    }

    synchronized void remove(String id) throws Exception {
        JSONArray items = entries();
        JSONArray next = new JSONArray();
        boolean found = false;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (id.equals(item.optString("id"))) found = true;
            else next.put(item);
        }
        if (!found) throw new IllegalArgumentException("Montaje no encontrado.");
        if (!preferences.edit().putString("entries", next.toString()).commit()) {
            throw new IllegalStateException("No se pudo actualizar el historial.");
        }
    }
}
