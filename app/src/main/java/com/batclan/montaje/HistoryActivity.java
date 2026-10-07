package com.batclan.montaje;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Historial separado: archivos locales elegidos por el usuario y registros en BAT. */
public final class HistoryActivity extends Activity {
    private static final int REAUTHORIZE_DELETE = 42;
    private static final int BG = Color.rgb(8, 9, 11);
    private static final int PANEL = Color.rgb(18, 20, 23);
    private static final int WHITE = Color.rgb(247, 248, 249);
    private static final int MUTED = Color.rgb(160, 165, 172);
    private static final int RED = Color.rgb(245, 27, 52);
    private LinearLayout localList;
    private LinearLayout cloudList;
    private EditText localSearch;
    private int cloudRequest;
    private SecureAccountStore accountStore;
    private String pendingDeleteId;
    private Uri pendingDeleteUri;
    private boolean deletingLocal;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        accountStore = new SecureAccountStore(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(18), dp(24), dp(18), dp(35));
        scroll.addView(root);
        setContentView(scroll);
        root.addView(text("BAT MONTAJE", 24, WHITE));
        root.addView(text("HISTORIAL", 13, RED));
        root.addView(text("MONTAJES LOCALES · 5 MÁS RECIENTES", 19, WHITE));
        localSearch = search(root, "Buscar por nombre...");
        localList = column();
        root.addView(localList);
        root.addView(text("ENVIADOS A BAT · 5 MÁS RECIENTES", 19, WHITE));
        cloudList = column();
        root.addView(cloudList);
        localSearch.addTextChangedListener(watcher(this::renderLocal));
        renderLocal();
        loadCloud();
    }

    private void renderLocal() {
        localList.removeAllViews();
        JSONArray items = new LocalHistoryStore(this).entries();
        String query = localSearch.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        int shown = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            if (!item.optString("name").toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            if (query.isEmpty() && shown >= 5) break;
            shown++;
            LinearLayout card = card();
            card.addView(text(item.optString("name", "Montaje"), 15, WHITE));
            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("es", "PR"))
                    .format(new Date(item.optLong("created_at")));
            card.addView(text(date, 12, MUTED));
            LinearLayout actions = row();
            Button open = button("ABRIR", false);
            actions.addView(open);
            open.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(item.getString("uri")), "video/mp4");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } catch (Exception exception) { alert("No se pudo abrir el archivo. Puede haber sido movido o eliminado."); }
            });
            Button rename = button("CAMBIAR NOMBRE", false);
            actions.addView(rename);
            rename.setOnClickListener(v -> rename(item));
            Button delete = button("ELIMINAR", false);
            delete.setTextColor(RED);
            delete.setOnClickListener(v -> confirmDelete(item));
            card.addView(actions);
            LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            deleteLp.topMargin = dp(5);
            card.addView(delete, deleteLp);
            localList.addView(card);
        }
        if (shown == 0) localList.addView(text(query.isEmpty()
                ? "Todavía no has guardado montajes locales." : "No hay montajes con ese nombre.", 13, MUTED));
    }

    private void rename(JSONObject item) {
        EditText input = new EditText(this);
        input.setText(item.optString("name", ""));
        input.setSingleLine(true);
        input.setTextColor(WHITE);
        new AlertDialog.Builder(this).setTitle("Nombre local del montaje").setView(input)
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("GUARDAR", (dialog, which) -> {
                    try {
                        new LocalHistoryStore(this).rename(item.getString("id"), input.getText().toString());
                        renderLocal();
                    } catch (Exception exception) { alert("No se pudo cambiar el nombre."); }
                }).show();
    }

    private void confirmDelete(JSONObject item) {
        if (deletingLocal) return;
        String id = item.optString("id");
        String address = item.optString("uri");
        if (id.isEmpty() || address.isEmpty()) { alert("No se pudo identificar este montaje."); return; }
        new AlertDialog.Builder(this)
                .setTitle("¿Eliminar este montaje?")
                .setMessage("Esta acción eliminará el montaje guardado localmente en este dispositivo.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dialog, which) -> deleteLocal(id, Uri.parse(address)))
                .show();
    }

    private void deleteLocal(String id, Uri uri) {
        if (deletingLocal) return;
        if (!"content".equals(uri.getScheme()) || !DocumentsContract.isDocumentUri(this, uri)) {
            alert("No se puede eliminar este archivo desde BAT Montaje.");
            return;
        }
        deletingLocal = true;
        new Thread(() -> {
            boolean deleted = false;
            Exception failure = null;
            try {
                deleted = DocumentsContract.deleteDocument(getContentResolver(), uri);
                if (!deleted) throw new IllegalStateException("El proveedor no eliminó el documento.");
                new LocalHistoryStore(this).remove(id);
            } catch (Exception exception) { failure = exception; }
            boolean fileDeleted = deleted;
            Exception finalFailure = failure;
            runOnUiThread(() -> {
                deletingLocal = false;
                renderLocal();
                if (finalFailure == null) return;
                if (!fileDeleted && finalFailure instanceof SecurityException) {
                    requestDeletePermission(id, uri);
                } else if (fileDeleted) {
                    alert("El archivo se eliminó, pero no se pudo actualizar el historial. Inténtalo nuevamente.");
                } else {
                    alert("No se pudo eliminar este montaje. Comprueba que el archivo exista y que su ubicación permita eliminarlo.");
                }
            });
        }).start();
    }

    private void requestDeletePermission(String id, Uri uri) {
        pendingDeleteId = id;
        pendingDeleteUri = uri;
        new AlertDialog.Builder(this)
                .setTitle("Permiso para eliminar")
                .setMessage("Para eliminar un montaje guardado con una versión anterior, selecciona nuevamente ese mismo archivo.")
                .setNegativeButton("CANCELAR", (dialog, which) -> clearPendingDelete())
                .setPositiveButton("SELECCIONAR ARCHIVO", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("video/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivityForResult(intent, REAUTHORIZE_DELETE);
                }).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REAUTHORIZE_DELETE) return;
        String id = pendingDeleteId;
        Uri expected = pendingDeleteUri;
        clearPendingDelete();
        if (resultCode != RESULT_OK || data == null || data.getData() == null || id == null) return;
        Uri selected = data.getData();
        if (!selected.equals(expected)) { alert("Selecciona exactamente el mismo montaje para eliminarlo."); return; }
        int grants = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((grants & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) {
            alert("La ubicación de este montaje no permite eliminarlo desde BAT Montaje."); return;
        }
        try {
            getContentResolver().takePersistableUriPermission(selected, grants);
            deleteLocal(id, selected);
        } catch (SecurityException exception) {
            alert("No se obtuvo permiso para eliminar este montaje.");
        }
    }

    private void clearPendingDelete() {
        pendingDeleteId = null;
        pendingDeleteUri = null;
    }

    private void loadCloud() {
        cloudRequest++;
        cloudList.removeAllViews();
        try {
            if (!"ACTIVE".equals(accountStore.load().optString("mode"))) {
                cloudList.removeAllViews();
                cloudList.addView(text("Accede a tu cuenta BAT para consultar tus envíos.", 13, MUTED));
                return;
            }
        } catch (Exception ignored) { }
        cloudList.addView(text("Consultando envíos…", 13, MUTED));
        final int requestId = cloudRequest;
        new Thread(() -> {
            try {
                JSONObject account = accountStore.load();
                JSONObject response = new BackendClient(accountStore.endpoint()).post(
                        ResumableUploader.withCredentials(account, "my_uploads")
                                .put("offset", 0).put("limit", 5));
                if (!response.optBoolean("ok")) throw new IllegalStateException();
                JSONArray items = response.getJSONArray("uploads");
                runOnUiThread(() -> {
                    if (requestId != cloudRequest) return;
                    showCloud(items, null);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (requestId != cloudRequest) return;
                    showCloud(null, "No se pudo consultar el historial. Revisa tu conexión e inténtalo de nuevo.");
                });
            }
        }).start();
    }

    private void showCloud(JSONArray items, String warning) {
        if (isFinishing() || isDestroyed()) return;
        cloudList.removeAllViews();
        if (warning != null) cloudList.addView(text(warning +
                (items == null || items.length() == 0 ? "" : " Mostrando los resultados ya cargados."), 12, RED));
        if (items == null) return;
        if (items.length() == 0) {
            cloudList.addView(text("Todavía no hay envíos registrados.", 13, MUTED));
            return;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            LinearLayout card = card();
            card.addView(text(item.optString("filename", "Vídeo"), 14, WHITE));
            card.addView(text(statusName(item.optString("status")), 12, RED));
            card.addView(text(item.optString("uploaded_at", ""), 11, MUTED));
            cloudList.addView(card);
        }
    }

    private String statusName(String status) {
        switch (status) {
            case "UPLOADING": return "Subiendo";
            case "NEW": return "Enviado";
            case "APPROVED": return "Aprobado";
            case "REJECTED": return "No aprobado";
            case "COMPLETED": return "Completado";
            case "FAILED": return "Error";
            default: return "Estado no disponible";
        }
    }

    private EditText search(LinearLayout parent, String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setTextSize(14);
        parent.addView(input);
        return input;
    }

    private TextWatcher watcher(Runnable callback) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable text) { callback.run(); }
        };
    }

    private void alert(String message) {
        new AlertDialog.Builder(this).setTitle("BAT Montaje").setMessage(message)
                .setPositiveButton("ENTENDIDO", null).show();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); return box; }
    private LinearLayout row() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.HORIZONTAL); return box; }
    private LinearLayout card() {
        LinearLayout box = column(); box.setPadding(dp(12), dp(12), dp(12), dp(12)); box.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10); box.setLayoutParams(params); return box;
    }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(7), 0, dp(7)); return view;
    }
    private Button button(String caption, boolean primary) {
        Button button = new Button(this); button.setText(caption); button.setAllCaps(false);
        button.setTextColor(WHITE); button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                primary ? RED : Color.rgb(36, 39, 44))); return button;
    }
}
