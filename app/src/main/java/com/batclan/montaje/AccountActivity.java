package com.batclan.montaje;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Locale;
import java.util.UUID;

/** Pantalla de registro/validación. El editor local permanece separado. */
public final class AccountActivity extends Activity {
    private static final int BG = Color.rgb(8, 9, 11);
    private static final int PANEL = Color.rgb(18, 20, 23);
    private static final int WHITE = Color.rgb(247, 248, 249);
    private static final int MUTED = Color.rgb(160, 165, 172);
    private static final int RED = Color.rgb(245, 27, 52);
    private SecureAccountStore store;
    private JSONObject state;
    private LinearLayout body;
    private TextView message;
    private boolean busy;
    private boolean profileRequested;
    private boolean loginScreen;
    private boolean forgotScreen;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        store = new SecureAccountStore(this);
        profileRequested = getIntent().getBooleanExtra("show_profile", false);
        try {
            state = store.load();
            if (state.length() == 0 && store.hasSignedOut()) loginScreen = true;
            if ("PENDING".equals(state.optString("mode"))) {
                state.put("mode", "PENDIENTE");
                store.save(state);
            }
            if ("BANNED".equals(state.optString("mode"))) {
                state.put("mode", "BANEADO");
                store.save(state);
            }
        }
        catch (Exception exception) {
            state = new JSONObject();
            showFatal("No se pudo leer la sesión cifrada. No se han borrado tus datos.");
            return;
        }
        render();
        if (!profileRequested && !"ACTIVE".equals(state.optString("mode"))) checkStartupAvailability();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(20), dp(25), dp(20), dp(35));
        scroll.addView(root);
        setContentView(scroll);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.bat_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(logo, new LinearLayout.LayoutParams(dp(80), dp(80)));
        TextView title = text("BAT MONTAJE", 27, WHITE);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title);
        root.addView(text("CLAN BAT · CUENTA Y ACCESO", 11, RED));
        body = column();
        body.setPadding(dp(16), dp(16), dp(16), dp(20));
        body.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(24);
        root.addView(body, bodyParams);
        message = text("", 13, MUTED);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(16);
        root.addView(message, messageParams);
        String mode = state.optString("mode", "");
        if (forgotScreen) showForgotPassword();
        else if (loginScreen) showLogin();
        else if ("PENDIENTE".equals(mode) || "REJECTED".equals(mode) ||
                "SUSPENDED".equals(mode) || "BANEADO".equals(mode)) showPending();
        else if ("ACTIVE".equals(mode)) {
            if (profileRequested) showProfile(); else showActive();
        }
        else showRegistration();
    }

    private void showRegistration() {
        body.addView(text("REGISTRO", 20, WHITE));
        body.addView(text("Completa tus datos para solicitar acceso a BAT.", 13, MUTED));
        EditText endpointInput = input("URL del servidor BAT (Apps Script)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpointInput.setText(store.endpoint());
        if (!store.endpoint().isEmpty()) endpointInput.setVisibility(View.GONE);
        EditText nameInput = input("Nombre del jugador", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        body.removeView(nameInput);
        LinearLayout suffixRow = new LinearLayout(this);
        suffixRow.setOrientation(LinearLayout.HORIZONTAL);
        suffixRow.setGravity(Gravity.CENTER_VERTICAL);
        suffixRow.addView(nameInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView suffix = text("-BAT", 16, RED);
        suffix.setPadding(dp(12), dp(10), dp(10), dp(10));
        suffixRow.addView(suffix);
        body.addView(suffixRow);
        body.addView(text("El sufijo del clan se añadirá una sola vez.", 12, MUTED));
        EditText idInput = input("Player ID", InputType.TYPE_CLASS_TEXT);
        EditText emailInput = input("Correo electrónico", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText passwordInput = input("Contraseña (mínimo 12 caracteres)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirmInput = input("Confirmar contraseña",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button register = button("REGISTRARSE", true);
        body.addView(register);
        register.setOnClickListener(v -> {
            if (busy) return;
            String endpoint = endpointInput.getText().toString().trim();
            if (!BackendClient.validEndpoint(endpoint)) { message.setText("No se pudo conectar con BAT. Inténtalo más tarde."); return; }
            String name = normalizeName(nameInput.getText().toString());
            String id = idInput.getText().toString();
            String email = emailInput.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (name == null || id.isEmpty() || id.length() > 64 || !id.equals(id.trim()) ||
                    id.matches("^[=+@-].*") || id.matches("(?s).*\\p{Cntrl}.*") ||
                    !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ||
                    email.matches("^[=+@-].*") || email.length() > 254) {
                message.setText("Revisa el nombre, Player ID y correo electrónico.");
                return;
            }
            String password = passwordInput.getText().toString();
            if (password.length() < 12 || password.length() > 128) {
                message.setText("La contraseña debe tener entre 12 y 128 caracteres.");
                return;
            }
            if (!password.equals(confirmInput.getText().toString())) {
                message.setText("Las contraseñas no coinciden.");
                return;
            }
            new AlertDialog.Builder(this).setTitle("Vinculación del dispositivo")
                    .setMessage("Tu cuenta BAT quedará vinculada a este dispositivo. Una cuenta solo puede estar asociada a un dispositivo.")
                    .setNegativeButton("CANCELAR", null)
                    .setPositiveButton("ACEPTAR Y CONTINUAR", (dialog, which) -> {
                        try { store.setEndpoint(endpoint); }
                        catch (Exception exception) { message.setText("No se pudo preparar el registro."); return; }
                        busy = true;
                        register.setEnabled(false);
                        message.setText("Protegiendo tu cuenta…");
                        new Thread(() -> {
                            try {
                                String salt = PasswordProof.newSalt();
                                String key = PasswordProof.derive(password, salt, PasswordProof.ITERATIONS);
                                JSONObject pending = new JSONObject()
                                        .put("mode", "PENDIENTE").put("registration_sent", false)
                                        .put("player_name", name).put("player_id", id).put("email", email)
                                        .put("registration_id", UUID.randomUUID().toString())
                                        .put("installation_id", UUID.randomUUID().toString())
                                        .put("device_binding_id", DeviceBinding.id(this))
                                        .put("pending_token", SecureAccountStore.randomToken())
                                        .put("password_salt", salt).put("password_key", key);
                                store.save(pending);
                                runOnUiThread(() -> {
                                    if (isFinishing() || isDestroyed()) return;
                                    busy = false;
                                    state = pending;
                                    render();
                                    sendRegistration();
                                });
                            } catch (Exception exception) {
                                runOnUiThread(() -> {
                                    busy = false;
                                    register.setEnabled(true);
                                    message.setText("No se pudo guardar la solicitud de forma segura.");
                                });
                            }
                        }).start();
                    }).show();
        });
        Button access = button("ACCEDER A MI CUENTA", false);
        body.addView(access);
        access.setOnClickListener(v -> { loginScreen = true; forgotScreen = false; render(); });
    }

    private void showLogin() {
        body.addView(text("ACCEDER A MI CUENTA", 20, WHITE));
        body.addView(text("Utiliza el correo y la contraseña de tu cuenta BAT.", 13, MUTED));
        EditText emailInput = input("Correo electrónico",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setText(state.optString("email", ""));
        EditText passwordInput = input("Contraseña",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button access = button("ACCEDER", true);
        body.addView(access);
        access.setOnClickListener(v -> {
            if (busy) return;
            String email = emailInput.getText().toString().trim().toLowerCase(Locale.ROOT);
            String password = passwordInput.getText().toString();
            if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || password.isEmpty()) {
                message.setText("Introduce tu correo y contraseña.");
                return;
            }
            passwordInput.setText("");
            String installationId = UUID.randomUUID().toString();
            String accessToken = SecureAccountStore.randomToken();
            access.setEnabled(false);
            runRequest("Accediendo a tu cuenta…", () -> {
                BackendClient backend = new BackendClient(store.endpoint());
                JSONObject saltResponse = backend.post(new JSONObject()
                        .put("action", "password_salt").put("email", email));
                if (!saltResponse.optBoolean("ok")) return saltResponse;
                String key = PasswordProof.derive(password, saltResponse.getString("salt"),
                        saltResponse.getInt("iterations"));
                return backend.post(new JSONObject().put("action", "login")
                        .put("email", email).put("password_key", key)
                        .put("device_binding_id", DeviceBinding.id(this))
                        .put("installation_id", installationId).put("access_token", accessToken));
            }, response -> {
                access.setEnabled(true);
                if (!response.optBoolean("ok")) {
                    message.setText(accountError(response, "No se pudo acceder a la cuenta."));
                    return;
                }
                try {
                    state = new JSONObject().put("mode", "ACTIVE")
                            .put("email", email).put("installation_id", installationId)
                            .put("access_token", accessToken)
                            .put("last_status", response.optString("status"));
                    store.save(state);
                    loginScreen = false;
                    if (response.optBoolean("authorized")) openEditor(false);
                    else { profileRequested = true; render(); }
                } catch (Exception exception) {
                    message.setText("No se pudo guardar tu sesión en este dispositivo.");
                }
            });
        });
        Button forgot = button("OLVIDÉ MI CONTRASEÑA", false);
        body.addView(forgot);
        forgot.setOnClickListener(v -> { loginScreen = false; forgotScreen = true; render(); });
        Button back = button("VOLVER AL REGISTRO", false);
        body.addView(back);
        back.setOnClickListener(v -> { loginScreen = false; render(); });
    }

    private void showForgotPassword() {
        body.addView(text("OLVIDÉ MI CONTRASEÑA", 20, WHITE));
        body.addView(text("Te enviaremos un enlace temporal al correo de tu cuenta BAT.", 13, MUTED));
        EditText emailInput = input("Correo electrónico",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setText(state.optString("email", ""));
        Button send = button("ENVIAR ENLACE", true);
        body.addView(send);
        send.setOnClickListener(v -> {
            if (busy) return;
            String email = emailInput.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                message.setText("Introduce un correo válido."); return;
            }
            send.setEnabled(false);
            runRequest("Enviando instrucciones…", () -> new BackendClient(store.endpoint()).post(
                    new JSONObject().put("action", "request_password_reset").put("email", email)), response -> {
                send.setEnabled(true);
                message.setText(response.optBoolean("ok")
                        ? "Si el correo está registrado, recibirás un enlace para restablecer tu contraseña."
                        : "No se pudo enviar el enlace. Inténtalo más tarde.");
            });
        });
        Button back = button("VOLVER A ACCEDER", false);
        body.addView(back);
        back.setOnClickListener(v -> { forgotScreen = false; loginScreen = true; render(); });
    }

    private void showProfile() {
        body.addView(text("CUENTA BAT", 20, WHITE));
        body.addView(text("Consultando información de tu cuenta…", 13, MUTED));
        loadProfile();
    }

    private void loadProfile() {
        if (busy) return;
        runRequest("Actualizando cuenta…", () -> new BackendClient(store.endpoint()).post(
                ResumableUploader.withCredentials(state, "account_profile")), response -> {
            body.removeAllViews();
            body.addView(text("CUENTA BAT", 20, WHITE));
            if (!response.optBoolean("ok")) {
                body.addView(text("No se pudo consultar la cuenta. Accede nuevamente si tu sesión terminó.", 13, MUTED));
                Button login = button("ACCEDER A MI CUENTA", true);
                body.addView(login);
                login.setOnClickListener(v -> { loginScreen = true; profileRequested = false; render(); });
                addLogoutButton();
                return;
            }
            body.addView(text("Nombre: " + response.optString("player_name"), 14, WHITE));
            body.addView(text("Player ID: " + response.optString("player_id"), 14, WHITE));
            body.addView(text("Correo electrónico: " + response.optString("email"), 13, MUTED));
            body.addView(text("Fecha de creación: " + displayDate(response.optString("registration_date")), 13, MUTED));
            body.addView(text("Fecha de aprobación: " + displayDate(response.optString("approval_date")), 13, MUTED));
            body.addView(text("Estado: " + statusName(response.optString("status")), 15, RED));
            int limit = response.optInt("daily_upload_limit", 0);
            int used = response.optInt("uploads_used_today", 0);
            if ("APPROVED".equals(response.optString("status"))) {
                body.addView(text("Uso de hoy: " + used + " de " + limit + " videos", 14, WHITE));
                body.addView(text("Disponibles: " + Math.max(0, limit - used) + " videos", 14, WHITE));
            } else {
                body.addView(text("Puedes crear montajes locales. Los envíos a BAT requieren una cuenta aprobada.", 13, MUTED));
            }
            if (!response.optBoolean("password_set")) {
                Button createPassword = button("ESTABLECER CONTRASEÑA", true);
                body.addView(createPassword);
                createPassword.setOnClickListener(v -> promptSetPassword());
            }
            if ("APPROVED".equals(response.optString("status"))) {
                Button linkWeb = button("VINCULAR BAT WEB", false);
                body.addView(linkWeb);
                linkWeb.setOnClickListener(v -> requestWebLinkCode());
            }
            Button refresh = button("ACTUALIZAR CUENTA", false);
            body.addView(refresh);
            refresh.setOnClickListener(v -> loadProfile());
            addLogoutButton();
            message.setText("");
        });
    }

    private void requestWebLinkCode() {
        if (busy) return;
        new AlertDialog.Builder(this).setTitle("Vincular BAT web")
                .setMessage("Se creará un código temporal para usar tu cuenta también en una instalación web. Si ya tienes una vinculada, la nueva la reemplazará.")
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("CREAR CÓDIGO", (dialog, which) ->
                    runRequest("Creando código…", () -> new BackendClient(store.endpoint()).post(
                        ResumableUploader.withCredentials(state, "create_web_link_code")), response -> {
                        if (!response.optBoolean("ok")) {
                            message.setText("No se pudo crear el código. Inténtalo nuevamente.");
                            return;
                        }
                        new AlertDialog.Builder(this).setTitle("Código para BAT web")
                                .setMessage(response.optString("code") +
                                        "\n\nEscribe este código en BAT Montaje web junto con tu correo y contraseña. Caduca en 10 minutos y solo puede utilizarse una vez.")
                                .setPositiveButton("ENTENDIDO", null).show();
                    })).show();
    }

    private void promptSetPassword() {
        LinearLayout fields = column();
        fields.setPadding(20, 8, 20, 8);
        EditText first = new EditText(this);
        first.setHint("Contraseña nueva (mínimo 12 caracteres)");
        first.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        fields.addView(first);
        EditText confirm = new EditText(this);
        confirm.setHint("Confirmar contraseña");
        confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        fields.addView(confirm);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Establecer contraseña")
                .setView(fields).setNegativeButton("CANCELAR", null)
                .setPositiveButton("GUARDAR", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = first.getText().toString();
            if (password.length() < 12 || password.length() > 128 || !password.equals(confirm.getText().toString())) {
                message.setText("Usa al menos 12 caracteres y confirma la misma contraseña."); return;
            }
            first.setText(""); confirm.setText(""); dialog.dismiss();
            runRequest("Protegiendo contraseña…", () -> {
                String salt = PasswordProof.newSalt();
                String key = PasswordProof.derive(password, salt, PasswordProof.ITERATIONS);
                return new BackendClient(store.endpoint()).post(ResumableUploader.withCredentials(state, "set_password")
                        .put("password_salt", salt).put("password_key", key));
            }, response -> {
                if (response.optBoolean("ok")) loadProfile();
                else message.setText(accountError(response, "No se pudo guardar la contraseña."));
            });
        }));
        dialog.show();
    }

    private String displayDate(String iso) {
        return iso.length() >= 10 ? iso.substring(0, 10) : "Pendiente";
    }

    private String statusName(String status) {
        switch (normalizedStatus(status)) {
            case "APPROVED": return "Aprobada";
            case "REJECTED": return "Rechazada";
            case "SUSPENDED": return "Suspendida";
            case "BANEADO": return "Baneada";
            default: return "Pendiente";
        }
    }

    private void showPending() {
        String mode = state.optString("mode", "");
        String title = "PENDIENTE".equals(mode) ? "SOLICITUD PENDIENTE" :
                "REJECTED".equals(mode) ? "REGISTRO RECHAZADO" :
                        "SUSPENDED".equals(mode) ? "CUENTA SUSPENDIDA" : "CUENTA BANEADA";
        body.addView(text(title, 20, WHITE));
        body.addView(text("Jugador: " + state.optString("player_name", ""), 14, MUTED));
        body.addView(text("Correo: " + state.optString("email", ""), 13, MUTED));
        if (!state.optBoolean("registration_sent", false)) {
            Button retry = button("REINTENTAR ENVÍO", true);
            body.addView(retry);
            retry.setOnClickListener(v -> sendRegistration());
        }
        Button refresh = button("COMPROBAR ESTADO", false);
        body.addView(refresh);
        refresh.setOnClickListener(v -> checkPending());
        Button access = button("ACCEDER A MI CUENTA", false);
        body.addView(access);
        access.setOnClickListener(v -> { loginScreen = true; render(); });
        message.setText("PENDIENTE".equals(mode)
                ? "Un administrador debe aprobar tu registro. Puedes volver a comprobarlo aquí."
                : "Contacta a un administrador BAT si crees que hay un error.");
    }

    private void showActive() {
        body.addView(text("VALIDANDO CUENTA", 20, WHITE));
        body.addView(text("Comprobando permisos y límite actual…", 13, MUTED));
        validateActive();
    }

    private void sendRegistration() {
        if (busy) return;
        runRequest("Enviando solicitud…", () -> new BackendClient(store.endpoint()).post(new JSONObject()
                .put("action", "register")
                .put("player_name", state.getString("player_name"))
                .put("player_id", state.getString("player_id"))
                .put("email", state.getString("email"))
                .put("registration_id", state.getString("registration_id"))
                .put("installation_id", state.getString("installation_id"))
                .put("device_binding_id", state.getString("device_binding_id"))
                .put("pending_token", state.getString("pending_token"))
                .put("password_salt", state.getString("password_salt"))
                .put("password_key", state.getString("password_key"))), response -> {
            if (response.optBoolean("ok") && ("PENDIENTE".equals(response.optString("status")) ||
                    "PENDING".equals(response.optString("status")))) {
                try {
                    state.put("registration_sent", true);
                    state.remove("password_key");
                    state.remove("password_salt");
                    store.save(state);
                }
                catch (Exception exception) { message.setText("Solicitud enviada, pero no se pudo guardar el estado local."); return; }
                render();
                message.setText("Solicitud enviada. Espera la aprobación de BAT.");
            } else message.setText(accountError(response, "No se pudo registrar."));
        });
    }

    private void checkPending() {
        if (busy) return;
        if (state.has("activation_token")) {
            runRequest("Recuperando activación…", () -> new BackendClient(store.endpoint()).post(new JSONObject()
                    .put("action", "validate")
                    .put("installation_id", state.getString("installation_id"))
                    .put("device_binding_id", state.getString("device_binding_id"))
                    .put("access_token", state.getString("activation_token"))), response -> {
                if (response.optBoolean("ok") && response.optBoolean("authorized")) {
                    finishActivation();
                } else {
                    checkPendingRegistration();
                }
            });
            return;
        }
        checkPendingRegistration();
    }

    private void checkPendingRegistration() {
        if (busy) return;
        runRequest("Consultando estado…", () -> new BackendClient(store.endpoint()).post(new JSONObject()
                .put("action", "registration_status")
                .put("registration_id", state.getString("registration_id"))
                .put("installation_id", state.getString("installation_id"))
                .put("device_binding_id", state.getString("device_binding_id"))
                .put("pending_token", state.getString("pending_token"))), response -> {
            if (!response.optBoolean("ok")) { message.setText(accountError(response, "No se pudo consultar la cuenta.")); return; }
            String status = normalizedStatus(response.optString("status"));
            if ("APPROVED".equals(status)) activate();
            else if ("REJECTED".equals(status) || "BANEADO".equals(status) || "SUSPENDED".equals(status)) {
                try { state.put("mode", status); store.save(state); render(); }
                catch (Exception exception) { message.setText("No se pudo guardar el estado."); }
            } else message.setText("Tu solicitud todavía está pendiente de aprobación.");
        });
    }

    private void activate() {
        if (busy) return;
        try {
            if (!state.has("activation_token")) {
                state.put("activation_token", SecureAccountStore.randomToken());
                store.save(state);
            }
        } catch (Exception exception) { message.setText("No se pudo preparar la sesión cifrada."); return; }
        runRequest("Activando cuenta…", () -> new BackendClient(store.endpoint()).post(new JSONObject()
                .put("action", "activate")
                .put("registration_id", state.getString("registration_id"))
                .put("installation_id", state.getString("installation_id"))
                .put("device_binding_id", state.getString("device_binding_id"))
                .put("pending_token", state.getString("pending_token"))
                .put("access_token", state.getString("activation_token"))), response -> {
            if (response.optBoolean("ok") && response.optBoolean("authorized")) {
                finishActivation();
            } else message.setText(accountError(response, "La cuenta aún no está autorizada."));
        });
    }

    private void finishActivation() {
        try {
            state.put("mode", "ACTIVE");
            state.put("access_token", state.getString("activation_token"));
            state.remove("activation_token"); state.remove("pending_token");
            state.put("last_status", "APPROVED");
            store.save(state);
            openEditor(false);
        } catch (Exception exception) { message.setText("No se pudo guardar la sesión."); }
    }

    private void validateActive() {
        if (busy) return;
        runRequest("Comprobando permisos…", () -> new BackendClient(store.endpoint()).post(new JSONObject()
                .put("action", "validate")
                .put("installation_id", state.getString("installation_id"))
                .put("device_binding_id", state.getString("device_binding_id"))
                .put("access_token", state.getString("access_token"))), response -> {
            if (response.optBoolean("ok") && response.optBoolean("authorized")) {
                try { state.put("last_status", "APPROVED"); store.save(state); }
                catch (Exception exception) { message.setText("No se pudo guardar el estado."); return; }
                openEditor(false);
            } else if (response.optBoolean("ok")) {
                String status = normalizedStatus(response.optString("status", ""));
                try { state.put("last_status", status); store.save(state); }
                catch (Exception ignored) {}
                profileRequested = true;
                render();
            } else {
                body.removeAllViews();
                body.addView(text("No se pudo validar la sesión.", 13, RED));
                Button access = button("ACCEDER A MI CUENTA", true);
                body.addView(access);
                access.setOnClickListener(v -> { loginScreen = true; render(); });
                message.setText("Accede nuevamente para utilizar tu cuenta BAT.");
            }
        });
    }

    private interface Request { JSONObject run() throws Exception; }
    private interface Result { void apply(JSONObject response); }

    private void runRequest(String progress, Request request, Result result) {
        if (busy) return;
        busy = true;
        message.setText(progress);
        new Thread(() -> {
            JSONObject response = null;
            Exception failure = null;
            try { response = request.run(); } catch (Exception exception) { failure = exception; }
            JSONObject finalResponse = response;
            Exception finalFailure = failure;
            runOnUiThread(() -> {
                busy = false;
                if (isFinishing() || isDestroyed()) return;
                if (finalFailure != null) {
                    message.setText("No se pudo conectar con BAT. Revisa Internet y vuelve a intentar.");
                    showOfflineFallback();
                } else result.apply(finalResponse);
            });
        }).start();
    }

    private void openEditor(boolean offline) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("session_validated", true);
        intent.putExtra("offline_local", offline);
        startActivity(intent);
        finish();
    }

    private boolean hasLocalFallback() {
        for (int i = 0; i < body.getChildCount(); i++) {
            View child = body.getChildAt(i);
            if (child instanceof Button && "CONTINUAR EN LOCAL".contentEquals(((Button) child).getText())) return true;
        }
        return false;
    }

    private void showOfflineFallback() {
        if (profileRequested || hasLocalFallback()) return;
        Button local = button("CONTINUAR EN LOCAL", false);
        body.addView(local);
        local.setOnClickListener(v -> openEditor(true));
    }

    private void checkStartupAvailability() {
        if (store.endpoint().isEmpty()) { showOfflineFallback(); return; }
        new Thread(() -> {
            boolean available;
            try {
                available = new BackendClient(store.endpoint()).post(
                        new JSONObject().put("action", "health")).optBoolean("ok");
            } catch (Exception exception) { available = false; }
            boolean result = available;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed() && !result) showOfflineFallback();
            });
        }).start();
    }

    private void addLogoutButton() {
        Button logout = button("Salir de la cuenta", true);
        body.addView(logout);
        logout.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("¿Salir de tu cuenta?")
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("SALIR", (dialog, which) -> logout())
                .show());
    }

    private void logout() {
        if (busy) return;
        runRequest("Cerrando sesión…", () -> new BackendClient(store.endpoint()).post(
                ResumableUploader.withCredentials(state, "logout")), response -> {
            if (!response.optBoolean("ok")) {
                message.setText("No se pudo cerrar la sesión. Inténtalo de nuevo.");
                return;
            }
            store.clear();
            state = new JSONObject();
            profileRequested = false;
            forgotScreen = false;
            loginScreen = true;
            Intent intent = new Intent(this, AccountActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void showFatal(String explanation) {
        new AlertDialog.Builder(this).setTitle("BAT Montaje")
                .setMessage(explanation).setPositiveButton("CERRAR", (dialog, which) -> finish()).show();
    }

    private String normalizeName(String value) {
        String name = value.trim().replaceAll("\\s+", " ");
        while (name.toUpperCase(Locale.ROOT).endsWith("-BAT")) name = name.substring(0, name.length() - 4).trim();
        if (name.length() < 2 || name.length() > 32 || !name.matches("[\\p{L}\\p{N} _.\\-]+") ||
                name.matches("^[=+@-].*")) return null;
        return name + "-BAT";
    }

    private String normalizedStatus(String status) {
        return "BANNED".equals(status) ? "BANEADO" : status;
    }

    private String accountError(JSONObject response, String fallback) {
        String detail = response.optString("error", "");
        switch (detail) {
            case "Correo o contraseña incorrectos.":
            case "Demasiados intentos. Vuelve a intentarlo más tarde.":
            case "Ya existe una solicitud para este correo o identificador.":
            case "La cuenta ya tiene contraseña. Usa la recuperación por correo para cambiarla.":
            case "Esta cuenta está vinculada a otro dispositivo.":
            case "Solicitud no reconocida.":
                return detail;
            default:
                return fallback;
        }
    }

    private LinearLayout column() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); return box; }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(6), 0, dp(6)); return view;
    }
    private EditText input(String hint, int type) {
        EditText field = new EditText(this); field.setHint(hint); field.setHintTextColor(MUTED);
        field.setTextColor(WHITE); field.setTextSize(15);
        field.setSingleLine(true);
        field.setInputType(type);
        if ((type & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            field.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        }
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12); body.addView(field, params); return field;
    }
    private Button button(String label, boolean primary) {
        Button view = new Button(this); view.setText(label); view.setAllCaps(false);
        view.setTextColor(WHITE); view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary ? RED : Color.rgb(37, 40, 44)));
        return view;
    }
}
