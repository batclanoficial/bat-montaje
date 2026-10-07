package com.batclan.montaje;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import androidx.media3.ui.PlayerView;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Portrait-first, offline montage editor. The Android build intentionally has no ANALIZAR feature. */
public final class MainActivity extends Activity {
    private static final int PICK_VIDEO = 10;
    private static final int SAVE_VIDEO = 11;
    private static final int RED = Color.rgb(245, 27, 52);
    private static final int BG = Color.rgb(8, 9, 11);
    private static final int PANEL = Color.rgb(18, 20, 23);
    private static final int PANEL_LIGHT = Color.rgb(27, 30, 34);
    private static final int BORDER = Color.rgb(49, 53, 58);
    private static final int WHITE = Color.rgb(247, 248, 249);
    private static final int MUTED = Color.rgb(160, 165, 172);
    private static final String[] TYPES = {"KILL", "CLUTCH", "COMBATE", "OTRO"};

    private final ArrayList<MontageLogic.Event> events = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Uri sourceUri;
    private long durationMs = -1;
    private boolean sourceHasAudio = true;
    private ExoPlayer player;
    private Transformer transformer;
    private File tempOutput;
    private boolean exporting;
    private boolean uploadAfterExport;
    private String newEventType = TYPES[0];
    private TextView fileLabel;
    private TextView durationLabel;
    private TextView eventCount;
    private TextView statusLabel;
    private TextView exportSummary;
    private ScrollView pageScroll;
    private View previewSection;
    private LinearLayout eventList;
    private EditText timestampInput;
    private EditText endTimestampInput;
    private EditText customNameInput;
    private TextView timestampLabel;
    private Button startMarkerButton;
    private LinearLayout combatFields;
    private LinearLayout customNameFields;
    private EditText beforeInput;
    private EditText afterInput;
    private LinearLayout typeChooser;
    private SeekBar previewSeek;
    private Button previewPlay;
    private TextView previewClock;
    private boolean previewScrubbing;
    private final Runnable previewTick = new Runnable() {
        @Override public void run() {
            updatePreviewControls();
            handler.postDelayed(this, 250);
        }
    };
    private Button exportButton;
    private Button uploadButton;
    private boolean uploadValidating;
    private AlertDialog progressDialog;
    private ProgressBar progressBar;
    private TextView progressLabel;
    private Runnable progressTick;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SecureAccountStore accountStore = new SecureAccountStore(this);
        if (!accountStore.endpoint().isEmpty() &&
                !getIntent().getBooleanExtra("session_validated", false)) {
            startActivity(new Intent(this, AccountActivity.class));
            finish();
            return;
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        buildUi();
        renderEvents();
        try {
            JSONObject account = accountStore.load();
            if (account.optJSONObject("upload_pending") != null) {
                handler.post(() -> new AlertDialog.Builder(this)
                        .setTitle("Envío pendiente")
                        .setMessage("Hay un montaje cuyo envío a BAT no ha terminado. ¿Quieres reanudarlo?")
                        .setPositiveButton("REANUDAR", (dialog, which) ->
                                startActivity(new Intent(this, UploadActivity.class)))
                        .setNegativeButton("MÁS TARDE", null)
                        .show());
            }
        } catch (Exception ignored) { }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        pageScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setClipToPadding(true);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            scroll.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout root = column();
        root.setPadding(dp(16), dp(16), dp(16), dp(30));
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.bat_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setBackground(shape(PANEL, BORDER, 16));
        header.addView(logo, new LinearLayout.LayoutParams(dp(68), dp(68)));
        LinearLayout brand = column();
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        brandLp.leftMargin = dp(13);
        header.addView(brand, brandLp);
        TextView title = label("BAT MONTAJE", 24, WHITE, true);
        title.setLetterSpacing(0.055f);
        brand.addView(title);
        TextView subtitle = label("TUS MEJORES MOMENTOS", 10, RED, true);
        subtitle.setLetterSpacing(0.18f);
        topMargin(brand, subtitle, 4);
        root.addView(header);

        Button account = button("CUENTA BAT", false);
        topMargin(root, account, 14);
        account.setOnClickListener(v -> {
            Intent intent = new Intent(this, AccountActivity.class);
            intent.putExtra("show_profile", true);
            startActivity(intent);
        });
        Button history = button("HISTORIAL", false);
        topMargin(root, history, 8);
        history.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));

        TextView intro = label("R A I N B O W   S I X   M O B I L E", 10, MUTED, false);
        intro.setGravity(Gravity.CENTER);
        topMargin(root, intro, 14);

        LinearLayout videoCard = card(root, 18);
        videoCard.addView(sectionTitle("01", "VIDEO FUENTE"));
        fileLabel = label("Ningún vídeo seleccionado", 15, WHITE, true);
        fileLabel.setMaxLines(2);
        topMargin(videoCard, fileLabel, 14);
        durationLabel = label("Ningún video seleccionado. Selecciona un video para comenzar.", 12, MUTED, false);
        topMargin(videoCard, durationLabel, 5);
        Button choose = button("SELECCIONAR VIDEO", false);
        topMargin(videoCard, choose, 16);
        choose.setOnClickListener(v -> chooseVideo());

        LinearLayout previewCard = card(root, 14);
        previewSection = previewCard;
        previewCard.addView(sectionTitle("02", "VISTA PREVIA"));
        TextView previewHint = label("Reproduce y desliza la barra para ubicar tus momentos.", 12, MUTED, false);
        topMargin(previewCard, previewHint, 8);
        PlayerView playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams playerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230));
        playerLp.topMargin = dp(12);
        previewCard.addView(playerView, playerLp);
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        LinearLayout previewControls = row();
        previewControls.setGravity(Gravity.CENTER_VERTICAL);
        topMargin(previewCard, previewControls, 10);
        previewPlay = button("▶", false);
        previewPlay.setContentDescription("Reproducir o pausar");
        previewControls.addView(previewPlay, new LinearLayout.LayoutParams(dp(52), dp(44)));
        previewPlay.setOnClickListener(v -> {
            if (player.isPlaying()) player.pause(); else player.play();
            updatePreviewControls();
        });
        previewSeek = new SeekBar(this);
        previewSeek.setMax(1000);
        previewSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(RED));
        previewSeek.setThumbTintList(android.content.res.ColorStateList.valueOf(RED));
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        seekLp.leftMargin = dp(6);
        previewControls.addView(previewSeek, seekLp);
        previewSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar seekBar) { previewScrubbing = true; }
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && durationMs > 0) previewClock.setText(
                        MontageLogic.formatTime(durationMs * progress / 1000) + " / " + MontageLogic.formatTime(durationMs));
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (durationMs > 0) player.seekTo(durationMs * seekBar.getProgress() / 1000);
                previewScrubbing = false;
                updatePreviewControls();
            }
        });
        previewClock = label("0:00 / 0:00", 11, MUTED, true);
        previewClock.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        previewControls.addView(previewClock);
        LinearLayout seekButtons = row();
        topMargin(previewCard, seekButtons, 5);
        Button backTen = button("−10 s", false);
        Button forwardTen = button("+10 s", false);
        seekButtons.addView(backTen, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams forwardLp = new LinearLayout.LayoutParams(0, dp(42), 1);
        forwardLp.leftMargin = dp(8);
        seekButtons.addView(forwardTen, forwardLp);
        backTen.setOnClickListener(v -> seekPreviewBy(-10000));
        forwardTen.setOnClickListener(v -> seekPreviewBy(10000));
        handler.post(previewTick);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && player.getDuration() > 0 && durationMs <= 0) {
                    durationMs = player.getDuration();
                    updateDuration();
                    updateActionButtons();
                    updatePreviewControls();
                }
            }
            @Override public void onIsPlayingChanged(boolean isPlaying) { updatePreviewControls(); }
            @Override public void onMediaItemTransition(MediaItem mediaItem, int reason) { updatePreviewControls(); }
            @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                Log.e("BATMontaje", "Vista previa", error);
                status("No se puede reproducir este video.");
            }
        });

        LinearLayout settingsCard = card(root, 14);
        settingsCard.addView(sectionTitle("03", "AJUSTES DE CLIPS"));
        topMargin(settingsCard, label("Se aplican a cada evento nuevo. Puedes editar cada uno después.", 12, MUTED, false), 8);
        LinearLayout settingsRow = row();
        settingsRow.setGravity(Gravity.CENTER_VERTICAL);
        topMargin(settingsCard, settingsRow, 12);
        LinearLayout beforeBox = inputBlock("SEGUNDOS ANTES", "7");
        beforeInput = (EditText) beforeBox.getChildAt(1);
        settingsRow.addView(beforeBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout afterBox = inputBlock("SEGUNDOS DESPUÉS", "3");
        afterInput = (EditText) afterBox.getChildAt(1);
        LinearLayout.LayoutParams afterLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        afterLp.leftMargin = dp(12);
        settingsRow.addView(afterBox, afterLp);
        beforeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        afterInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        Button applyAll = button("APLICAR A TODOS", false);
        topMargin(settingsCard, applyAll, 12);
        applyAll.setOnClickListener(v -> applyDefaultsToAll());

        LinearLayout addCard = card(root, 14);
        addCard.addView(sectionTitle("04", "AÑADIR EVENTO"));
        topMargin(addCard, label("TIPO DE EVENTO", 11, MUTED, true), 14);
        typeChooser = column();
        topMargin(addCard, typeChooser, 8);
        renderTypeChooser();
        LinearLayout timeFields = row();
        topMargin(addCard, timeFields, 14);
        LinearLayout startField = column();
        timeFields.addView(startField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        timestampLabel = label("TIEMPO DEL MOMENTO", 11, MUTED, true);
        startField.addView(timestampLabel);
        timestampInput = new EditText(this);
        timestampInput.setSingleLine(true);
        timestampInput.setTextColor(WHITE);
        timestampInput.setHintTextColor(MUTED);
        timestampInput.setTextSize(22);
        timestampInput.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timestampInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        timestampInput.setHint("0:00");
        timestampInput.setPadding(dp(14), 0, dp(14), 0);
        timestampInput.setBackground(shape(PANEL_LIGHT, BORDER, 12));
        topMargin(startField, timestampInput, 8, dp(52));
        installTimeMask(timestampInput);
        startMarkerButton = button("MARCAR INICIO", false);
        topMargin(startField, startMarkerButton, 8, dp(44));
        startMarkerButton.setOnClickListener(v -> markPreviewTime(timestampInput));
        combatFields = column();
        LinearLayout.LayoutParams combatLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        combatLp.leftMargin = dp(8);
        timeFields.addView(combatFields, combatLp);
        combatFields.addView(label("FIN DEL COMBATE", 11, MUTED, true));
        endTimestampInput = dialogInput("");
        endTimestampInput.setHint("0:00");
        endTimestampInput.setHintTextColor(MUTED);
        endTimestampInput.setTextSize(22);
        endTimestampInput.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        endTimestampInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        topMargin(combatFields, endTimestampInput, 8, dp(52));
        installTimeMask(endTimestampInput);
        Button markEnd = button("MARCAR FIN", false);
        topMargin(combatFields, markEnd, 8, dp(44));
        markEnd.setOnClickListener(v -> markPreviewTime(endTimestampInput));
        customNameFields = column();
        topMargin(addCard, customNameFields, 12);
        customNameFields.addView(label("NOMBRE DEL EVENTO", 11, MUTED, true));
        customNameInput = dialogInput("");
        customNameInput.setHint("Por ejemplo, ACE");
        customNameInput.setHintTextColor(MUTED);
        customNameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        topMargin(customNameFields, customNameInput, 8, dp(52));
        updateEventForm();
        Button add = button("＋  AÑADIR EVENTO", true);
        topMargin(addCard, add, 14);
        add.setOnClickListener(v -> addEvent());
        topMargin(addCard, label("Escribe solo números: 224 → 2:24  ·  4 → 0:04", 11, MUTED, false), 10);

        LinearLayout eventsCard = card(root, 14);
        LinearLayout eventsHeader = row();
        eventsHeader.setGravity(Gravity.CENTER_VERTICAL);
        eventsHeader.addView(sectionTitle("05", "EVENTOS"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        eventCount = label("0", 13, RED, true);
        eventsHeader.addView(eventCount);
        eventsCard.addView(eventsHeader);
        eventList = column();
        topMargin(eventsCard, eventList, 12);

        LinearLayout outputCard = card(root, 16);
        outputCard.addView(sectionTitle("06", "CREAR MONTAJE"));
        exportSummary = label("Añade eventos para crear tu video.", 12, MUTED, false);
        topMargin(outputCard, exportSummary, 10);
        exportButton = button("▶  LOCAL", true);
        topMargin(outputCard, exportButton, 16, dp(56));
        exportButton.setOnClickListener(v -> startExport());
        if (!getIntent().getBooleanExtra("offline_local", false) &&
                !new SecureAccountStore(this).endpoint().isEmpty()) {
            uploadButton = button("↑  SUBIR A BAT", false);
            topMargin(outputCard, uploadButton, 10, dp(52));
            uploadButton.setOnClickListener(v -> startBatExport());
            topMargin(outputCard, label("El envío requiere aprobación y cuota disponible. Los montajes locales siguen disponibles.",
                    11, MUTED, false), 8);
        }
        statusLabel = label("Listo para comenzar.", 11, MUTED, false);
        statusLabel.setGravity(Gravity.CENTER);
        topMargin(root, statusLabel, 16);
    }

    private void chooseVideo() {
        if (exporting) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_VIDEO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == SAVE_VIDEO && tempOutput != null) {
                status("Montaje exportado; falta elegir dónde guardarlo.");
                new AlertDialog.Builder(this)
                        .setTitle("Guardar montaje")
                        .setMessage("El video está preparado. ¿Quieres elegir otra ubicación para guardarlo?")
                        .setPositiveButton("GUARDAR", (dialog, which) -> chooseOutputLocation())
                        .setNegativeButton("AHORA NO", null)
                        .show();
            }
            return;
        }
        if (requestCode == PICK_VIDEO) loadVideo(data.getData());
        if (requestCode == SAVE_VIDEO) saveOutput(data.getData());
    }

    private void loadVideo(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // The current picker grant is still enough for this session.
        }
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        long newDuration;
        boolean hasAudio = true;
        try {
            metadata.setDataSource(this, uri);
            String value = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            newDuration = value == null ? -1 : Long.parseLong(value);
            String audioValue = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);
            if (audioValue != null) hasAudio = "yes".equalsIgnoreCase(audioValue);
        } catch (Exception exception) {
            newDuration = -1;
        } finally {
            try { metadata.release(); } catch (Exception ignored) {}
        }
        sourceUri = uri;
        durationMs = newDuration;
        sourceHasAudio = hasAudio;
        events.clear();
        timestampInput.setText("");
        endTimestampInput.setText("");
        customNameInput.setText("");
        newEventType = TYPES[0];
        renderTypeChooser();
        updateEventForm();
        fileLabel.setText(displayName(uri));
        updateDuration();
        renderEvents();
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
        updatePreviewControls();
        status("Vídeo cargado. Reproduce, localiza momentos y añádelos.");
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {}
        return "Vídeo seleccionado";
    }

    private void updateDuration() {
        durationLabel.setText(durationMs > 0 ? "Duración: " + MontageLogic.formatTime(durationMs) : "Preparando vídeo…");
    }

    private void installTimeMask(EditText input) {
        input.addTextChangedListener(new TextWatcher() {
            private boolean changing;
            private String enteredDigits = "";
            private String insertedDigits = "";
            private int oldLength;
            private int removedCount;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (changing) return;
                oldLength = s.length();
                removedCount = count;
            }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (changing) return;
                insertedDigits = s.subSequence(start, start + count).toString().replaceAll("\\D", "");
            }
            @Override public void afterTextChanged(Editable editable) {
                if (changing) return;
                if (oldLength > 0 && removedCount == oldLength) {
                    enteredDigits = "";
                } else if (removedCount > 0 && insertedDigits.isEmpty() && !enteredDigits.isEmpty()) {
                    // A backspace over a colon or a padded zero removes one real digit.
                    enteredDigits = enteredDigits.substring(0, enteredDigits.length() - 1);
                }
                enteredDigits += insertedDigits;
                if (enteredDigits.length() > 6) enteredDigits = enteredDigits.substring(0, 6);
                String masked = MontageLogic.maskTimestamp(enteredDigits);
                if (!editable.toString().equals(masked)) {
                    changing = true;
                    input.setText(masked);
                    input.setSelection(masked.length());
                    changing = false;
                }
            }
        });
    }

    private void renderTypeChooser() {
        typeChooser.removeAllViews();
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            LinearLayout typeRow = row();
            if (rowIndex > 0) topMargin(typeChooser, typeRow, 7);
            else typeChooser.addView(typeRow);
            for (int columnIndex = 0; columnIndex < 2; columnIndex++) {
                String type = TYPES[rowIndex * 2 + columnIndex];
                boolean selected = type.equals(newEventType);
                TextView chip = label(type, 12, selected ? WHITE : MUTED, true);
                chip.setGravity(Gravity.CENTER);
                chip.setBackground(shape(selected ? RED : PANEL_LIGHT, selected ? RED : BORDER, 10));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1);
                if (columnIndex > 0) lp.leftMargin = dp(7);
                typeRow.addView(chip, lp);
                chip.setOnClickListener(v -> {
                    newEventType = type;
                    renderTypeChooser();
                    updateEventForm();
                });
            }
        }
    }

    private void updateEventForm() {
        if (timestampLabel == null) return;
        boolean combat = "COMBATE".equals(newEventType);
        timestampLabel.setText(combat ? "INICIO DEL COMBATE" : "TIEMPO DEL MOMENTO");
        startMarkerButton.setVisibility(combat ? View.VISIBLE : View.GONE);
        combatFields.setVisibility(combat ? View.VISIBLE : View.GONE);
        customNameFields.setVisibility("OTRO".equals(newEventType) ? View.VISIBLE : View.GONE);
    }

    private void markPreviewTime(EditText input) {
        if (sourceUri == null || durationMs <= 0) { alert("Selecciona primero un vídeo válido."); return; }
        player.pause();
        input.setText(MontageLogic.formatTime(Math.min(player.getCurrentPosition(), durationMs)));
        status("Tiempo marcado desde la vista previa.");
    }

    private void seekPreviewBy(long offsetMs) {
        if (sourceUri == null || durationMs <= 0) return;
        player.seekTo(Math.max(0, Math.min(durationMs, player.getCurrentPosition() + offsetMs)));
        updatePreviewControls();
    }

    private void updatePreviewControls() {
        if (player == null || previewSeek == null || previewClock == null) return;
        long length = durationMs > 0 ? durationMs : Math.max(0, player.getDuration());
        long position = Math.max(0, player.getCurrentPosition());
        if (!previewScrubbing) {
            previewSeek.setProgress(length > 0 ? (int) Math.min(1000, position * 1000 / length) : 0);
            previewClock.setText(MontageLogic.formatTime(position) + " / " + MontageLogic.formatTime(length));
        }
        previewSeek.setEnabled(length > 0);
        previewPlay.setEnabled(sourceUri != null);
        previewPlay.setText(player.isPlaying() ? "Ⅱ" : "▶");
    }

    private void addEvent() {
        if (sourceUri == null || durationMs <= 0) { alert("Selecciona primero un vídeo válido."); return; }
        long time = MontageLogic.parseTimestamp(timestampInput.getText().toString());
        if (time < 0) { alert("Introduce una hora válida, por ejemplo 2:24 o 1:02:24."); return; }
        if (time > durationMs) { alert("El evento no puede superar la duración del vídeo (" + MontageLogic.formatTime(durationMs) + ")."); return; }
        long end = time;
        if ("COMBATE".equals(newEventType)) {
            end = MontageLogic.parseTimestamp(endTimestampInput.getText().toString());
            if (end <= time || end > durationMs) {
                alert("El fin del combate debe ser posterior al inicio y estar dentro del vídeo.");
                return;
            }
        }
        String customName = "";
        if ("OTRO".equals(newEventType)) {
            customName = customNameInput.getText().toString().trim();
            if (!validCustomName(customName)) { alert("Escribe un nombre válido para el evento OTRO."); return; }
        }
        long before = parseSeconds(beforeInput, "segundos antes");
        long after = parseSeconds(afterInput, "segundos después");
        if (before < 0 || after < 0) return;
        if (before + after == 0 && !"COMBATE".equals(newEventType)) {
            alert("El clip necesita al menos un segundo antes o después."); return;
        }
        events.add(new MontageLogic.Event(time, end, newEventType, customName, before, after));
        events.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
        timestampInput.setText("");
        endTimestampInput.setText("");
        customNameInput.setText("");
        timestampInput.clearFocus();
        renderEvents();
        player.pause();
        player.seekTo(time);
        status("Evento añadido en " + MontageLogic.formatTime(time) + ".");
    }

    private boolean validCustomName(String value) {
        return !value.isEmpty() && value.length() <= 40 && !value.matches("(?s).*\\p{Cntrl}.*");
    }

    private long parseSeconds(EditText input, String name) {
        try {
            int seconds = Integer.parseInt(input.getText().toString().trim());
            if (seconds < 0 || seconds > 600) throw new NumberFormatException();
            return seconds * 1000L;
        } catch (NumberFormatException exception) {
            alert("Introduce un valor entre 0 y 600 para " + name + ".");
            return -1;
        }
    }

    private void applyDefaultsToAll() {
        long before = parseSeconds(beforeInput, "segundos antes");
        long after = parseSeconds(afterInput, "segundos después");
        if (before < 0 || after < 0) return;
        if (before + after == 0 && events.stream().anyMatch(event -> !"COMBATE".equals(event.type))) {
            alert("Los eventos puntuales necesitan al menos un segundo antes o después."); return;
        }
        for (MontageLogic.Event event : events) { event.beforeMs = before; event.afterMs = after; }
        renderEvents();
        status("Ajustes aplicados a " + events.size() + " eventos.");
    }

    private void renderEvents() {
        eventList.removeAllViews();
        eventCount.setText(events.size() + (events.size() == 1 ? " EVENTO" : " EVENTOS"));
        if (events.isEmpty()) {
            TextView empty = label("Todavía no hay eventos. Añade el primer momento arriba.", 13, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(24), dp(12), dp(24));
            empty.setBackground(shape(PANEL_LIGHT, BORDER, 12));
            eventList.addView(empty);
        }
        for (MontageLogic.Event event : events) {
            LinearLayout item = column();
            item.setPadding(dp(13), dp(12), dp(13), dp(10));
            item.setBackground(shape(PANEL_LIGHT, BORDER, 12));
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            itemLp.bottomMargin = dp(8);
            eventList.addView(item, itemLp);
            LinearLayout top = row();
            top.setGravity(Gravity.CENTER_VERTICAL);
            item.addView(top);
            String timeText = MontageLogic.formatTime(event.timeMs) +
                    ("COMBATE".equals(event.type) ? " → " + MontageLogic.formatTime(event.endMs) : "");
            TextView time = label(timeText, "COMBATE".equals(event.type) ? 17 : 20, WHITE, true);
            time.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            top.addView(time, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView kind = label(event.displayName(), 11, RED, true);
            kind.setPadding(dp(10), dp(6), dp(10), dp(6));
            kind.setBackground(shape(PANEL, RED, 8));
            top.addView(kind);
            TextView trim = label("ANTES " + secondsText(event.beforeMs) + "s    ·    DESPUÉS " + secondsText(event.afterMs) + "s", 11, MUTED, true);
            topMargin(item, trim, 9);
            LinearLayout actions = row();
            topMargin(item, actions, 11);
            TextView preview = action("VER");
            actions.addView(preview);
            preview.setOnClickListener(v -> {
                player.pause();
                player.seekTo(event.timeMs);
                pageScroll.post(() -> pageScroll.smoothScrollTo(0, previewSection.getTop()));
                status("Vista previa en " + MontageLogic.formatTime(event.timeMs) + ". Pulsa Play para reproducir.");
            });
            TextView edit = action("EDITAR");
            LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            editLp.leftMargin = dp(18);
            actions.addView(edit, editLp);
            edit.setOnClickListener(v -> editEvent(event));
            TextView delete = action("ELIMINAR");
            delete.setTextColor(RED);
            LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            deleteLp.leftMargin = dp(18);
            actions.addView(delete, deleteLp);
            delete.setOnClickListener(v -> {
                events.remove(event);
                renderEvents();
                status("Evento eliminado.");
            });
        }
        if (exportSummary != null) {
            List<MontageLogic.Range> ranges = MontageLogic.mergedRanges(events, Math.max(0, durationMs));
            long total = 0;
            for (MontageLogic.Range range : ranges) total += range.endMs - range.startMs;
            exportSummary.setText(events.isEmpty() ? "Añade eventos para crear tu video." :
                    events.size() + (events.size() == 1 ? " evento  ·  " : " eventos  ·  ")
                            + ranges.size() + (ranges.size() == 1 ? " clip  ·  " : " clips fusionados  ·  ")
                            + "aprox. " + MontageLogic.formatTime(total));
            updateActionButtons();
        }
    }

    private boolean hasValidMontage() {
        return sourceUri != null && durationMs > 0 && !events.isEmpty() &&
                !MontageLogic.mergedRanges(events, durationMs).isEmpty();
    }

    private void updateActionButtons() {
        boolean ready = hasValidMontage() && !exporting && !uploadValidating;
        if (exportButton != null) {
            exportButton.setEnabled(ready);
            exportButton.setAlpha(ready ? 1f : 0.45f);
        }
        if (uploadButton != null) {
            uploadButton.setEnabled(ready);
            uploadButton.setAlpha(ready ? 1f : 0.45f);
            if (!uploadValidating) uploadButton.setText("↑  SUBIR A BAT");
        }
    }

    private void editEvent(MontageLogic.Event event) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = column();
        body.setPadding(dp(22), dp(10), dp(22), dp(6));
        scroll.addView(body);
        TextView timeLabel = label("TIEMPO", 11, MUTED, true);
        body.addView(timeLabel);
        EditText timeInput = dialogInput(MontageLogic.formatTime(event.timeMs));
        topMargin(body, timeInput, 6, dp(48));
        body.addView(label("TIPO", 11, MUTED, true));
        RadioGroup types = new RadioGroup(this);
        for (int i = 0; i < TYPES.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setId(100 + i);
            option.setText(TYPES[i]);
            option.setTextColor(WHITE);
            option.setButtonTintList(android.content.res.ColorStateList.valueOf(RED));
            types.addView(option);
            if (TYPES[i].equals(event.type)) types.check(option.getId());
        }
        body.addView(types);
        LinearLayout endBlock = column();
        topMargin(body, endBlock, 8);
        endBlock.addView(label("FIN DEL COMBATE", 11, MUTED, true));
        EditText endInput = dialogInput(MontageLogic.formatTime(event.endMs));
        topMargin(endBlock, endInput, 6, dp(48));
        LinearLayout customBlock = column();
        topMargin(body, customBlock, 8);
        customBlock.addView(label("NOMBRE DEL EVENTO", 11, MUTED, true));
        EditText customInput = dialogInput(event.customName == null ? "" : event.customName);
        customInput.setHint("Por ejemplo, ACE");
        topMargin(customBlock, customInput, 6, dp(48));
        types.setOnCheckedChangeListener((group, checkedId) -> {
            String selected = TYPES[Math.max(0, Math.min(TYPES.length - 1, checkedId - 100))];
            boolean combat = "COMBATE".equals(selected);
            timeLabel.setText(combat ? "INICIO DEL COMBATE" : "TIEMPO");
            endBlock.setVisibility(combat ? View.VISIBLE : View.GONE);
            customBlock.setVisibility("OTRO".equals(selected) ? View.VISIBLE : View.GONE);
        });
        int initiallyChecked = types.getCheckedRadioButtonId();
        types.clearCheck();
        types.check(initiallyChecked == -1 ? 100 : initiallyChecked);
        LinearLayout durations = row();
        topMargin(body, durations, 8);
        LinearLayout left = inputBlock("ANTES (SEG)", secondsText(event.beforeMs));
        EditText before = (EditText) left.getChildAt(1);
        durations.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout right = inputBlock("DESPUÉS (SEG)", secondsText(event.afterMs));
        EditText after = (EditText) right.getChildAt(1);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        rightLp.leftMargin = dp(12);
        durations.addView(right, rightLp);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Editar evento")
                .setView(scroll)
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("GUARDAR", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long time = MontageLogic.parseTimestamp(timeInput.getText().toString());
            if (time < 0 || time > durationMs) { alert("La hora debe ser válida y estar dentro del vídeo."); return; }
            int checked = types.getCheckedRadioButtonId() - 100;
            String selected = checked >= 0 && checked < TYPES.length ? TYPES[checked] : TYPES[0];
            long end = time;
            if ("COMBATE".equals(selected)) {
                end = MontageLogic.parseTimestamp(endInput.getText().toString());
                if (end <= time || end > durationMs) {
                    alert("El fin del combate debe ser posterior al inicio y estar dentro del vídeo."); return;
                }
            }
            String custom = "OTRO".equals(selected) ? customInput.getText().toString().trim() : "";
            if ("OTRO".equals(selected) && !validCustomName(custom)) {
                alert("Escribe un nombre válido para el evento OTRO."); return;
            }
            long beforeMs = parseSeconds(before, "segundos antes");
            long afterMs = parseSeconds(after, "segundos después");
            if (beforeMs < 0 || afterMs < 0) return;
            if (beforeMs + afterMs == 0 && !"COMBATE".equals(selected)) {
                alert("El clip necesita al menos un segundo antes o después."); return;
            }
            event.timeMs = time;
            event.endMs = end;
            event.type = selected;
            event.customName = custom;
            event.beforeMs = beforeMs;
            event.afterMs = afterMs;
            events.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
            renderEvents();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void startExport() { startExport(false); }

    private void startBatExport() {
        if (exporting || uploadValidating) return;
        if (!hasValidMontage()) { updateActionButtons(); return; }
        if (getIntent().getBooleanExtra("offline_local", false)) {
            alert("Estás en modo local sin conexión. Vuelve a abrir la cuenta cuando tengas Internet.");
            return;
        }
        uploadValidating = true;
        uploadButton.setText("SUBIENDO…");
        updateActionButtons();
        status("Preparando envío a BAT…");
        new Thread(() -> {
            JSONObject result;
            try {
                SecureAccountStore store = new SecureAccountStore(this);
                JSONObject account = store.load();
                result = new BackendClient(store.endpoint()).post(ResumableUploader.withCredentials(account, "validate"));
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    uploadValidating = false;
                    updateActionButtons();
                    alert("No se pudo verificar el acceso a BAT. Revisa Internet y vuelve a intentar.");
                });
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                uploadValidating = false;
                updateActionButtons();
                if (!result.optBoolean("ok") || !result.optBoolean("authorized")) {
                    alert("Tu cuenta no puede enviar videos a BAT en este momento. Consulta Cuenta BAT para ver su estado.");
                } else if (result.optInt("uploads_used_today") >= result.optInt("daily_upload_limit")) {
                    alert("Has alcanzado tu límite de envíos de hoy. Puedes seguir creando montajes locales.");
                } else {
                    startExport(true);
                }
            });
        }).start();
    }

    private void startExport(boolean toBat) {
        if (exporting) return;
        if (sourceUri == null || durationMs <= 0 || events.isEmpty()) { alert("Selecciona un vídeo y añade al menos un evento."); return; }
        List<MontageLogic.Range> ranges = MontageLogic.mergedRanges(events, durationMs);
        if (ranges.isEmpty()) { alert("No hay segmentos válidos para exportar."); return; }
        try {
            uploadAfterExport = toBat;
            File folder = new File(getCacheDir(), "montages");
            if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("No se pudo crear la carpeta temporal.");
            tempOutput = new File(folder, "BAT_Montaje_" + System.currentTimeMillis() + ".mp4");
            List<EditedMediaItem> clips = new ArrayList<>();
            for (MontageLogic.Range range : ranges) {
                MediaItem mediaItem = new MediaItem.Builder()
                        .setUri(sourceUri)
                        .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(range.startMs)
                                .setEndPositionMs(range.endMs)
                                .build())
                        .build();
                clips.add(new EditedMediaItem.Builder(mediaItem).build());
            }
            EditedMediaItemSequence sequence = sourceHasAudio
                    ? EditedMediaItemSequence.withAudioAndVideoFrom(clips)
                    : EditedMediaItemSequence.withVideoFrom(clips);
            // Punto de integración futuro: el preset oficial obligatorio se aplicará aquí
            // solo cuando BAT defina intro, outro, música y volúmenes. Hoy ambos flujos
            // usan exactamente el render local existente, sin inventar branding.
            Composition composition = new Composition.Builder(sequence).build();
            transformer = new Transformer.Builder(this)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(new Transformer.Listener() {
                        @Override public void onCompleted(Composition composition, ExportResult result) {
                            exporting = false;
                            updateActionButtons();
                            stopProgress();
                            if (tempOutput == null || !tempOutput.isFile() || tempOutput.length() == 0) {
                                alert("La exportación terminó sin crear un video válido.");
                                return;
                            }
                            if (uploadAfterExport) {
                                status("Montaje creado. Preparando envío a BAT…");
                                Intent upload = new Intent(MainActivity.this, UploadActivity.class);
                                upload.putExtra("file_path", tempOutput.getAbsolutePath());
                                startActivity(upload);
                            } else {
                                status("Montaje creado. Elige dónde guardarlo.");
                                chooseOutputLocation();
                            }
                        }
                        @Override public void onError(Composition composition, ExportResult result, ExportException exception) {
                            exporting = false;
                            updateActionButtons();
                            stopProgress();
                            status("No se pudo crear el montaje.");
                            Log.e("BATMontaje", "Exportar montaje", exception);
                            alert("No se pudo crear el montaje. Inténtalo nuevamente.");
                        }
                    }).build();
            exporting = true;
            updateActionButtons();
            player.pause();
            showProgress();
            transformer.start(composition, tempOutput.getAbsolutePath());
            pollProgress();
        } catch (Exception exception) {
            exporting = false;
            updateActionButtons();
            stopProgress();
            Log.e("BATMontaje", "Iniciar exportación", exception);
            alert("No se pudo iniciar la creación del montaje.");
        }
    }

    private void showProgress() {
        LinearLayout body = column();
        body.setPadding(dp(24), dp(10), dp(24), dp(8));
        progressLabel = label("Preparando clips…", 13, WHITE, false);
        body.addView(progressLabel);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(RED));
        topMargin(body, progressBar, 16, dp(8));
        progressDialog = new AlertDialog.Builder(this)
                .setTitle(uploadAfterExport ? "Creando montaje para BAT" : "Creando montaje")
                .setView(body)
                .setNegativeButton("CANCELAR", (dialog, which) -> {
                    if (transformer != null) transformer.cancel();
                    exporting = false;
                    updateActionButtons();
                    status("Exportación cancelada.");
                })
                .create();
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    private void chooseOutputLocation() {
        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType("video/mp4");
        save.putExtra(Intent.EXTRA_TITLE, "BAT_Montaje.mp4");
        save.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(save, SAVE_VIDEO);
    }

    private void pollProgress() {
        progressTick = new Runnable() {
            @Override public void run() {
                if (!exporting || transformer == null) return;
                ProgressHolder holder = new ProgressHolder();
                int state = transformer.getProgress(holder);
                if (state == Transformer.PROGRESS_STATE_AVAILABLE && progressBar != null) {
                    progressBar.setProgress(holder.progress);
                    progressLabel.setText("Procesando vídeo y audio… " + holder.progress + "%");
                }
                handler.postDelayed(this, 500);
            }
        };
        handler.post(progressTick);
    }

    private void stopProgress() {
        if (progressTick != null) handler.removeCallbacks(progressTick);
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
    }

    private void saveOutput(Uri destination) {
        File source = tempOutput;
        if (source == null || !source.isFile()) { alert("No se encontró el montaje exportado."); return; }
        status("Guardando video…");
        new Thread(() -> {
            Exception failure = null;
            try (InputStream input = new FileInputStream(source);
                 OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                if (output == null) throw new IllegalStateException("No se pudo abrir el destino.");
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                output.flush();
            } catch (Exception exception) { failure = exception; }
            Exception finalFailure = failure;
            runOnUiThread(() -> {
                if (finalFailure != null) { alert("No se pudo guardar el video. Inténtalo nuevamente."); return; }
                try {
                    getContentResolver().takePersistableUriPermission(destination,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (SecurityException exception) {
                    try {
                        getContentResolver().takePersistableUriPermission(destination, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException ignored) { }
                }
                try {
                    new LocalHistoryStore(this).add(destination, displayName(destination), source.length());
                } catch (Exception exception) {
                    status("Video guardado; no se pudo actualizar Mis montajes.");
                }
                source.delete(); // Only the temporary copy; the chosen output is preserved.
                if (tempOutput == source) tempOutput = null;
                status("Montaje guardado correctamente.");
                new AlertDialog.Builder(this)
                        .setTitle("Montaje listo")
                        .setMessage("El video se guardó en la ubicación elegida.")
                        .setPositiveButton("ABRIR", (dialog, which) -> {
                            Intent open = new Intent(Intent.ACTION_VIEW);
                            open.setDataAndType(destination, "video/mp4");
                            open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            try { startActivity(open); } catch (Exception exception) { Toast.makeText(this, "No hay un reproductor disponible.", Toast.LENGTH_LONG).show(); }
                        })
                        .setNegativeButton("CERRAR", null)
                        .show();
            });
        }).start();
    }

    private void status(String text) { if (statusLabel != null) statusLabel.setText(text); }
    private void alert(String text) { new AlertDialog.Builder(this).setTitle("BAT Montaje").setMessage(text).setPositiveButton("ENTENDIDO", null).show(); }
    private String secondsText(long millis) { return Long.toString(Math.round(millis / 1000.0)); }

    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); return layout; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != fill) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView sectionTitle(String number, String title) {
        TextView view = label(number + "  /  " + title, 14, WHITE, true);
        view.setLetterSpacing(0.04f);
        return view;
    }

    private LinearLayout card(LinearLayout parent, int top) {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(shape(PANEL, BORDER, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(top);
        parent.addView(card, lp);
        return card;
    }

    private Button button(String title, boolean prominent) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTextColor(WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(shape(prominent ? RED : PANEL_LIGHT, prominent ? RED : BORDER, 12));
        button.setPadding(dp(12), 0, dp(12), 0);
        return button;
    }

    private LinearLayout inputBlock(String caption, String initial) {
        LinearLayout block = column();
        block.addView(label(caption, 10, MUTED, true));
        EditText input = dialogInput(initial);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        topMargin(block, input, 6, dp(46));
        return block;
    }

    private EditText dialogInput(String initial) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(initial);
        input.setTextColor(WHITE);
        input.setTextSize(15);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(shape(PANEL_LIGHT, BORDER, 10));
        return input;
    }

    private TextView action(String text) {
        TextView view = label(text, 11, WHITE, true);
        view.setPadding(dp(2), dp(6), dp(2), dp(6));
        return view;
    }

    private void topMargin(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(top);
        parent.addView(child, lp);
    }

    private void topMargin(LinearLayout parent, View child, int top, int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        lp.topMargin = dp(top);
        parent.addView(child, lp);
    }

    @Override protected void onDestroy() {
        stopProgress();
        handler.removeCallbacks(previewTick);
        if (transformer != null && exporting) transformer.cancel();
        if (player != null) player.release();
        super.onDestroy();
    }

    @Override protected void onStop() {
        if (player != null) player.pause();
        super.onStop();
    }
}
