package com.batclan.montaje;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Cliente HTTPS para Apps Script. No conoce ni almacena OAuth de Google. */
final class BackendClient {
    private static final int MAX_RESPONSE = 65536;
    private final String endpoint;

    BackendClient(String endpoint) {
        if (!validEndpoint(endpoint)) throw new IllegalArgumentException("URL del backend inválida.");
        this.endpoint = endpoint;
    }

    static boolean validEndpoint(String value) {
        try {
            URL url = new URL(value);
            return "https".equals(url.getProtocol()) && "script.google.com".equals(url.getHost()) &&
                    url.getPath().matches("/macros/s/[A-Za-z0-9_-]+/exec") &&
                    (url.getQuery() == null || url.getQuery().isEmpty()) && url.getRef() == null;
        } catch (Exception exception) {
            return false;
        }
    }

    JSONObject post(JSONObject request) throws Exception {
        byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
        if (body.length > 4096) throw new IOException("Solicitud demasiado grande.");
        URL url = new URL(endpoint);
        String method = "POST";
        for (int redirect = 0; redirect < 4; redirect++) {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod(method);
            if ("POST".equals(method)) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            }
            int status = connection.getResponseCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IOException("Redirección sin destino.");
                URL next = new URL(url, location);
                String host = next.getHost();
                if (!"https".equals(next.getProtocol()) ||
                        !("script.google.com".equals(host) || "script.googleusercontent.com".equals(host))) {
                    throw new IOException("Redirección no autorizada.");
                }
                url = next;
                if (status == 301 || status == 302 || status == 303) method = "GET";
                continue;
            }
            try {
                if (status < 200 || status >= 300) throw new IOException("Servidor HTTP " + status + ".");
                try (InputStream input = connection.getInputStream()) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > MAX_RESPONSE) throw new IOException("Respuesta demasiado grande.");
                        output.write(buffer, 0, count);
                    }
                    return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Demasiadas redirecciones del servidor.");
    }
}
