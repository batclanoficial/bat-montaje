package com.batclan.montaje;

import android.content.Context;
import android.provider.Settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Identificador estable de esta aplicación en este dispositivo; no es un secreto. */
final class DeviceBinding {
    static String id(Context context) throws Exception {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.isEmpty() || "9774d56d682e549c".equals(androidId)) {
            throw new IllegalStateException("Dispositivo no identificable.");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                (context.getPackageName() + ":" + androidId).getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(64);
        for (byte part : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", part & 255));
        return result.toString();
    }
}
