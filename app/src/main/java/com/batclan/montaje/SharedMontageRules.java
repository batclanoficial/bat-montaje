package com.batclan.montaje;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Reads the same event and timing rules used by the PWA from the packaged shared asset. */
final class SharedMontageRules {
    final String[] eventTypes;
    final int defaultBeforeSeconds;
    final int defaultAfterSeconds;
    final long mergeGapMilliseconds;

    private SharedMontageRules(String[] eventTypes, int before, int after, long mergeGap) {
        this.eventTypes = eventTypes;
        this.defaultBeforeSeconds = before;
        this.defaultAfterSeconds = after;
        this.mergeGapMilliseconds = mergeGap;
    }

    static SharedMontageRules load(Context context) {
        try (InputStream input = context.getAssets().open("montage-rules.json");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            JSONObject json = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            JSONArray names = json.getJSONArray("eventTypes");
            if (names.length() == 0) throw new IllegalArgumentException("No hay tipos de evento");
            String[] types = new String[names.length()];
            for (int i = 0; i < names.length(); i++) {
                types[i] = names.getString(i).trim();
                if (types[i].isEmpty()) throw new IllegalArgumentException("Tipo de evento vacío");
            }
            int before = json.getInt("defaultBeforeSeconds");
            int after = json.getInt("defaultAfterSeconds");
            long gap = json.getLong("mergeGapMilliseconds");
            if (before < 0 || after < 0 || gap < 0) throw new IllegalArgumentException("Tiempo negativo");
            return new SharedMontageRules(types, before, after, gap);
        } catch (Exception error) {
            throw new IllegalStateException("No se pudieron cargar las reglas del montaje", error);
        }
    }
}
