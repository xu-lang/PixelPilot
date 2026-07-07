package com.openipc.pixelpilot;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hierynomus.sshj.transport.cipher.BlockCiphers;
import com.hierynomus.sshj.transport.kex.DHGroups;
import com.hierynomus.sshj.transport.mac.Macs;

import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.DefaultSecurityProviderConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OpenIpcConfigActivity extends AppCompatActivity {
    private static final String PREFS = "openipc_config";
    private static final String REMOTE_MAJESTIC = "/etc/majestic.yaml";
    private static final String REMOTE_WFB_YAML = "/etc/wfb.yaml";
    private static final String REMOTE_WFB_CONF = "/etc/wfb.conf";
    private static final String REMOTE_TELEMETRY = "/etc/telemetry.conf";
    private static final String MAJESTIC_RESTART = "killall -1 majestic";
    private static final String WFB_RESTART = "wifibroadcast stop; sleep 2; wifibroadcast start";
    private static final String TELEMETRY_RESTART = "telemetry stop; sleep 2; telemetry start";
    private static final String DATALINK_RESTART = "/etc/init.d/S98datalink stop ;/etc/init.d/S98datalink start";
    private static final String ALINK_STATUS = "grep -q \"alink_drone\" /etc/rc.local && echo \"true\" || echo \"false\"";
    private static final String ALINK_ENABLE = "grep -q \"alink_drone\" /etc/rc.local || sed -i '/^exit 0/i # Start alink drone service\\n/usr/bin/alink_drone \\&\\n' /etc/rc.local";
    private static final String ALINK_DISABLE = "sed -i '/# Start alink drone service/d; /alink_drone/d; /^$/d' /etc/rc.local";
    private static final int NAV_TEXT_SELECTED = Color.WHITE;
    private static final int NAV_TEXT_IDLE = Color.rgb(154, 170, 182);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText editHost;
    private EditText editPort;
    private EditText editUsername;
    private EditText editPassword;
    private Spinner spinnerResolution;
    private Spinner spinnerFps;
    private Spinner spinnerCodec;
    private Spinner spinnerBitrate;
    private Spinner spinnerExposure;
    private Spinner spinnerContrast;
    private Spinner spinnerHue;
    private Spinner spinnerSaturation;
    private Spinner spinnerLuminance;
    private EditText editFpvNoiseLevel;
    private EditText editFpvRoiQp;
    private EditText editFpvRefEnhance;
    private EditText editFpvIntraLine;
    private EditText editFpvRoiRect;
    private Spinner spinnerWfbChannel;
    private Spinner spinnerWfbBandwidth;
    private Spinner spinnerWfbMcs;
    private Spinner spinnerWfbFecK;
    private Spinner spinnerWfbFecN;
    private Spinner spinnerWfbMlink;
    private Spinner spinnerTelemetrySerial;
    private Spinner spinnerTelemetryBaud;
    private Spinner spinnerTelemetryRouter;
    private Spinner spinnerTelemetryMspFps;
    private Spinner spinnerTelemetryMcs;
    private Spinner spinnerTelemetryAggregate;
    private Spinner spinnerTelemetryRcChannel;
    private Spinner spinnerSensorBin;
    private SeekBar seekWfbTxPower;
    private EditText editWfbConfig;
    private EditText editTelemetryConfig;
    private EditText editSensorConfigPath;
    private CheckBox checkFlip;
    private CheckBox checkMirror;
    private CheckBox checkAudio;
    private CheckBox checkRecord;
    private CheckBox checkRecordNoTime;
    private CheckBox checkFpvEnabled;
    private CheckBox checkFpvRefPred;
    private CheckBox checkFpvIntraQp;
    private CheckBox checkWfbStbc;
    private CheckBox checkWfbLdpc;
    private TextView textStatus;
    private TextView textDeviceInfo;
    private TextView textMajesticPreview;
    private TextView textWfbTxPower;
    private TextView textSetupInfo;
    private TextView textAdvancedOutput;
    private TextView textFirmwareOutput;
    private TextView textPreferencesInfo;
    private Button btnSaveMajestic;
    private Button[] navButtons;
    private View[] tabSections;

    private Map<String, Object> majesticConfig;
    private Map<String, Object> wfbYamlConfig;
    private String activeWfbPath = REMOTE_WFB_YAML;
    private boolean telemetryUsesWfbYaml = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_openipc_config);

        bindViews();
        setupChoiceControls();
        setupEditTextBehavior(findViewById(android.R.id.content));
        applyOpenIpcTextColors(findViewById(android.R.id.content));
        loadConnectionSettings();
        setupTabs();

        findViewById(R.id.btnConnect).setOnClickListener(v -> connectAndRead());
        btnSaveMajestic.setOnClickListener(v -> saveAndRestartMajestic());
        findViewById(R.id.btnReadWfb).setOnClickListener(v -> readWfbConfig());
        findViewById(R.id.btnSaveWfb).setOnClickListener(v -> saveWfbConfig());
        findViewById(R.id.btnReadTelemetry).setOnClickListener(v -> readTelemetryConfig());
        findViewById(R.id.btnSaveTelemetry).setOnClickListener(v -> saveTelemetryConfig());
        findViewById(R.id.btnEnableUart0).setOnClickListener(v -> runCommandWithToast("sed -i 's/console::respawn:\\/sbin\\/getty -L console 0 vt100/#console::respawn:\\/sbin\\/getty -L console 0 vt100/' /etc/inittab", "UART0 enabled"));
        findViewById(R.id.btnDisableUart0).setOnClickListener(v -> runCommandWithToast("sed -i 's/#console::respawn:\\/sbin\\/getty -L console 0 vt100/console::respawn:\\/sbin\\/getty -L console 0 vt100/' /etc/inittab", "UART0 disabled"));
        findViewById(R.id.btnEnable40Mhz).setOnClickListener(v -> uploadAssetAndRun("binaries/clean/wifibroadcast", "/usr/bin/wifibroadcast", "dos2unix /usr/bin/wifibroadcast 2>/dev/null || true; chmod +x /usr/bin/wifibroadcast", "40MHz enabled"));
        findViewById(R.id.btnAddMavlink).setOnClickListener(v -> runCommandWithToast("grep -q mavlink /etc/rc.local || sed -i '/^exit 0/i mavlink-routerd \\&' /etc/rc.local", "MAVLink added"));
        findViewById(R.id.btnMsposdCameraExtra).setOnClickListener(v -> uploadAssetAndRun("binaries/clean/telemetry_msposd_extra", "/usr/bin/telemetry", "chmod +x /usr/bin/telemetry; " + DATALINK_RESTART, "MSPOSD Camera Extra enabled"));
        findViewById(R.id.btnMsposdGsExtra).setOnClickListener(v -> uploadAssetAndRun("binaries/clean/telemetry_msposd_gs", "/usr/bin/telemetry", "chmod +x /usr/bin/telemetry; " + DATALINK_RESTART, "MSPOSD GS Extra enabled"));
        findViewById(R.id.btnRemoveMsposdExtra).setOnClickListener(v -> runCommandWithToast("sed -i 's/sleep 5/#sleep 5/' /usr/bin/telemetry; " + DATALINK_RESTART, "MSPOSD extra removed"));
        findViewById(R.id.btnApplySensorPath).setOnClickListener(v -> applySensorPath());
        findViewById(R.id.btnUploadApplySensorBin).setOnClickListener(v -> uploadAndApplySelectedSensorBin());
        findViewById(R.id.btnGetDroneKeyChecksum).setOnClickListener(v -> getDroneKeyChecksum());
        findViewById(R.id.btnDownloadDroneKey).setOnClickListener(v -> downloadDroneKey());
        findViewById(R.id.btnCheckAlink).setOnClickListener(v -> runCommandWithToast(ALINK_STATUS, "alink_drone"));
        findViewById(R.id.btnEnableAlink).setOnClickListener(v -> runCommandWithToast(ALINK_ENABLE, "alink_drone enabled"));
        findViewById(R.id.btnDisableAlink).setOnClickListener(v -> runCommandWithToast(ALINK_DISABLE, "alink_drone disabled"));
        findViewById(R.id.btnSystemReport).setOnClickListener(v -> runCommandToOutput("hostname; uname -a; uptime; df -h; free; ps", textAdvancedOutput, "Generating report..."));
        findViewById(R.id.btnViewSystemLogs).setOnClickListener(v -> runCommandToOutput("logread | tail -n 120", textAdvancedOutput, "Reading logs..."));
        findViewById(R.id.btnNetworkDiagnostics).setOnClickListener(v -> runCommandToOutput("ip addr; ip route; iw dev 2>/dev/null; lsusb", textAdvancedOutput, "Running diagnostics..."));
        findViewById(R.id.btnFirmwareStatus).setOnClickListener(v -> runCommandToOutput("hostname; fw_printenv soc sensor 2>/dev/null; cat /etc/os-release 2>/dev/null; df -h", textFirmwareOutput, "Reading firmware status..."));
        findViewById(R.id.btnClearOpenIpcSettings).setOnClickListener(v -> clearSavedOpenIpcSettings());
        findViewById(R.id.btnShowLocalLogHint).setOnClickListener(v -> textPreferencesInfo.setText("Android logs are available through logcat. PixelPilot app data is stored under the app sandbox."));
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        View focused = getCurrentFocus();
        if (focused instanceof EditText) {
            clearEditFocus(focused);
            return;
        }
        super.onBackPressed();
    }

    private void bindViews() {
        editHost = findViewById(R.id.editHost);
        editPort = findViewById(R.id.editPort);
        editUsername = findViewById(R.id.editUsername);
        editPassword = findViewById(R.id.editPassword);
        spinnerResolution = findViewById(R.id.spinnerResolution);
        spinnerFps = findViewById(R.id.spinnerFps);
        spinnerCodec = findViewById(R.id.spinnerCodec);
        spinnerBitrate = findViewById(R.id.spinnerBitrate);
        spinnerExposure = findViewById(R.id.spinnerExposure);
        spinnerContrast = findViewById(R.id.spinnerContrast);
        spinnerHue = findViewById(R.id.spinnerHue);
        spinnerSaturation = findViewById(R.id.spinnerSaturation);
        spinnerLuminance = findViewById(R.id.spinnerLuminance);
        editFpvNoiseLevel = findViewById(R.id.editFpvNoiseLevel);
        editFpvRoiQp = findViewById(R.id.editFpvRoiQp);
        editFpvRefEnhance = findViewById(R.id.editFpvRefEnhance);
        editFpvIntraLine = findViewById(R.id.editFpvIntraLine);
        editFpvRoiRect = findViewById(R.id.editFpvRoiRect);
        spinnerWfbChannel = findViewById(R.id.spinnerWfbChannel);
        spinnerWfbBandwidth = findViewById(R.id.spinnerWfbBandwidth);
        spinnerWfbMcs = findViewById(R.id.spinnerWfbMcs);
        spinnerWfbFecK = findViewById(R.id.spinnerWfbFecK);
        spinnerWfbFecN = findViewById(R.id.spinnerWfbFecN);
        spinnerWfbMlink = findViewById(R.id.spinnerWfbMlink);
        spinnerTelemetrySerial = findViewById(R.id.spinnerTelemetrySerial);
        spinnerTelemetryBaud = findViewById(R.id.spinnerTelemetryBaud);
        spinnerTelemetryRouter = findViewById(R.id.spinnerTelemetryRouter);
        spinnerTelemetryMspFps = findViewById(R.id.spinnerTelemetryMspFps);
        spinnerTelemetryMcs = findViewById(R.id.spinnerTelemetryMcs);
        spinnerTelemetryAggregate = findViewById(R.id.spinnerTelemetryAggregate);
        spinnerTelemetryRcChannel = findViewById(R.id.spinnerTelemetryRcChannel);
        spinnerSensorBin = findViewById(R.id.spinnerSensorBin);
        seekWfbTxPower = findViewById(R.id.seekWfbTxPower);
        editWfbConfig = findViewById(R.id.editWfbConfig);
        editTelemetryConfig = findViewById(R.id.editTelemetryConfig);
        editSensorConfigPath = findViewById(R.id.editSensorConfigPath);
        checkFlip = findViewById(R.id.checkFlip);
        checkMirror = findViewById(R.id.checkMirror);
        checkAudio = findViewById(R.id.checkAudio);
        checkRecord = findViewById(R.id.checkRecord);
        checkRecordNoTime = findViewById(R.id.checkRecordNoTime);
        checkFpvEnabled = findViewById(R.id.checkFpvEnabled);
        checkFpvRefPred = findViewById(R.id.checkFpvRefPred);
        checkFpvIntraQp = findViewById(R.id.checkFpvIntraQp);
        checkWfbStbc = findViewById(R.id.checkWfbStbc);
        checkWfbLdpc = findViewById(R.id.checkWfbLdpc);
        textStatus = findViewById(R.id.textStatus);
        textDeviceInfo = findViewById(R.id.textDeviceInfo);
        textMajesticPreview = findViewById(R.id.textMajesticPreview);
        textWfbTxPower = findViewById(R.id.textWfbTxPower);
        textSetupInfo = findViewById(R.id.textSetupInfo);
        textAdvancedOutput = findViewById(R.id.textAdvancedOutput);
        textFirmwareOutput = findViewById(R.id.textFirmwareOutput);
        textPreferencesInfo = findViewById(R.id.textPreferencesInfo);
        btnSaveMajestic = findViewById(R.id.btnSaveMajestic);
        tabSections = new View[]{
                findViewById(R.id.sectionConnection),
                findViewById(R.id.sectionCamera),
                findViewById(R.id.sectionWfb),
                findViewById(R.id.sectionTelemetry),
                findViewById(R.id.sectionSetup),
                findViewById(R.id.sectionAdvanced),
                findViewById(R.id.sectionFirmware),
                findViewById(R.id.sectionPreferences)
        };
        navButtons = new Button[]{
                findViewById(R.id.navConnect),
                findViewById(R.id.navCamera),
                findViewById(R.id.navWfb),
                findViewById(R.id.navTelemetry),
                findViewById(R.id.navSetup),
                findViewById(R.id.navAdvanced),
                findViewById(R.id.navFirmware),
                findViewById(R.id.navPrefs)
        };
    }

    private void setupChoiceControls() {
        setSpinnerItems(spinnerResolution, "1280x720", "1456x816", "1920x1080", "2104x1184", "2208x1248", "2240x1264", "2312x1304", "2512x1416", "2560x1440", "2560x1920", "3200x1800", "3840x2160");
        setSpinnerItems(spinnerFps, rangeStrings(20, 120, 10));
        setSpinnerItems(spinnerCodec, "h264", "h265");
        setSpinnerItems(spinnerBitrate, multiplesOf1024(1, 40));
        setSpinnerItems(spinnerExposure, "5", "6", "8", "10", "11", "12", "14", "16", "33", "50");
        setSpinnerItems(spinnerContrast, multiplesOf5(1, 100));
        setSpinnerItems(spinnerHue, multiplesOf5(1, 100));
        setSpinnerItems(spinnerSaturation, multiplesOf5(1, 100));
        setSpinnerItems(spinnerLuminance, multiplesOf5(1, 100));

        setSpinnerItems(spinnerWfbChannel,
                "2412 MHz [1]", "2417 MHz [2]", "2422 MHz [3]", "2427 MHz [4]", "2432 MHz [5]", "2437 MHz [6]", "2442 MHz [7]", "2447 MHz [8]", "2452 MHz [9]", "2457 MHz [10]", "2462 MHz [11]", "2467 MHz [12]", "2472 MHz [13]", "2484 MHz [14]",
                "5180 MHz [36]", "5200 MHz [40]", "5220 MHz [44]", "5240 MHz [48]", "5260 MHz [52]", "5280 MHz [56]", "5300 MHz [60]", "5320 MHz [64]", "5500 MHz [100]", "5520 MHz [104]", "5540 MHz [108]", "5560 MHz [112]", "5580 MHz [116]", "5600 MHz [120]", "5620 MHz [124]", "5640 MHz [128]", "5660 MHz [132]", "5680 MHz [136]", "5700 MHz [140]", "5720 MHz [144]", "5745 MHz [149]", "5765 MHz [153]", "5785 MHz [157]", "5805 MHz [161]", "5825 MHz [165]", "5845 MHz [169]", "5865 MHz [173]", "5885 MHz [177]");
        setSpinnerItems(spinnerWfbBandwidth, "20", "40");
        setSpinnerItems(spinnerWfbMcs, rangeStrings(0, 30, 1));
        setSpinnerItems(spinnerWfbFecK, rangeStrings(0, 19, 1));
        setSpinnerItems(spinnerWfbFecN, rangeStrings(0, 19, 1));
        setSpinnerItems(spinnerWfbMlink, rangeStrings(0, 10, 1));
        setSpinnerItems(spinnerTelemetrySerial, "ttyS0", "ttyS1", "ttyS2", "ttyS3");
        setSpinnerItems(spinnerTelemetryBaud, "4800", "9600", "19200", "38400", "57600", "115200");
        setSpinnerItems(spinnerTelemetryRouter, "mavfwd", "mavlink-routed", "msposd");
        setSpinnerItems(spinnerTelemetryMspFps, "20", "30", "60", "90", "100", "120");
        setSpinnerItems(spinnerTelemetryMcs, rangeStrings(0, 9, 1));
        setSpinnerItems(spinnerTelemetryAggregate, "0", "1", "2", "4", "6", "8", "10", "12", "14", "15");
        setSpinnerItems(spinnerTelemetryRcChannel, rangeStrings(0, 8, 1));
        loadSensorAssetChoices();
        seekWfbTxPower.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textWfbTxPower.setText("TX Power: " + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void setupEditTextBehavior(View root) {
        if (root instanceof EditText) {
            EditText editText = (EditText) root;
            boolean isMultiLine = (editText.getInputType() & android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
            if (!isMultiLine) {
                editText.setSingleLine(true);
                editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
                editText.setOnEditorActionListener((v, actionId, event) -> {
                    boolean isDone = actionId == EditorInfo.IME_ACTION_DONE;
                    boolean isEnter = event != null
                            && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_UP;
                    if (isDone || isEnter) {
                        clearEditFocus(v);
                        return true;
                    }
                    return false;
                });
            }
            return;
        }

        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                setupEditTextBehavior(group.getChildAt(i));
            }
        }
    }

    private void clearEditFocus(View view) {
        view.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void setSpinnerItems(Spinner spinner, String... values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.openipc_spinner_item, values);
        adapter.setDropDownViewResource(R.layout.openipc_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        setupSpinnerScrollBehavior(spinner);
    }

    private void applyOpenIpcTextColors(View root) {
        if (root instanceof TextView && !(root instanceof Button) && !(root instanceof EditText)) {
            ((TextView) root).setTextColor(getColor(R.color.openipc_text_muted));
        }
        if (root instanceof EditText) {
            ((EditText) root).setTextColor(getColor(R.color.openipc_text));
            ((EditText) root).setHintTextColor(getColor(R.color.openipc_text_muted));
            root.setBackgroundResource(R.drawable.openipc_field);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyOpenIpcTextColors(group.getChildAt(i));
            }
        }
    }

    private void setupSpinnerScrollBehavior(Spinner spinner) {
        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        final float[] downY = new float[1];
        final boolean[] moved = new boolean[1];

        spinner.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY[0] = event.getRawY();
                    moved[0] = false;
                    allowParentsToInterceptTouch(view);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getRawY() - downY[0]) > touchSlop) {
                        moved[0] = true;
                        view.setPressed(false);
                        allowParentsToInterceptTouch(view);
                    }
                    return moved[0];
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (moved[0]) {
                        moved[0] = false;
                        view.setPressed(false);
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        });
    }

    private void allowParentsToInterceptTouch(View view) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(false);
            parent = parent.getParent();
        }
    }

    private void loadSensorAssetChoices() {
        try {
            String[] sensors = getAssets().list("sensors");
            if (sensors == null || sensors.length == 0) {
                setSpinnerItems(spinnerSensorBin, "");
                return;
            }
            Arrays.sort(sensors);
            setSpinnerItems(spinnerSensorBin, sensors);
        } catch (IOException e) {
            setSpinnerItems(spinnerSensorBin, "");
            textStatus.setText("Failed to list bundled sensors: " + e.getMessage());
        }
    }

    private String spinnerValue(Spinner spinner) {
        Object selected = spinner.getSelectedItem();
        String value = selected == null ? "" : String.valueOf(selected);
        return spinner == spinnerWfbChannel ? channelFromFrequencyLabel(value) : value;
    }

    private void selectSpinnerValue(Spinner spinner, String value) {
        if (TextUtils.isEmpty(value) || spinner.getAdapter() == null) {
            return;
        }
        String normalizedValue = spinner == spinnerWfbChannel ? channelFromFrequencyLabel(value) : value;
        for (int i = 0; i < spinner.getAdapter().getCount(); i++) {
            String item = String.valueOf(spinner.getAdapter().getItem(i));
            String normalizedItem = spinner == spinnerWfbChannel ? channelFromFrequencyLabel(item) : item;
            if (normalizedValue.equals(normalizedItem)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private String channelFromFrequencyLabel(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        int openBracket = value.lastIndexOf('[');
        int closeBracket = value.lastIndexOf(']');
        if (openBracket >= 0 && closeBracket > openBracket) {
            return value.substring(openBracket + 1, closeBracket).trim();
        }
        return value.trim();
    }

    private void setWfbTxPower(String value) {
        int power = parseIntOrDefault(value, seekWfbTxPower.getProgress());
        power = Math.max(0, Math.min(power, seekWfbTxPower.getMax()));
        seekWfbTxPower.setProgress(power);
        textWfbTxPower.setText("TX Power: " + power);
    }

    private String[] rangeStrings(int start, int end, int step) {
        int size = ((end - start) / step) + 1;
        String[] values = new String[size];
        int index = 0;
        for (int value = start; value <= end; value += step) {
            values[index++] = String.valueOf(value);
        }
        return values;
    }

    private String[] multiplesOf1024(int start, int end) {
        String[] values = new String[(end - start) + 1];
        for (int i = start; i <= end; i++) {
            values[i - start] = String.valueOf(i * 1024);
        }
        return values;
    }

    private String[] multiplesOf5(int start, int end) {
        String[] values = new String[(end - start) + 1];
        for (int i = start; i <= end; i++) {
            values[i - start] = String.valueOf(i * 5);
        }
        return values;
    }

    private void setupTabs() {
        for (int i = 0; i < navButtons.length; i++) {
            final int index = i;
            navButtons[i].setOnClickListener(v -> showTab(index));
        }
        showTab(0);
    }

    private void showTab(int selectedIndex) {
        for (int i = 0; i < tabSections.length; i++) {
            tabSections[i].setVisibility(i == selectedIndex ? View.VISIBLE : View.GONE);
            navButtons[i].setSelected(i == selectedIndex);
            navButtons[i].setTextColor(i == selectedIndex ? NAV_TEXT_SELECTED : NAV_TEXT_IDLE);
            navButtons[i].setTypeface(Typeface.DEFAULT, i == selectedIndex ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void loadConnectionSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        editHost.setText(prefs.getString("host", "10.5.0.10"));
        editPort.setText(String.valueOf(prefs.getInt("port", 22)));
        editUsername.setText(prefs.getString("username", "root"));
        editPassword.setText(prefs.getString("password", "12345"));
    }

    private void saveConnectionSettings(ConnectionConfig config) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("host", config.host)
                .putInt("port", config.port)
                .putString("username", config.username)
                .putString("password", config.password)
                .apply();
    }

    private void connectAndRead() {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }

        saveConnectionSettings(config);
        setBusy(true, "Connecting...");

        executor.execute(() -> {
            try (SSHClient ssh = openSsh(config)) {
                String hostname = exec(ssh, "hostname -s").trim();
                String sensor = exec(ssh, "fw_printenv sensor | awk -F= '{print $2}'").trim();
                String soc = exec(ssh, "fw_printenv soc | awk -F= '{print $2}'").trim();
                String wfbYaml = exec(ssh, "test -f /etc/wfb.yaml && echo 'true' || echo 'false'").trim();
                activeWfbPath = "true".equalsIgnoreCase(wfbYaml) ? REMOTE_WFB_YAML : REMOTE_WFB_CONF;
                String majestic = downloadText(ssh, REMOTE_MAJESTIC);

                parseMajestic(majestic);

                mainHandler.post(() -> {
                    textDeviceInfo.setText("Hostname: " + hostname
                            + "\nSensor: " + emptyFallback(sensor)
                            + "\nSoC: " + emptyFallback(soc)
                            + "\nWFB config: " + activeWfbPath);
                    textMajesticPreview.setText(majestic);
                    fillMajesticFields();
                    btnSaveMajestic.setEnabled(true);
                    setBusy(false, "Connected. Read " + REMOTE_MAJESTIC);
                });
            } catch (Exception e) {
                postError("Connect/read failed: " + e.getMessage());
            }
        });
    }

    private void readWfbConfig() {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }
        setBusy(true, "Reading WFB config...");
        executor.execute(() -> {
            try {
                String wfbYaml = exec(config, "test -f /etc/wfb.yaml && echo 'true' || echo 'false'").trim();
                activeWfbPath = "true".equalsIgnoreCase(wfbYaml) ? REMOTE_WFB_YAML : REMOTE_WFB_CONF;
                String content = downloadText(config, activeWfbPath);
                parseWfbConfig(content);
                mainHandler.post(() -> {
                    editWfbConfig.setText(content);
                    fillWfbFields();
                    setBusy(false, "Read " + activeWfbPath);
                });
            } catch (Exception e) {
                postError("Read WFB failed: " + e.getMessage());
            }
        });
    }

    private void saveWfbConfig() {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }
        String content = buildWfbContentForSave();
        setBusy(true, "Saving WFB config...");
        executor.execute(() -> {
            try {
                uploadText(config, activeWfbPath, content);
                exec(config, WFB_RESTART);
                mainHandler.post(() -> {
                    editWfbConfig.setText(content);
                    setBusy(false, "Saved and restarted WFB.");
                });
            } catch (Exception e) {
                postError("Save WFB failed: " + e.getMessage());
            }
        });
    }

    private void readTelemetryConfig() {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }
        setBusy(true, "Reading telemetry config...");
        executor.execute(() -> {
            try {
                String wfbYaml = exec(config, "test -f /etc/wfb.yaml && echo 'true' || echo 'false'").trim();
                telemetryUsesWfbYaml = "true".equalsIgnoreCase(wfbYaml);
                String content;
                if (telemetryUsesWfbYaml) {
                    activeWfbPath = REMOTE_WFB_YAML;
                    content = downloadText(config, REMOTE_WFB_YAML);
                    parseWfbConfig(content);
                } else {
                    content = downloadText(config, REMOTE_TELEMETRY);
                }
                mainHandler.post(() -> {
                    editTelemetryConfig.setText(content);
                    fillTelemetryFields(content);
                    setBusy(false, "Read " + (telemetryUsesWfbYaml ? REMOTE_WFB_YAML : REMOTE_TELEMETRY));
                });
            } catch (Exception e) {
                postError("Read telemetry failed: " + e.getMessage());
            }
        });
    }

    private void saveTelemetryConfig() {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }
        String content = buildTelemetryContentForSave();
        setBusy(true, "Saving telemetry config...");
        executor.execute(() -> {
            try {
                if (telemetryUsesWfbYaml) {
                    uploadText(config, REMOTE_WFB_YAML, content);
                    exec(config, "reboot");
                } else {
                    uploadText(config, REMOTE_TELEMETRY, content);
                    exec(config, TELEMETRY_RESTART);
                }
                mainHandler.post(() -> {
                    editTelemetryConfig.setText(content);
                    setBusy(false, telemetryUsesWfbYaml ? "Saved telemetry in wfb.yaml and rebooted." : "Saved and restarted telemetry.");
                });
            } catch (Exception e) {
                postError("Save telemetry failed: " + e.getMessage());
            }
        });
    }

    private void applySensorPath() {
        ConnectionConfig config = readConnectionConfig();
        String sensorPath = editSensorConfigPath.getText().toString().trim();
        if (config == null) {
            return;
        }
        if (TextUtils.isEmpty(sensorPath)) {
            Toast.makeText(this, "Sensor path is required", Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true, "Applying sensor path...");
        executor.execute(() -> {
            try {
                exec(config, "yaml-cli -s .isp.sensorConfig " + shellQuote(sensorPath));
                exec(config, MAJESTIC_RESTART);
                mainHandler.post(() -> setBusy(false, "Applied sensor path and restarted majestic."));
            } catch (Exception e) {
                postError("Apply sensor failed: " + e.getMessage());
            }
        });
    }

    private void uploadAndApplySelectedSensorBin() {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }
        String sensorName = spinnerValue(spinnerSensorBin);
        if (TextUtils.isEmpty(sensorName)) {
            Toast.makeText(this, "No bundled sensor selected", Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true, "Uploading sensor " + sensorName + "...");
        executor.execute(() -> {
            File temp = new File(getCacheDir(), sensorName);
            String remotePath = "/etc/sensors/" + sensorName;
            try {
                copyAssetToFile("sensors/" + sensorName, temp);
                try (SSHClient ssh = openSsh(config)) {
                    ssh.newSCPFileTransfer().upload(temp.getAbsolutePath(), remotePath);
                }
                exec(config, "yaml-cli -s .isp.sensorConfig " + shellQuote(remotePath));
                exec(config, MAJESTIC_RESTART);
                mainHandler.post(() -> {
                    editSensorConfigPath.setText(remotePath);
                    textSetupInfo.setText("Uploaded and applied: " + remotePath);
                    setBusy(false, "Applied sensor and restarted majestic.");
                });
            } catch (Exception e) {
                postError("Upload/apply sensor failed: " + e.getMessage());
            } finally {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        });
    }

    private void runCommandWithToast(String command, String label) {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }
        setBusy(true, label + "...");
        executor.execute(() -> {
            try {
                String output = exec(config, command).trim();
                String message = TextUtils.isEmpty(output) ? label : label + ": " + output;
                mainHandler.post(() -> {
                    setBusy(false, message);
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                postError(label + " failed: " + e.getMessage());
            }
        });
    }

    private void runCommandToOutput(String command, TextView outputView, String busyMessage) {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }
        setBusy(true, busyMessage);
        executor.execute(() -> {
            try {
                String output = exec(config, command).trim();
                mainHandler.post(() -> {
                    outputView.setText(output);
                    setBusy(false, "Done.");
                });
            } catch (Exception e) {
                postError("Command failed: " + e.getMessage());
            }
        });
    }

    private void uploadAssetAndRun(String assetPath, String remotePath, String command, String label) {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }
        setBusy(true, label + "...");
        executor.execute(() -> {
            File temp = new File(getCacheDir(), new File(assetPath).getName());
            try {
                copyAssetToFile(assetPath, temp);
                try (SSHClient ssh = openSsh(config)) {
                    ssh.newSCPFileTransfer().upload(temp.getAbsolutePath(), remotePath);
                    exec(ssh, command);
                }
                mainHandler.post(() -> {
                    setBusy(false, label);
                    Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                postError(label + " failed: " + e.getMessage());
            } finally {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        });
    }

    private void getDroneKeyChecksum() {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }
        setBusy(true, "Downloading drone.key...");
        executor.execute(() -> {
            File temp = new File(getCacheDir(), "drone.key");
            try {
                downloadText(config, "/etc/drone.key", temp);
                String md5 = md5Hex(temp);
                mainHandler.post(() -> {
                    textSetupInfo.setText("/etc/drone.key MD5: " + md5);
                    setBusy(false, "Read drone.key checksum.");
                });
            } catch (Exception e) {
                postError("Read drone.key failed: " + e.getMessage());
            }
        });
    }

    private void downloadDroneKey() {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }
        setBusy(true, "Downloading drone.key...");
        executor.execute(() -> {
            File target = new File(getCacheDir(), "drone.key");
            try {
                downloadText(config, "/etc/drone.key", target);
                mainHandler.post(() -> {
                    textSetupInfo.setText("Downloaded to: " + target.getAbsolutePath());
                    setBusy(false, "Downloaded drone.key.");
                });
            } catch (Exception e) {
                postError("Download drone.key failed: " + e.getMessage());
            }
        });
    }

    private void clearSavedOpenIpcSettings() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        loadConnectionSettings();
        textPreferencesInfo.setText("Saved OpenIPC connection settings cleared.");
    }

    private void saveAndRestartMajestic() {
        ConnectionConfig config = readConnectionConfig();
        if (config == null || majesticConfig == null) {
            return;
        }

        updateMajesticFromFields();
        String yamlText = dumpYaml(majesticConfig);
        setBusy(true, "Uploading majestic.yaml...");

        executor.execute(() -> {
            try {
                uploadText(config, REMOTE_MAJESTIC, yamlText);
                exec(config, MAJESTIC_RESTART);
                mainHandler.post(() -> {
                    textMajesticPreview.setText(yamlText);
                    setBusy(false, "Saved and restarted majestic.");
                    Toast.makeText(this, "Majestic restarted", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                postError("Save failed: " + e.getMessage());
            }
        });
    }

    private ConnectionConfig readConnectionConfig() {
        String host = editHost.getText().toString().trim();
        String portText = editPort.getText().toString().trim();
        String username = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString();

        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(portText)
                || TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Host, port, username and password are required", Toast.LENGTH_SHORT).show();
            return null;
        }

        try {
            return new ConnectionConfig(host, Integer.parseInt(portText), username, password);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid SSH port", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private SSHClient openSsh(ConnectionConfig config) throws IOException {
        DefaultConfig sshConfig = new DefaultSecurityProviderConfig();
        sshConfig.setKeyExchangeFactories(
                DHGroups.Group14SHA1());
        sshConfig.setCipherFactories(
                BlockCiphers.AES128CTR(),
                BlockCiphers.AES256CTR());
        sshConfig.setMACFactories(
                Macs.HMACSHA1());

        SSHClient ssh = new SSHClient(sshConfig);
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(config.host, config.port);
        ssh.authPassword(config.username, config.password);
        return ssh;
    }

    private String exec(ConnectionConfig config, String command) throws IOException {
        try (SSHClient ssh = openSsh(config)) {
            return exec(ssh, command);
        }
    }

    private String exec(SSHClient ssh, String command) throws IOException {
        try (Session session = ssh.startSession()) {
            Session.Command cmd = session.exec(command);
            String output = readStream(cmd.getInputStream());
            String error = readStream(cmd.getErrorStream());
            cmd.join(15, TimeUnit.SECONDS);
            Integer status = cmd.getExitStatus();
            if (status != null && status != 0 && !TextUtils.isEmpty(error)) {
                throw new IOException(error.trim());
            }
            return output;
        }
    }

    private String downloadText(ConnectionConfig config, String remotePath) throws IOException {
        try (SSHClient ssh = openSsh(config)) {
            return downloadText(ssh, remotePath);
        }
    }

    private String downloadText(SSHClient ssh, String remotePath) throws IOException {
        File temp = File.createTempFile("openipc", ".tmp", getCacheDir());
        try {
            ssh.newSCPFileTransfer().download(remotePath, temp.getAbsolutePath());
            return readFile(temp);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private void downloadText(ConnectionConfig config, String remotePath, File target) throws IOException {
        try (SSHClient ssh = openSsh(config)) {
            ssh.newSCPFileTransfer().download(remotePath, target.getAbsolutePath());
        }
    }

    private void uploadText(ConnectionConfig config, String remotePath, String content) throws IOException {
        File temp = File.createTempFile("openipc", ".yaml", getCacheDir());
        try {
            writeFile(temp, content);
            try (SSHClient ssh = openSsh(config)) {
                ssh.newSCPFileTransfer().upload(temp.getAbsolutePath(), remotePath);
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    @SuppressWarnings("unchecked")
    private void parseMajestic(String yamlText) {
        Object loaded = new Yaml().load(yamlText);
        if (loaded instanceof Map) {
            majesticConfig = (Map<String, Object>) loaded;
        } else {
            majesticConfig = new LinkedHashMap<>();
        }
    }

    private String dumpYaml(Map<String, Object> map) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(map);
    }

    private void fillMajesticFields() {
        selectSpinnerValue(spinnerResolution, getStringValue("video0", "size"));
        selectSpinnerValue(spinnerFps, getStringValue("video0", "fps"));
        selectSpinnerValue(spinnerCodec, getStringValue("video0", "codec"));
        selectSpinnerValue(spinnerBitrate, getStringValue("video0", "bitrate"));
        selectSpinnerValue(spinnerExposure, getStringValue("isp", "exposure"));
        editSensorConfigPath.setText(getStringValue("isp", "sensorConfig"));
        selectSpinnerValue(spinnerContrast, getStringValue("image", "contrast"));
        selectSpinnerValue(spinnerHue, getStringValue("image", "hue"));
        selectSpinnerValue(spinnerSaturation, getStringValue("image", "saturation"));
        selectSpinnerValue(spinnerLuminance, getStringValue("image", "luminance"));
        checkFlip.setChecked(getBooleanValue("image", "flip"));
        checkMirror.setChecked(getBooleanValue("image", "mirror"));
        checkAudio.setChecked(getBooleanValue("audio", "enabled"));
        checkRecord.setChecked(getBooleanValue("records", "enabled"));
        checkRecordNoTime.setChecked(getBooleanValue("records", "notime"));
        checkFpvEnabled.setChecked(getBooleanValue("fpv", "enabled"));
        editFpvNoiseLevel.setText(getStringValue("fpv", "noiseLevel"));
        editFpvRoiQp.setText(getStringValue("fpv", "roiQp"));
        editFpvRefEnhance.setText(getStringValue("fpv", "refEnhance"));
        checkFpvRefPred.setChecked(getBooleanValue("fpv", "refPred"));
        editFpvIntraLine.setText(getStringValue("fpv", "intraLine"));
        checkFpvIntraQp.setChecked(getBooleanValue("fpv", "intraQp"));
        editFpvRoiRect.setText(getStringValue("fpv", "roiRect"));
    }

    private void updateMajesticFromFields() {
        putStringValue("video0", "size", spinnerValue(spinnerResolution));
        putNumberOrStringValue("video0", "fps", spinnerValue(spinnerFps));
        putStringValue("video0", "codec", spinnerValue(spinnerCodec));
        putNumberOrStringValue("video0", "bitrate", spinnerValue(spinnerBitrate));
        putNumberOrStringValue("isp", "exposure", spinnerValue(spinnerExposure));
        putStringValue("isp", "sensorConfig", editSensorConfigPath.getText().toString().trim());
        putNumberOrStringValue("image", "contrast", spinnerValue(spinnerContrast));
        putNumberOrStringValue("image", "hue", spinnerValue(spinnerHue));
        putNumberOrStringValue("image", "saturation", spinnerValue(spinnerSaturation));
        putNumberOrStringValue("image", "luminance", spinnerValue(spinnerLuminance));
        putBooleanValue("image", "flip", checkFlip.isChecked());
        putBooleanValue("image", "mirror", checkMirror.isChecked());
        putBooleanValue("audio", "enabled", checkAudio.isChecked());
        putBooleanValue("records", "enabled", checkRecord.isChecked());
        putBooleanValue("records", "notime", checkRecordNoTime.isChecked());
        putBooleanValue("fpv", "enabled", checkFpvEnabled.isChecked());
        putNumberOrStringValue("fpv", "noiseLevel", editFpvNoiseLevel.getText().toString().trim());
        putNumberOrStringValue("fpv", "roiQp", editFpvRoiQp.getText().toString().trim());
        putNumberOrStringValue("fpv", "refEnhance", editFpvRefEnhance.getText().toString().trim());
        putBooleanValue("fpv", "refPred", checkFpvRefPred.isChecked());
        putNumberOrStringValue("fpv", "intraLine", editFpvIntraLine.getText().toString().trim());
        putBooleanValue("fpv", "intraQp", checkFpvIntraQp.isChecked());
        putStringValue("fpv", "roiRect", editFpvRoiRect.getText().toString().trim());
    }

    @SuppressWarnings("unchecked")
    private void parseWfbConfig(String content) {
        if (REMOTE_WFB_YAML.equals(activeWfbPath)) {
            Object loaded = new Yaml().load(content);
            if (loaded instanceof Map) {
                wfbYamlConfig = (Map<String, Object>) loaded;
            } else {
                wfbYamlConfig = new LinkedHashMap<>();
            }
        } else {
            wfbYamlConfig = null;
        }
    }

    private void fillWfbFields() {
        if (REMOTE_WFB_YAML.equals(activeWfbPath) && wfbYamlConfig != null) {
            selectSpinnerValue(spinnerWfbChannel, getNestedString(wfbYamlConfig, "wireless", "channel"));
            setWfbTxPower(getNestedString(wfbYamlConfig, "wireless", "txpower"));
            selectSpinnerValue(spinnerWfbBandwidth, getNestedString(wfbYamlConfig, "wireless", "width"));
            selectSpinnerValue(spinnerWfbMlink, getNestedString(wfbYamlConfig, "wireless", "mlink"));
            selectSpinnerValue(spinnerWfbMcs, getNestedString(wfbYamlConfig, "broadcast", "mcs_index"));
            selectSpinnerValue(spinnerWfbFecK, getNestedString(wfbYamlConfig, "broadcast", "fec_k"));
            selectSpinnerValue(spinnerWfbFecN, getNestedString(wfbYamlConfig, "broadcast", "fec_n"));
            checkWfbStbc.setChecked(getNestedBoolean(wfbYamlConfig, "broadcast", "stbc"));
            checkWfbLdpc.setChecked(getNestedBoolean(wfbYamlConfig, "broadcast", "ldpc"));
            return;
        }

        String content = editWfbConfig.getText().toString();
        selectSpinnerValue(spinnerWfbChannel, parseConfValue(content, "channel"));
        String channel = spinnerValue(spinnerWfbChannel);
        boolean is24GHz = parseIntOrDefault(channel, 0) < 30;
        setWfbTxPower(parseConfValue(content, is24GHz ? "txpower" : "driver_txpower_override"));
        selectSpinnerValue(spinnerWfbBandwidth, parseConfValue(content, "bandwidth"));
        selectSpinnerValue(spinnerWfbMcs, parseConfValue(content, "mcs_index"));
        selectSpinnerValue(spinnerWfbFecK, parseConfValue(content, "fec_k"));
        selectSpinnerValue(spinnerWfbFecN, parseConfValue(content, "fec_n"));
        selectSpinnerValue(spinnerWfbMlink, "0");
        checkWfbStbc.setChecked("1".equals(parseConfValue(content, "stbc")));
        checkWfbLdpc.setChecked("1".equals(parseConfValue(content, "ldpc")));
    }

    private String buildWfbContentForSave() {
        if (REMOTE_WFB_YAML.equals(activeWfbPath)) {
            if (wfbYamlConfig == null) {
                parseWfbConfig(editWfbConfig.getText().toString());
            }
            putNestedNumberOrString(wfbYamlConfig, "wireless", "channel", spinnerValue(spinnerWfbChannel));
            putNestedNumberOrString(wfbYamlConfig, "wireless", "txpower", String.valueOf(seekWfbTxPower.getProgress()));
            putNestedNumberOrString(wfbYamlConfig, "wireless", "width", spinnerValue(spinnerWfbBandwidth));
            putNestedNumberOrString(wfbYamlConfig, "wireless", "mlink", spinnerValue(spinnerWfbMlink));
            putNestedNumberOrString(wfbYamlConfig, "broadcast", "mcs_index", spinnerValue(spinnerWfbMcs));
            putNestedNumberOrString(wfbYamlConfig, "broadcast", "fec_k", spinnerValue(spinnerWfbFecK));
            putNestedNumberOrString(wfbYamlConfig, "broadcast", "fec_n", spinnerValue(spinnerWfbFecN));
            putNestedBooleanAsInt(wfbYamlConfig, "broadcast", "stbc", checkWfbStbc.isChecked());
            putNestedBooleanAsInt(wfbYamlConfig, "broadcast", "ldpc", checkWfbLdpc.isChecked());
            return dumpYaml(wfbYamlConfig);
        }

        String content = editWfbConfig.getText().toString();
        int channel = parseIntOrDefault(spinnerValue(spinnerWfbChannel), 0);
        content = replaceConfValue(content, "channel", String.valueOf(channel));
        content = replaceConfValue(content, channel < 30 ? "txpower" : "driver_txpower_override",
                String.valueOf(seekWfbTxPower.getProgress()));
        content = replaceConfValue(content, "bandwidth", spinnerValue(spinnerWfbBandwidth));
        content = replaceConfValue(content, "mcs_index", spinnerValue(spinnerWfbMcs));
        content = replaceConfValue(content, "fec_k", spinnerValue(spinnerWfbFecK));
        content = replaceConfValue(content, "fec_n", spinnerValue(spinnerWfbFecN));
        content = replaceConfValue(content, "stbc", checkWfbStbc.isChecked() ? "1" : "0");
        content = replaceConfValue(content, "ldpc", checkWfbLdpc.isChecked() ? "1" : "0");
        return content;
    }

    private void fillTelemetryFields(String content) {
        if (telemetryUsesWfbYaml && wfbYamlConfig != null) {
            selectSpinnerValue(spinnerTelemetrySerial, baseSerial(getNestedString(wfbYamlConfig, "telemetry", "serial")));
            selectSpinnerValue(spinnerTelemetryRouter, getNestedString(wfbYamlConfig, "telemetry", "router"));
            selectSpinnerValue(spinnerTelemetryMspFps, getNestedString(wfbYamlConfig, "telemetry", "osd_fps"));
            return;
        }

        selectSpinnerValue(spinnerTelemetrySerial, baseSerial(parseConfValue(content, "serial")));
        selectSpinnerValue(spinnerTelemetryBaud, parseConfValue(content, "baud"));
        selectSpinnerValue(spinnerTelemetryRouter, routerName(parseConfValue(content, "router")));
        selectSpinnerValue(spinnerTelemetryMcs, parseConfValue(content, "mcs_index"));
        selectSpinnerValue(spinnerTelemetryAggregate, parseConfValue(content, "aggregate"));
        selectSpinnerValue(spinnerTelemetryRcChannel, parseConfValue(content, "channels"));
    }

    private String buildTelemetryContentForSave() {
        if (telemetryUsesWfbYaml) {
            if (wfbYamlConfig == null) {
                parseWfbConfig(editTelemetryConfig.getText().toString());
            }
            putNestedString(wfbYamlConfig, "telemetry", "serial", spinnerValue(spinnerTelemetrySerial));
            putNestedString(wfbYamlConfig, "telemetry", "router", spinnerValue(spinnerTelemetryRouter));
            putNestedNumberOrString(wfbYamlConfig, "telemetry", "osd_fps", spinnerValue(spinnerTelemetryMspFps));
            return dumpYaml(wfbYamlConfig);
        }

        String content = editTelemetryConfig.getText().toString();
        content = replaceConfValue(content, "serial", "/dev/" + spinnerValue(spinnerTelemetrySerial));
        content = replaceConfValue(content, "baud", spinnerValue(spinnerTelemetryBaud));
        content = replaceConfValue(content, "router", routerValue(spinnerValue(spinnerTelemetryRouter)));
        content = replaceConfValue(content, "mcs_index", spinnerValue(spinnerTelemetryMcs));
        content = replaceConfValue(content, "aggregate", spinnerValue(spinnerTelemetryAggregate));
        content = replaceConfValue(content, "channels", spinnerValue(spinnerTelemetryRcChannel));
        return content;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(String name) {
        Object existing = majesticConfig.get(name);
        if (existing instanceof Map) {
            return (Map<String, Object>) existing;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        majesticConfig.put(name, created);
        return created;
    }

    private String getStringValue(String section, String key) {
        Object value = section(section).get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private boolean getBooleanValue(String section, String key) {
        Object value = section(section).get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private void putStringValue(String section, String key, String value) {
        if (!TextUtils.isEmpty(value)) {
            section(section).put(key, value);
        }
    }

    private void putNumberOrStringValue(String section, String key, String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        try {
            section(section).put(key, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            section(section).put(key, value);
        }
    }

    private void putBooleanValue(String section, String key, boolean value) {
        section(section).put(key, value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedSection(Map<String, Object> root, String section) {
        Object existing = root.get(section);
        if (existing instanceof Map) {
            return (Map<String, Object>) existing;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        root.put(section, created);
        return created;
    }

    private String getNestedString(Map<String, Object> root, String section, String key) {
        Object value = nestedSection(root, section).get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private boolean getNestedBoolean(Map<String, Object> root, String section, String key) {
        Object value = nestedSection(root, section).get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return "1".equals(String.valueOf(value)) || Boolean.parseBoolean(String.valueOf(value));
    }

    private void putNestedNumberOrString(Map<String, Object> root, String section, String key, String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        try {
            nestedSection(root, section).put(key, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            nestedSection(root, section).put(key, value);
        }
    }

    private void putNestedString(Map<String, Object> root, String section, String key, String value) {
        if (!TextUtils.isEmpty(value)) {
            nestedSection(root, section).put(key, value);
        }
    }

    private void putNestedBooleanAsInt(Map<String, Object> root, String section, String key, boolean value) {
        nestedSection(root, section).put(key, value ? 1 : 0);
    }

    private String parseConfValue(String content, String key) {
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || !trimmed.startsWith(key + "=")) {
                continue;
            }
            return trimmed.substring((key + "=").length()).trim();
        }
        return "";
    }

    private String replaceConfValue(String content, String key, String value) {
        if (TextUtils.isEmpty(value)) {
            return content;
        }
        String[] lines = content.split("\\r?\\n", -1);
        boolean replaced = false;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (!trimmed.startsWith("#") && trimmed.startsWith(key + "=")) {
                builder.append(key).append("=").append(value);
                replaced = true;
            } else {
                builder.append(line);
            }
            if (i < lines.length - 1) {
                builder.append('\n');
            }
        }
        if (!replaced) {
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                builder.append('\n');
            }
            builder.append(key).append("=").append(value).append('\n');
        }
        return builder.toString();
    }

    private int parseIntOrDefault(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String baseSerial(String value) {
        if (value == null) {
            return "";
        }
        return value.startsWith("/dev/") ? value.substring("/dev/".length()) : value;
    }

    private String routerName(String value) {
        if ("0".equals(value)) {
            return "mavfwd";
        }
        if ("1".equals(value)) {
            return "mavlink-routed";
        }
        if ("2".equals(value)) {
            return "msposd";
        }
        return value;
    }

    private String routerValue(String value) {
        if ("mavfwd".equals(value)) {
            return "0";
        }
        if ("mavlink-routed".equals(value)) {
            return "1";
        }
        if ("msposd".equals(value)) {
            return "2";
        }
        return value;
    }

    private void setBusy(boolean busy, String message) {
        findViewById(R.id.btnConnect).setEnabled(!busy);
        btnSaveMajestic.setEnabled(!busy && majesticConfig != null);
        textStatus.setText(message);
    }

    private void postError(String message) {
        mainHandler.post(() -> {
            setBusy(false, message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private String emptyFallback(String value) {
        return TextUtils.isEmpty(value) ? "unknown" : value;
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private String readStream(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[4096];
        int read;
        StringBuilder builder = new StringBuilder();
        while ((read = inputStream.read(buffer)) != -1) {
            builder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private String readFile(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return readStream(inputStream);
        }
    }

    private void writeFile(File file, String content) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void copyAssetToFile(String assetPath, File target) throws IOException {
        try (InputStream inputStream = getAssets().open(assetPath);
             FileOutputStream outputStream = new FileOutputStream(target)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        }
    }

    private String md5Hex(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        try (FileInputStream inputStream = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static class ConnectionConfig {
        final String host;
        final int port;
        final String username;
        final String password;

        ConnectionConfig(String host, int port, String username, String password) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }
    }
}
