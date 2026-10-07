package com.batclan.montaje;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** PUT por bloques a la sesión temporal de Drive, sin OAuth de Google en el cliente. */
final class ResumableUploader {
    interface Listener {
        void onProgress(long sent, long total);
        void onSession(String uploadId, String sessionUri) throws Exception;
    }

    private static final int CHUNK = 8 * 1024 * 1024; // Múltiplo de 256 KiB.
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    void cancel() { cancelled.set(true); }

    JSONObject upload(File file, JSONObject reservation, BackendClient backend,
                      JSONObject credentials, Listener listener) throws Exception {
        if (file == null || !file.isFile() || file.length() < 1) throw new IOException("MP4 no disponible.");
        final long total = file.length();
        String uploadId = reservation.getString("upload_id");
        String sessionUri = reservation.getString("session_uri");
        validateSessionUri(sessionUri);
        listener.onSession(uploadId, sessionUri);
        int renewals = 0;
        int transientFailures = 0;
        long offset = 0;
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            while (true) {
                if (cancelled.get()) throw new InterruptedException("Subida cancelada.");
                Reply status;
                try {
                    status = queryStatus(sessionUri, total);
                } catch (IOException exception) {
                    if (++transientFailures > 6) throw exception;
                    delay(transientFailures);
                    continue;
                }
                if (status.code == 200 || status.code == 201) {
                    return confirm(backend, credentials, uploadId, status.fileId());
                }
                if (status.code == 404) {
                    if (++renewals > 3) throw new IOException("La sesión de Drive caducó repetidamente.");
                    JSONObject replacement = backend.post(withCredentials(credentials, "renew_upload")
                            .put("upload_id", uploadId));
                    if (!replacement.optBoolean("ok")) throw new IOException(replacement.optString("error", "No se pudo reanudar."));
                    if ("NEW".equals(replacement.optString("status"))) return replacement;
                    sessionUri = replacement.getString("session_uri");
                    validateSessionUri(sessionUri);
                    listener.onSession(uploadId, sessionUri);
                    offset = 0;
                    transientFailures = 0;
                    continue;
                }
                if (status.code != 308) {
                    if (status.code >= 500 || status.code == 429) {
                        if (++transientFailures > 6) throw new IOException("Drive no respondió tras varios intentos.");
                        delay(transientFailures);
                        continue;
                    }
                    throw new IOException("Drive rechazó la subida (HTTP " + status.code + ").");
                }
                offset = status.nextOffset();
                if (offset < 0 || offset > total) throw new IOException("Posición de subida inválida.");
                transientFailures = 0;
                listener.onProgress(offset, total);
                if (offset == total) {
                    delay(1);
                    continue;
                }
                int length = (int) Math.min(CHUNK, total - offset);
                byte[] chunk = new byte[length];
                input.seek(offset);
                input.readFully(chunk);
                Reply answer;
                try {
                    answer = sendChunk(sessionUri, chunk, offset, total);
                } catch (IOException exception) {
                    if (++transientFailures > 6) throw exception;
                    delay(transientFailures);
                    // Consulta el estado real; no reenvíes un bloque a ciegas.
                    continue;
                }
                if (answer.code == 200 || answer.code == 201) {
                    listener.onProgress(total, total);
                    return confirm(backend, credentials, uploadId, answer.fileId());
                }
                if (answer.code == 308) {
                    long accepted = answer.nextOffset();
                    if (accepted > total) throw new IOException("Drive devolvió una posición inválida.");
                    if (accepted <= offset) {
                        if (++transientFailures > 6) throw new IOException("Drive no confirmó el bloque enviado.");
                        delay(transientFailures);
                        continue;
                    }
                    offset = accepted;
                    listener.onProgress(offset, total);
                } else if (answer.code == 404 || answer.code == 429 || answer.code >= 500) {
                    if (++transientFailures > 6) throw new IOException("Drive no respondió tras varios intentos.");
                    delay(transientFailures);
                } else {
                    throw new IOException("Drive rechazó el bloque (HTTP " + answer.code + ").");
                }
            }
        }
    }

    private JSONObject confirm(BackendClient backend, JSONObject credentials,
                               String uploadId, String fileId) throws Exception {
        if (fileId == null || fileId.isEmpty()) {
            JSONObject found = backend.post(withCredentials(credentials, "renew_upload")
                    .put("upload_id", uploadId));
            if (found.optBoolean("ok") && "NEW".equals(found.optString("status"))) return found;
            throw new IOException("Drive completó la subida, pero no devolvió el ID del archivo.");
        }
        JSONObject result = backend.post(withCredentials(credentials, "finish_upload")
                .put("upload_id", uploadId).put("drive_file_id", fileId));
        if (!result.optBoolean("ok") || !"NEW".equals(result.optString("status"))) {
            throw new IOException(result.optString("error", "El backend no confirmó el archivo."));
        }
        return result;
    }

    static JSONObject withCredentials(JSONObject credentials, String action) throws Exception {
        return new JSONObject().put("action", action)
                .put("installation_id", credentials.getString("installation_id"))
                .put("device_binding_id", credentials.getString("device_binding_id"))
                .put("access_token", credentials.getString("access_token"));
    }

    private Reply queryStatus(String sessionUri, long total) throws IOException {
        HttpURLConnection connection = openSession(sessionUri, "PUT");
        connection.setRequestProperty("Content-Range", "bytes */" + total);
        connection.setFixedLengthStreamingMode(0);
        connection.setDoOutput(true);
        try (OutputStream ignored = connection.getOutputStream()) { }
        return readReply(connection);
    }

    private Reply sendChunk(String sessionUri, byte[] chunk, long offset, long total) throws IOException {
        HttpURLConnection connection = openSession(sessionUri, "PUT");
        connection.setRequestProperty("Content-Type", "video/mp4");
        connection.setRequestProperty("Content-Range", "bytes " + offset + "-" +
                (offset + chunk.length - 1) + "/" + total);
        connection.setFixedLengthStreamingMode(chunk.length);
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) { output.write(chunk); }
        return readReply(connection);
    }

    private HttpURLConnection openSession(String uri, String method) throws IOException {
        validateSessionUri(uri);
        HttpURLConnection connection = (HttpURLConnection) new URL(uri).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        return connection;
    }

    private static void validateSessionUri(String uri) throws IOException {
        try {
            URL url = new URL(uri);
            if (!"https".equals(url.getProtocol()) || !"www.googleapis.com".equals(url.getHost()) ||
                    !"/upload/drive/v3/files".equals(url.getPath()) ||
                    url.getQuery() == null || !url.getQuery().contains("upload_id=")) {
                throw new IOException("Sesión de Drive inválida.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("Sesión de Drive inválida.", exception);
        }
    }

    private Reply readReply(HttpURLConnection connection) throws IOException {
        try {
            int code = connection.getResponseCode();
            String range = connection.getHeaderField("Range");
            String content = "";
            if (code == 200 || code == 201) {
                try (InputStream input = connection.getInputStream()) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = input.read(buffer)) >= 0) {
                        if (output.size() + n > 65536) throw new IOException("Respuesta de Drive demasiado grande.");
                        output.write(buffer, 0, n);
                    }
                    content = output.toString(StandardCharsets.UTF_8.name());
                }
            }
            return new Reply(code, range, content);
        } finally { connection.disconnect(); }
    }

    private void delay(int attempts) throws InterruptedException {
        long millis = Math.min(16000L, 1000L << Math.min(attempts, 4));
        long until = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < until) {
            if (cancelled.get()) throw new InterruptedException("Subida cancelada.");
            Thread.sleep(Math.min(250L, until - System.currentTimeMillis()));
        }
    }

    private static final class Reply {
        final int code;
        final String range;
        final String body;
        Reply(int code, String range, String body) { this.code = code; this.range = range; this.body = body; }
        long nextOffset() throws IOException {
            if (range == null || range.isEmpty()) return 0;
            if (!range.matches("bytes=0-[0-9]+")) throw new IOException("Rango de Drive inválido.");
            return Long.parseLong(range.substring(range.indexOf('-') + 1)) + 1;
        }
        String fileId() throws IOException {
            try { return body.isEmpty() ? "" : new JSONObject(body).optString("id", ""); }
            catch (Exception exception) { throw new IOException("Respuesta de Drive inválida.", exception); }
        }
    }
}
