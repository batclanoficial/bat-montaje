package com.batclan.montaje;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.util.UUID;

/** Envío a BAT independiente del render local; conserva la sesión para reanudar. */
public final class UploadActivity extends Activity {
    private static final int BG = Color.rgb(8, 9, 11);
    private static final int WHITE = Color.rgb(247, 248, 249);
    private static final int MUTED = Color.rgb(160, 165, 172);
    private static final int RED = Color.rgb(245, 27, 52);
    private SecureAccountStore store;
    private BackendClient backend;
    private JSONObject pending;
    private File file;
    private ResumableUploader uploader;
    private TextView status;
    private ProgressBar bar;
    private Button retry;
    private Button cancel;
    private boolean running;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        store = new SecureAccountStore(this);
        buildUi();
        try {
            backend = new BackendClient(store.endpoint());
            JSONObject account = store.load();
            if (!"ACTIVE".equals(account.optString("mode"))) throw new IllegalStateException("Tu cuenta no está activa.");
            pending = account.optJSONObject("upload_pending");
            if (pending == null) {
                String path = getIntent().getStringExtra("file_path");
                if (path == null) throw new IllegalStateException("No se encontró un montaje pendiente.");
                File source = new File(path).getCanonicalFile();
                File folder = new File(getCacheDir(), "montages").getCanonicalFile();
                if (!source.getPath().startsWith(folder.getPath() + File.separator) || !source.isFile()) {
                    throw new IllegalStateException("El MP4 temporal no está disponible.");
                }
                pending = new JSONObject().put("client_request_id", UUID.randomUUID().toString())
                        .put("file_path", source.getAbsolutePath()).put("file_size", source.length());
                account.put("upload_pending", pending);
                store.save(account);
            }
            file = new File(pending.getString("file_path"));
            if (!file.isFile() || file.length() != pending.getLong("file_size")) {
                throw new IllegalStateException("El archivo temporal cambió o ya no existe. Cancela la reserva pendiente.");
            }
            startUpload();
        } catch (Exception exception) {
            Log.e("BAT Montaje", "No se pudo preparar el envío", exception);
            status.setText("No se pudo preparar el envío. Regresa al editor e inténtalo nuevamente.");
            retry.setEnabled(false);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setBackgroundColor(BG);
        setContentView(root);
        TextView title = text("SUBIR A BAT", 26, WHITE);
        root.addView(title);
        root.addView(text("Comparte tu montaje con BAT.", 14, MUTED));
        status = text("Preparando subida…", 14, WHITE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(25);
        root.addView(status, statusParams);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(RED));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
        barParams.topMargin = dp(18);
        root.addView(bar, barParams);
        retry = button("REINTENTAR / REANUDAR", true);
        root.addView(retry);
        retry.setEnabled(false);
        retry.setOnClickListener(v -> startUpload());
        cancel = button("CANCELAR ENVÍO", false);
        root.addView(cancel);
        cancel.setOnClickListener(v -> cancelUpload());
        Button back = button("VOLVER AL EDITOR", false);
        root.addView(back);
        back.setOnClickListener(v -> onBackPressed());
        root.addView(text("Si se interrumpe la conexión, vuelve aquí y pulsa REANUDAR. " +
                "La confirmación aparecerá cuando el video se haya enviado por completo.", 12, MUTED));
    }

