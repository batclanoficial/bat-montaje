package com.batclan.montaje;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Datos de sesión cifrados con una clave no exportable de Android Keystore. */
final class SecureAccountStore {
    private static final String ALIAS = "bat_montaje_session_v1";
    private static final String PREFS = "bat_account_v1";
    private static final String KEY_STATE = "encrypted_state";
    private static final String KEY_ENDPOINT = "backend_endpoint"; // La URL pública no es secreta.
    private static final String KEY_SIGNED_OUT = "signed_out";
    private final SharedPreferences preferences;
    private final String defaultEndpoint;
    private final Context context;

    SecureAccountStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        defaultEndpoint = context.getString(R.string.backend_url);
    }

    String endpoint() { return preferences.getString(KEY_ENDPOINT, defaultEndpoint); }

    void setEndpoint(String endpoint) {
        if (!BackendClient.validEndpoint(endpoint)) throw new IllegalArgumentException("URL inválida.");
        preferences.edit().putString(KEY_ENDPOINT, endpoint).apply();
    }

    JSONObject load() throws Exception {
        String encoded = preferences.getString(KEY_STATE, null);
        if (encoded == null) return new JSONObject();
        byte[] blob = Base64.decode(encoded, Base64.NO_WRAP);
        if (blob.length < 29) throw new IllegalStateException("Sesión dañada.");
        byte[] nonce = new byte[12];
        byte[] payload = new byte[blob.length - nonce.length];
        System.arraycopy(blob, 0, nonce, 0, nonce.length);
        System.arraycopy(blob, nonce.length, payload, 0, payload.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
        JSONObject state = new JSONObject(new String(cipher.doFinal(payload), java.nio.charset.StandardCharsets.UTF_8));
        state.put("device_binding_id", DeviceBinding.id(context));
        return state;
    }

    void save(JSONObject state) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        // Android Keystore exige generar el IV para cada cifrado; rechaza uno
        // suministrado por la aplicación cuando la aleatorización es obligatoria.
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] nonce = cipher.getIV();
        if (nonce == null || nonce.length != 12) {
            throw new IllegalStateException("IV de sesión no válido.");
        }
        byte[] payload = cipher.doFinal(state.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] blob = new byte[nonce.length + payload.length];
        System.arraycopy(nonce, 0, blob, 0, nonce.length);
        System.arraycopy(payload, 0, blob, nonce.length, payload.length);
        if (!preferences.edit().putString(KEY_STATE, Base64.encodeToString(blob, Base64.NO_WRAP))
                .putBoolean(KEY_SIGNED_OUT, false).commit()) {
            throw new IllegalStateException("No se pudo guardar la sesión.");
        }
    }

    void clear() { preferences.edit().remove(KEY_STATE).putBoolean(KEY_SIGNED_OUT, true).commit(); }

    boolean hasSignedOut() { return preferences.getBoolean(KEY_SIGNED_OUT, false); }

    static String randomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[64];
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = digits[(bytes[i] >> 4) & 15];
            result[i * 2 + 1] = digits[bytes[i] & 15];
        }
        return new String(result);
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