    private void startUpload() {
        if (running) return;
        if (file == null || !file.isFile()) { status.setText("El video ya no está disponible para reanudar."); return; }
        running = true;
        retry.setEnabled(false);
        status.setText("Subiendo video a BAT…");
        uploader = new ResumableUploader();
        new Thread(() -> {
            try {
                JSONObject account = store.load();
                JSONObject request = ResumableUploader.withCredentials(account, "start_upload")
                        .put("file_size", file.length())
                        .put("client_request_id", pending.getString("client_request_id"));
                JSONObject reservation;
                if (pending.has("session_uri") && pending.has("upload_id")) {
                    reservation = new JSONObject().put("upload_id", pending.getString("upload_id"))
                            .put("session_uri", pending.getString("session_uri"));
                } else {
                    reservation = backend.post(request);
                    if (!reservation.optBoolean("ok")) throw new IllegalStateException(
                            reservation.optString("error", "No se pudo iniciar la subida."));
                    if ("NEW".equals(reservation.optString("status"))) { completed(); return; }
                    if (reservation.optBoolean("needs_renew")) {
                        reservation = backend.post(ResumableUploader.withCredentials(account, "renew_upload")
                                .put("upload_id", reservation.getString("upload_id")));
                        if (!reservation.optBoolean("ok")) throw new IllegalStateException(
                                reservation.optString("error", "No se pudo recuperar la reserva."));
                        if ("NEW".equals(reservation.optString("status"))) { completed(); return; }
                    }
                    pending.put("upload_id", reservation.getString("upload_id"));
                    savePending();
                }
                JSONObject result = uploader.upload(file, reservation, backend, account, new ResumableUploader.Listener() {
                    @Override public void onProgress(long sent, long total) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            int percent = (int) Math.min(100, sent * 100 / total);
                            bar.setProgress(percent);
                            status.setText("Subiendo a BAT… " + percent + "%");
                        });
                    }
                    @Override public void onSession(String uploadId, String sessionUri) throws Exception {
                        pending.put("upload_id", uploadId);
                        pending.put("session_uri", sessionUri);
                        savePending();
                    }
                });
                if (!"NEW".equals(result.optString("status"))) throw new IllegalStateException("El envío no se confirmó.");
                completed();
            } catch (InterruptedException exception) {
                try {
                    if (pending.has("upload_id")) {
                        JSONObject account = store.load();
                        JSONObject response = backend.post(ResumableUploader.withCredentials(account, "cancel_upload")
                                .put("upload_id", pending.getString("upload_id")));
                        if ("NEW".equals(response.optString("status"))) { completed(); return; }
                        if (!response.optBoolean("ok")) throw new IllegalStateException("Cancelación no confirmada.");
                    }
                    clearPending();
                    showFailure("Envío cancelado. Puedes volver a intentarlo más tarde.");
                } catch (Exception failure) {
                    showFailure("No se pudo confirmar la cancelación. Reintenta con Internet.");
                }
            } catch (Exception exception) {
                Log.e("BAT Montaje", "Error de subida", exception);
                String detail = exception.getMessage();
                showFailure(detail != null && detail.contains("límite de envíos")
                        ? "Has alcanzado tu límite de envíos de hoy."
                        : "No se pudo completar la subida. Inténtalo nuevamente.");
            }
        }).start();
    }

    private void cancelUpload() {
        if (running && uploader != null) {
            status.setText("Cancelando…");
            uploader.cancel();
        } else {
            running = true;
            status.setText("Confirmando cancelación…");
            new Thread(() -> {
                try {
                    JSONObject account = store.load();
                    String uploadId = pending.optString("upload_id", "");
                    if (uploadId.isEmpty()) {
                        JSONObject existing = backend.post(ResumableUploader.withCredentials(account, "start_upload")
                                .put("file_size", pending.getLong("file_size"))
                                .put("client_request_id", pending.getString("client_request_id")));
                        if (existing.optBoolean("ok")) uploadId = existing.optString("upload_id", "");
                    }
                    if (!uploadId.isEmpty()) {
                        JSONObject answer = backend.post(ResumableUploader.withCredentials(account, "cancel_upload")
                                .put("upload_id", uploadId));
                        if ("NEW".equals(answer.optString("status"))) { completed(); return; }
                        if (!answer.optBoolean("ok")) throw new IllegalStateException("Cancelación no confirmada.");
                    }
                    clearPending();
                    showFailure("Envío cancelado. Puedes volver a intentarlo más tarde.");
                } catch (Exception exception) {
                    showFailure("No se pudo confirmar la cancelación. Vuelve a intentar con Internet.");
                }
            }).start();
        }
    }

    private synchronized void savePending() throws Exception {
        JSONObject account = store.load();
        account.put("upload_pending", pending);
        store.save(account);
    }

    private synchronized void clearPending() throws Exception {
        JSONObject account = store.load();
        account.remove("upload_pending");
        store.save(account);
    }

    private void completed() throws Exception {
        clearPending();
        runOnUiThread(() -> {
            running = false;
            if (isFinishing() || isDestroyed()) return;
            bar.setProgress(100);
            status.setText("Video enviado correctamente a BAT.");
            retry.setEnabled(false);
            cancel.setEnabled(false);
        });
    }

    private void showFailure(String reason) {
        runOnUiThread(() -> {
            running = false;
            if (isFinishing() || isDestroyed()) return;
            status.setText(reason);
            retry.setEnabled(file != null && file.isFile());
        });
    }

    @Override public void onBackPressed() {
        if (running) {
            status.setText("Espera a que termine el envío o pulsa CANCELAR ENVÍO.");
            return;
        }
        super.onBackPressed();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(8), 0, dp(8)); return view;
    }
    private Button button(String caption, boolean primary) {
        Button button = new Button(this); button.setText(caption); button.setAllCaps(false);
        button.setTextColor(WHITE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                primary ? RED : Color.rgb(36, 39, 44)));
        return button;
    }
}
