package com.openipc.pixelpilot;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

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
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
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
    private static final String OPENIPC_BUILDER_RELEASE_API = "https://api.github.com/repos/OpenIPC/builder/releases/latest";
    private static final String GREG_APFPV_CONTENTS_API = "https://api.github.com/repos/sickgreg/OpenIPC_sickgregFPV_apfpv/contents";
    private static final String OPENIPC_LOG_PREFIX = "openipc_config";
    private static final long OPENIPC_LOG_MAX_BYTES = 10L * 1024L * 1024L;
    private static final int OPENIPC_LOG_MAX_FILES = 5;
    private static final int NAV_TEXT_SELECTED = Color.WHITE;
    private static final int NAV_TEXT_IDLE = Color.rgb(154, 170, 182);
    private static final Map<String, String> FIRMWARE_MANUFACTURER_NAMES = new LinkedHashMap<>();
    private static final Map<String, String> FIRMWARE_DEVICE_NAMES = new LinkedHashMap<>();
    private static final Map<String, String> FIRMWARE_TYPE_NAMES = new LinkedHashMap<>();

    static {
        FIRMWARE_MANUFACTURER_NAMES.put("*", "Generic Manufacturer");
        FIRMWARE_MANUFACTURER_NAMES.put("openipc", "OpenIPC");
        FIRMWARE_MANUFACTURER_NAMES.put("emax", "EMax");
        FIRMWARE_MANUFACTURER_NAMES.put("runcam", "RunCam");
        FIRMWARE_MANUFACTURER_NAMES.put("caddx", "Caddx");

        FIRMWARE_DEVICE_NAMES.put("*", "Generic Device");
        FIRMWARE_DEVICE_NAMES.put("ssc338q", "Generic SSC338Q");
        FIRMWARE_DEVICE_NAMES.put("mario-aio", "OpenIPC Mario AIO");
        FIRMWARE_DEVICE_NAMES.put("thinker-aio", "OpenIPC Thinker AIO");
        FIRMWARE_DEVICE_NAMES.put("thinker-aio-wifi", "OpenIPC Thinker AIO (Built In Wifi)");
        FIRMWARE_DEVICE_NAMES.put("urllc-aio", "OpenIPC Generic SSC338");
        FIRMWARE_DEVICE_NAMES.put("wifilink", "WiFi Link");
        FIRMWARE_DEVICE_NAMES.put("wyvern-link", "Wyvern Link");

        FIRMWARE_TYPE_NAMES.put("fpv", "OpenIPC-FPV firmware");
        FIRMWARE_TYPE_NAMES.put("rubyfpv", "RubyFPV firmware");
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<Intent> firmwarePackagePicker;

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
    private Spinner spinnerPreferredFirmwareSource;
    private Spinner spinnerFirmwareSource;
    private Spinner spinnerFirmwareManufacturer;
    private Spinner spinnerFirmwareDevice;
    private Spinner spinnerFirmwareWfbRuby;
    private Spinner spinnerFirmwareBySoc;
    private Spinner spinnerSensorBin;
    private SeekBar seekWfbTxPower;
    private EditText editWfbConfig;
    private EditText editTelemetryConfig;
    private EditText editSensorConfigPath;
    private EditText editSetupScanSubnet;
    private EditText editFirmwareRestorePath;
    private EditText editFirmwareBootloaderUrl;
    private EditText editFirmwareLocalPackage;
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
    private CheckBox checkAlinkDroneOnBoot;
    private CheckBox checkUpdatesOnStartup;
    private CheckBox checkFirmwareFocusedMode;
    private CheckBox checkRememberOpenIpcPassword;
    private CheckBox checkShowRawConfigs;
    private CheckBox checkFirmwareRestoreConfirm;
    private CheckBox checkFirmwareBootloaderConfirm;
    private CheckBox checkFirmwareMethodAutomatic;
    private CheckBox checkFirmwareMethodBySoc;
    private CheckBox checkFirmwareMethodLocal;
    private TextView textStatus;
    private TextView textDeviceInfo;
    private EditText textMajesticPreview;
    private TextView textWfbTxPower;
    private TextView textSetupInfo;
    private TextView textSetupScanStatus;
    private TextView textSetupScanResults;
    private TextView textSetupLocalIp;
    private TextView textAdaptiveLinkStatus;
    private TextView textAdvancedOutput;
    private TextView textFirmwareOutput;
    private TextView textFirmwareBackupProgress;
    private TextView textFirmwareRestoreProgress;
    private TextView textPreferencesInfo;
    private Button btnSaveMajestic;
    private Button btnFirmwareRestore;
    private Button btnFirmwareBootloader;
    private Button btnFirmwareUpdate;
    private ScrollView openIpcContentScroll;
    private ProgressBar progressFirmwareBackup;
    private ProgressBar progressFirmwareRestore;
    private ProgressBar progressFirmwareUpdate;
    private View cardFirmwareMethodAutomatic;
    private View cardFirmwareMethodBySoc;
    private View cardFirmwareMethodLocal;
    private View panelFirmwareAutomatic;
    private View panelFirmwareBySoc;
    private View panelFirmwareLocal;
    private View rawCameraConfigPanel;
    private View rawWfbConfigPanel;
    private View rawTelemetryConfigPanel;
    private View setupConfigPanel;
    private volatile boolean cancelSetupScan;
    private Button[] navButtons;
    private int[] navIconRes;
    private View[] tabSections;

    private Map<String, Object> majesticConfig;
    private Map<String, Object> wfbYamlConfig;
    private final Map<String, Map<String, Map<String, String>>> automaticFirmwareChoices = new LinkedHashMap<>();
    private String activeWfbPath = REMOTE_WFB_YAML;
    private boolean telemetryUsesWfbYaml = false;
    private boolean rawCameraConfigEditing = false;
    private boolean rawWfbConfigEditing = false;
    private boolean rawTelemetryConfigEditing = false;
    private String connectedSoc = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_openipc_config);
        firmwarePackagePicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                cacheSelectedFirmwarePackage(result.getData().getData());
            }
        });

        bindViews();
        setupChoiceControls();
        setupRawConfigEditors();
        setupEditTextBehavior(findViewById(android.R.id.content));
        applyOpenIpcTextColors(findViewById(android.R.id.content));
        loadConnectionSettings();
        setupTabs();

        findViewById(R.id.imageOpenIpcLogo).setOnClickListener(v -> showOpenIpcAboutDialog());
        findViewById(R.id.btnConnect).setOnClickListener(v -> connectAndRead());
        textStatus.setOnClickListener(v -> showOpenIpcLogs());
        findViewById(R.id.btnReadMajestic).setOnClickListener(v -> readMajesticConfig());
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
        findViewById(R.id.btnScanSetupNetwork).setOnClickListener(v -> scanSetupNetwork());
        findViewById(R.id.btnCancelSetupScan).setOnClickListener(v -> cancelSetupScan = true);
        findViewById(R.id.btnCheckAdaptiveLinkStatus).setOnClickListener(v -> checkAdaptiveLinkStatus());
        checkAlinkDroneOnBoot.setOnClickListener(v -> setAlinkDroneOnBoot(checkAlinkDroneOnBoot.isChecked()));
        findViewById(R.id.btnSystemReport).setOnClickListener(v -> runCommandToOutput("echo '=== System Information ==='; uname -a; echo '=== CPU Info ==='; cat /proc/cpuinfo | grep -i 'model name\\|processor'; echo '=== Memory Info ==='; free -h; echo '=== Disk Usage ==='; df -h; echo '=== Network Interfaces ==='; ip addr; echo '=== Loaded Modules ==='; lsmod 2>/dev/null || echo 'lsmod not available'; echo '=== OpenIPC Configurations ==='; for f in /etc/os-release /etc/majestic.yaml /etc/wfb.yaml /etc/wfb.conf /etc/telemetry.conf /etc/alink.conf /etc/vtxmenu.ini /etc/txprofiles.conf /etc/datalink.conf; do echo \"--- $f ---\"; cat $f 2>/dev/null || echo 'File not found'; done", textAdvancedOutput, "Generating report..."));
        findViewById(R.id.btnViewSystemLogs).setOnClickListener(v -> runCommandToOutput("echo '=== Journal Logs (Last 100 Entries) ==='; journalctl -n 100 2>/dev/null || echo 'journalctl not available'; echo; echo '=== OpenIPC logread Output ==='; logread 2>/dev/null || echo 'logread command not available'", textAdvancedOutput, "Reading logs..."));
        findViewById(R.id.btnNetworkDiagnostics).setOnClickListener(v -> runCommandToOutput("echo '=== Network Interfaces ==='; ip addr; echo '=== Routes ==='; ip route; echo '=== Wireless ==='; iw dev 2>/dev/null || echo 'iw not available'; echo '=== USB ==='; lsusb 2>/dev/null || echo 'lsusb not available'; echo '=== Connection Stats ==='; netstat -tunap 2>/dev/null || ss -tunap 2>/dev/null || echo 'netstat/ss not available'", textAdvancedOutput, "Running diagnostics..."));
        findViewById(R.id.btnFirmwareStatus).setOnClickListener(v -> runCommandToOutput("hostname; fw_printenv soc sensor 2>/dev/null; cat /etc/os-release 2>/dev/null; df -h", textFirmwareOutput, "Reading firmware status..."));
        findViewById(R.id.btnFirmwareBackup).setOnClickListener(v -> createFirmwareBackup());
        findViewById(R.id.btnFirmwareRestore).setOnClickListener(v -> restoreFirmwareBackup());
        findViewById(R.id.btnFirmwareBootloader).setOnClickListener(v -> replaceFirmwareBootloader());
        findViewById(R.id.btnFirmwareUpdate).setOnClickListener(v -> performFirmwareSysupgrade());
        findViewById(R.id.btnFirmwareClear).setOnClickListener(v -> clearFirmwareForm());
        findViewById(R.id.btnFirmwareSelectLocalPackage).setOnClickListener(v -> selectLocalFirmwarePackage());
        checkFirmwareRestoreConfirm.setOnCheckedChangeListener((buttonView, isChecked) -> btnFirmwareRestore.setEnabled(isChecked));
        checkFirmwareBootloaderConfirm.setOnCheckedChangeListener((buttonView, isChecked) -> btnFirmwareBootloader.setEnabled(isChecked));
        checkFirmwareMethodAutomatic.setOnClickListener(v -> selectFirmwareMethod(checkFirmwareMethodAutomatic));
        checkFirmwareMethodBySoc.setOnClickListener(v -> selectFirmwareMethod(checkFirmwareMethodBySoc));
        checkFirmwareMethodLocal.setOnClickListener(v -> selectFirmwareMethod(checkFirmwareMethodLocal));
        cardFirmwareMethodAutomatic.setOnClickListener(v -> selectFirmwareMethod(checkFirmwareMethodAutomatic));
        cardFirmwareMethodBySoc.setOnClickListener(v -> selectFirmwareMethod(checkFirmwareMethodBySoc));
        cardFirmwareMethodLocal.setOnClickListener(v -> selectFirmwareMethod(checkFirmwareMethodLocal));
        spinnerFirmwareManufacturer.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateFirmwareDeviceChoices(String.valueOf(parent.getItemAtPosition(position)));
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        spinnerFirmwareDevice.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateFirmwarePackageChoices(spinnerValue(spinnerFirmwareManufacturer), String.valueOf(parent.getItemAtPosition(position)));
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        spinnerFirmwareSource.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                loadFirmwareBySocChoices();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        findViewById(R.id.btnClearOpenIpcSettings).setOnClickListener(v -> clearSavedOpenIpcSettings());
        findViewById(R.id.btnPreferencesDeviceInfo).setOnClickListener(v -> runCommandToOutput("hostname; uname -a; uptime; df -h; free; ps", textPreferencesInfo, "Getting device info..."));
        findViewById(R.id.btnShowLocalLogHint).setOnClickListener(v -> textPreferencesInfo.setText("Android logs are available through logcat. PixelPilot app data is stored under the app sandbox."));
        checkUpdatesOnStartup.setOnCheckedChangeListener((buttonView, isChecked) -> savePreferenceBoolean("check_updates_on_startup", isChecked));
        checkFirmwareFocusedMode.setOnCheckedChangeListener((buttonView, isChecked) -> savePreferenceBoolean("firmware_focused_mode", isChecked));
        checkRememberOpenIpcPassword.setOnCheckedChangeListener((buttonView, isChecked) -> savePreferenceBoolean("remember_openipc_password", isChecked));
        checkShowRawConfigs.setOnCheckedChangeListener((buttonView, isChecked) -> {
            savePreferenceBoolean("show_raw_configs", isChecked);
            updateRawConfigVisibility(isChecked);
        });
        spinnerPreferredFirmwareSource.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString("preferred_firmware_source", spinnerValue(spinnerPreferredFirmwareSource))
                        .apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
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
        spinnerPreferredFirmwareSource = findViewById(R.id.spinnerPreferredFirmwareSource);
        spinnerFirmwareSource = findViewById(R.id.spinnerFirmwareSource);
        spinnerFirmwareManufacturer = findViewById(R.id.spinnerFirmwareManufacturer);
        spinnerFirmwareDevice = findViewById(R.id.spinnerFirmwareDevice);
        spinnerFirmwareWfbRuby = findViewById(R.id.spinnerFirmwareWfbRuby);
        spinnerFirmwareBySoc = findViewById(R.id.spinnerFirmwareBySoc);
        spinnerSensorBin = findViewById(R.id.spinnerSensorBin);
        seekWfbTxPower = findViewById(R.id.seekWfbTxPower);
        editWfbConfig = findViewById(R.id.editWfbConfig);
        editTelemetryConfig = findViewById(R.id.editTelemetryConfig);
        editSensorConfigPath = findViewById(R.id.editSensorConfigPath);
        editSetupScanSubnet = findViewById(R.id.editSetupScanSubnet);
        editFirmwareRestorePath = findViewById(R.id.editFirmwareRestorePath);
        editFirmwareBootloaderUrl = findViewById(R.id.editFirmwareBootloaderUrl);
        editFirmwareLocalPackage = findViewById(R.id.editFirmwareLocalPackage);
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
        checkAlinkDroneOnBoot = findViewById(R.id.checkAlinkDroneOnBoot);
        checkUpdatesOnStartup = findViewById(R.id.checkUpdatesOnStartup);
        checkFirmwareFocusedMode = findViewById(R.id.checkFirmwareFocusedMode);
        checkRememberOpenIpcPassword = findViewById(R.id.checkRememberOpenIpcPassword);
        checkShowRawConfigs = findViewById(R.id.checkShowRawConfigs);
        checkFirmwareRestoreConfirm = findViewById(R.id.checkFirmwareRestoreConfirm);
        checkFirmwareBootloaderConfirm = findViewById(R.id.checkFirmwareBootloaderConfirm);
        checkFirmwareMethodAutomatic = findViewById(R.id.checkFirmwareMethodAutomatic);
        checkFirmwareMethodBySoc = findViewById(R.id.checkFirmwareMethodBySoc);
        checkFirmwareMethodLocal = findViewById(R.id.checkFirmwareMethodLocal);
        textStatus = findViewById(R.id.textStatus);
        textDeviceInfo = findViewById(R.id.textDeviceInfo);
        textMajesticPreview = findViewById(R.id.textMajesticPreview);
        textWfbTxPower = findViewById(R.id.textWfbTxPower);
        textSetupInfo = findViewById(R.id.textSetupInfo);
        textSetupScanStatus = findViewById(R.id.textSetupScanStatus);
        textSetupScanResults = findViewById(R.id.textSetupScanResults);
        textSetupLocalIp = findViewById(R.id.textSetupLocalIp);
        textAdaptiveLinkStatus = findViewById(R.id.textAdaptiveLinkStatus);
        textAdvancedOutput = findViewById(R.id.textAdvancedOutput);
        textFirmwareOutput = findViewById(R.id.textFirmwareOutput);
        textFirmwareBackupProgress = findViewById(R.id.textFirmwareBackupProgress);
        textFirmwareRestoreProgress = findViewById(R.id.textFirmwareRestoreProgress);
        textPreferencesInfo = findViewById(R.id.textPreferencesInfo);
        textPreferencesInfo.setTypeface(Typeface.MONOSPACE);
        btnSaveMajestic = findViewById(R.id.btnSaveMajestic);
        btnFirmwareRestore = findViewById(R.id.btnFirmwareRestore);
        btnFirmwareBootloader = findViewById(R.id.btnFirmwareBootloader);
        btnFirmwareUpdate = findViewById(R.id.btnFirmwareUpdate);
        openIpcContentScroll = findViewById(R.id.openIpcContentScroll);
        progressFirmwareBackup = findViewById(R.id.progressFirmwareBackup);
        progressFirmwareRestore = findViewById(R.id.progressFirmwareRestore);
        progressFirmwareUpdate = findViewById(R.id.progressFirmwareUpdate);
        cardFirmwareMethodAutomatic = findViewById(R.id.cardFirmwareMethodAutomatic);
        cardFirmwareMethodBySoc = findViewById(R.id.cardFirmwareMethodBySoc);
        cardFirmwareMethodLocal = findViewById(R.id.cardFirmwareMethodLocal);
        panelFirmwareAutomatic = findViewById(R.id.panelFirmwareAutomatic);
        panelFirmwareBySoc = findViewById(R.id.panelFirmwareBySoc);
        panelFirmwareLocal = findViewById(R.id.panelFirmwareLocal);
        rawCameraConfigPanel = findViewById(R.id.rawCameraConfigPanel);
        rawWfbConfigPanel = findViewById(R.id.rawWfbConfigPanel);
        rawTelemetryConfigPanel = findViewById(R.id.rawTelemetryConfigPanel);
        setupConfigPanel = findViewById(R.id.setupConfigPanel);
        tabSections = new View[]{
                findViewById(R.id.sectionCamera),
                findViewById(R.id.sectionWfb),
                findViewById(R.id.sectionTelemetry),
                findViewById(R.id.sectionSetup),
                findViewById(R.id.sectionAdvanced),
                findViewById(R.id.sectionFirmware),
                findViewById(R.id.sectionPreferences)
        };
        navButtons = new Button[]{
                findViewById(R.id.navCamera),
                findViewById(R.id.navWfb),
                findViewById(R.id.navTelemetry),
                findViewById(R.id.navSetup),
                findViewById(R.id.navAdvanced),
                findViewById(R.id.navFirmware),
                findViewById(R.id.navPrefs)
        };
        navIconRes = new int[]{
                R.drawable.openipc_tab_camera,
                R.drawable.openipc_tab_wifi,
                R.drawable.openipc_tab_telemetry,
                R.drawable.openipc_tab_settings,
                R.drawable.openipc_tab_advanced,
                R.drawable.openipc_tab_firmware,
                R.drawable.openipc_tab_settings
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
        setSpinnerItems(spinnerPreferredFirmwareSource, "OpenIPC", "OpenIPC FPV", "Custom/Local");
        setSpinnerItems(spinnerFirmwareSource, "OpenIPC Builder", "Greg APFPV");
        setSpinnerItems(spinnerFirmwareManufacturer, "");
        setSpinnerItems(spinnerFirmwareDevice, "");
        setSpinnerItems(spinnerFirmwareWfbRuby, "");
        setSpinnerItems(spinnerFirmwareBySoc, "");
        loadSensorAssetChoices();
        updateSetupScannerDefaults();
        updateSetupStage(false);
        updateFirmwareMethodPanels();
        loadPreferencesSettings();
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
                editText.setImeOptions((editText.getImeOptions()
                        & ~EditorInfo.IME_MASK_ACTION
                        & ~EditorInfo.IME_FLAG_NAVIGATE_NEXT
                        & ~EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS)
                        | EditorInfo.IME_ACTION_DONE
                        | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
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

    private void setupRawConfigEditors() {
        setupRawConfigEditor(textMajesticPreview, "Camera", () -> rawCameraConfigEditing = true);
        setupRawConfigEditor(editWfbConfig, "WFB", () -> rawWfbConfigEditing = true);
        setupRawConfigEditor(editTelemetryConfig, "Telemetry", () -> rawTelemetryConfigEditing = true);
        setRawConfigEditing(textMajesticPreview, false);
        setRawConfigEditing(editWfbConfig, false);
        setRawConfigEditing(editTelemetryConfig, false);
    }

    private void setupRawConfigEditor(EditText editor, String label, Runnable onConfirmed) {
        editor.setOnClickListener(v -> {
            if (!editor.isFocusableInTouchMode()) {
                Toast.makeText(this, "Long press to enter raw config edit mode", Toast.LENGTH_SHORT).show();
            }
        });
        editor.setOnLongClickListener(v -> {
            showRawConfigEditWarning(editor, label, onConfirmed);
            return true;
        });
    }

    private void showOpenIpcAboutDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundResource(R.drawable.openipc_log_dialog_bg);
        container.setPadding(dp(18), dp(16), dp(18), dp(16));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.openipc_logo);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        container.addView(logo, new LinearLayout.LayoutParams(dp(150), dp(42)));

        TextView version = new TextView(this);
        version.setText("PixelPilot " + appVersionName() + "\nOpenIPC Config");
        version.setTextColor(getColor(R.color.openipc_text_muted));
        version.setTextSize(12);
        version.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        versionParams.setMargins(0, dp(8), 0, dp(14));
        container.addView(version, versionParams);

        container.addView(aboutLinkRow(R.drawable.openipc_about_github, "GitHub", "https://github.com/OpenIPC/"));
        container.addView(aboutLinkRow(R.drawable.openipc_about_telegram, "Telegram", "https://t.me/+BMyMoolVOpkzNWUy"));
        container.addView(aboutLinkRow(R.drawable.openipc_about_discord, "Discord", "https://discord.gg/KtWgDV6Y"));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsParams.setMargins(0, dp(14), 0, 0);
        container.addView(buttons, buttonsParams);

        Button closeButton = new Button(this);
        closeButton.setText("Close");
        closeButton.setTextColor(getColor(R.color.openipc_button_text));
        closeButton.setTextSize(11);
        closeButton.setAllCaps(false);
        closeButton.setBackgroundResource(R.drawable.openipc_primary_button);
        closeButton.setBackgroundTintList(null);
        closeButton.setMinWidth(0);
        closeButton.setMinHeight(0);
        closeButton.setPadding(dp(10), 0, dp(10), 0);
        buttons.addView(closeButton, new LinearLayout.LayoutParams(dp(78), dp(34)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();
        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private View aboutLinkRow(int iconRes, String title, String url) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setOnClickListener(v -> openUrl(url));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        row.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView text = new TextView(this);
        text.setText(title + "\n" + url);
        text.setTextColor(getColor(R.color.openipc_text));
        text.setTextSize(12);
        text.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.setMargins(dp(12), 0, 0, 0);
        row.addView(text, textParams);
        return row;
    }

    private String appVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show();
        }
    }

    private void showRawConfigEditWarning(EditText editor, String label, Runnable onConfirmed) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundResource(R.drawable.openipc_log_dialog_bg);
        container.setPadding(dp(18), dp(16), dp(18), dp(16));

        TextView title = new TextView(this);
        title.setText("Raw config editing");
        title.setTextColor(getColor(R.color.openipc_text));
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        container.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(this);
        message.setText("If you do not understand the configuration file syntax, do not use raw file editing. Confirm only if you know what you are doing.");
        message.setTextColor(getColor(R.color.openipc_text_muted));
        message.setTextSize(13);
        message.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, dp(8), 0, dp(16));
        container.addView(message, messageParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        container.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Button backButton = new Button(this);
        backButton.setText("Back");
        backButton.setTextColor(getColor(R.color.openipc_text_muted));
        backButton.setTextSize(11);
        backButton.setAllCaps(false);
        backButton.setBackgroundResource(R.drawable.openipc_secondary_button);
        backButton.setBackgroundTintList(null);
        backButton.setMinWidth(0);
        backButton.setMinHeight(0);
        backButton.setPadding(dp(10), 0, dp(10), 0);
        buttons.addView(backButton, new LinearLayout.LayoutParams(dp(78), dp(34)));

        Button confirmButton = new Button(this);
        confirmButton.setText("Confirm");
        confirmButton.setTextColor(getColor(R.color.openipc_button_text));
        confirmButton.setTextSize(11);
        confirmButton.setAllCaps(false);
        confirmButton.setBackgroundResource(R.drawable.openipc_primary_button);
        confirmButton.setBackgroundTintList(null);
        confirmButton.setMinWidth(0);
        confirmButton.setMinHeight(0);
        confirmButton.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(dp(92), dp(34));
        confirmParams.setMargins(dp(8), 0, 0, 0);
        buttons.addView(confirmButton, confirmParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();
        backButton.setOnClickListener(v -> dialog.dismiss());
        confirmButton.setOnClickListener(v -> {
            onConfirmed.run();
            setRawConfigEditing(editor, true);
            editor.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
            }
            Toast.makeText(this, label + " raw config editing enabled", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private void setRawConfigEditing(EditText editor, boolean editing) {
        editor.setFocusable(editing);
        editor.setFocusableInTouchMode(editing);
        editor.setCursorVisible(editing);
        editor.setLongClickable(true);
        if (!editing) {
            clearEditFocus(editor);
        }
    }

    private void resetRawConfigEditing() {
        rawCameraConfigEditing = false;
        rawWfbConfigEditing = false;
        rawTelemetryConfigEditing = false;
        setRawConfigEditing(textMajesticPreview, false);
        setRawConfigEditing(editWfbConfig, false);
        setRawConfigEditing(editTelemetryConfig, false);
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
        if (root.getId() == R.id.textFirmwareRestoreWarning
                || root.getId() == R.id.textFirmwareBootloaderWarning) {
            return;
        }
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
                    if (isFirmwareDeviceDependentSpinner(spinner) && TextUtils.isEmpty(connectedSoc)) {
                        Toast.makeText(this, "Connect to device first", Toast.LENGTH_SHORT).show();
                        return true;
                    }
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

    private boolean isFirmwareDeviceDependentSpinner(Spinner spinner) {
        return spinner == spinnerFirmwareManufacturer
                || spinner == spinnerFirmwareDevice
                || spinner == spinnerFirmwareWfbRuby
                || spinner == spinnerFirmwareBySoc;
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

    private void loadFirmwareBySocChoices() {
        String soc = connectedSoc == null ? "" : connectedSoc.trim();
        if (TextUtils.isEmpty(soc)) {
            setSpinnerItems(spinnerFirmwareBySoc, "Connect to device first");
            return;
        }
        String source = spinnerValue(spinnerFirmwareSource);

        executor.execute(() -> {
            try {
                List<String> names = "Greg APFPV".equals(source)
                        ? fetchGregApfpvFirmwareNames()
                        : fetchOpenIpcBuilderFirmwareNames();
                Map<String, Map<String, Map<String, String>>> automaticChoices = buildAutomaticFirmwareChoices(names, soc);
                List<String> filtered = new ArrayList<>();
                String lowerSoc = soc.toLowerCase();
                for (String name : names) {
                    String lowerName = name.toLowerCase();
                    if (lowerName.contains(lowerSoc) && lowerName.contains("fpv") && name.endsWith(".tgz")) {
                        filtered.add(name);
                    }
                }
                if (filtered.isEmpty()) {
                    filtered.add("No firmware found for " + soc);
                }
                mainHandler.post(() -> {
                    automaticFirmwareChoices.clear();
                    automaticFirmwareChoices.putAll(automaticChoices);
                    updateFirmwareManufacturerChoices();
                    setSpinnerItems(spinnerFirmwareBySoc, filtered.toArray(new String[0]));
                });
            } catch (Exception e) {
                mainHandler.post(() -> setSpinnerItems(spinnerFirmwareBySoc, "Failed to load firmware list"));
            }
        });
    }

    private List<String> fetchOpenIpcBuilderFirmwareNames() throws Exception {
        JSONObject json = new JSONObject(readUrl(OPENIPC_BUILDER_RELEASE_API));
        JSONArray assets = json.optJSONArray("assets");
        List<String> names = new ArrayList<>();
        if (assets == null) {
            return names;
        }
        for (int i = 0; i < assets.length(); i++) {
            String name = assets.getJSONObject(i).optString("name", "");
            if (!TextUtils.isEmpty(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private List<String> fetchGregApfpvFirmwareNames() throws Exception {
        JSONArray items = new JSONArray(readUrl(GREG_APFPV_CONTENTS_API));
        List<String> names = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String name = item.optString("name", "");
            if ("file".equals(item.optString("type", "")) && name.endsWith(".tgz")) {
                names.add(name);
            }
        }
        return names;
    }

    private Map<String, Map<String, Map<String, String>>> buildAutomaticFirmwareChoices(List<String> names, String soc) {
        Map<String, Map<String, Map<String, String>>> choices = new LinkedHashMap<>();
        String lowerSoc = soc.toLowerCase();
        for (String name : names) {
            String lowerName = name.toLowerCase();
            if (!lowerName.startsWith(lowerSoc + "_") || lowerName.contains("rubyfpv")) {
                continue;
            }
            String[] parts = name.split("_", 3);
            if (parts.length < 3) {
                continue;
            }
            String firmwareType = parts[1];
            String tail = parts[2];
            int dash = tail.indexOf('-');
            if (dash <= 0) {
                continue;
            }
            String manufacturer = tail.substring(0, dash);
            String deviceAndMemory = tail.substring(dash + 1);
            int memoryDash = Math.max(deviceAndMemory.lastIndexOf("-nand"), deviceAndMemory.lastIndexOf("-nor"));
            if (memoryDash <= 0) {
                continue;
            }
            String device = deviceAndMemory.substring(0, memoryDash);
            choices.computeIfAbsent(manufacturer, key -> new LinkedHashMap<>())
                    .computeIfAbsent(device, key -> new LinkedHashMap<>())
                    .put(firmwareType, name);
        }
        for (String name : names) {
            if (name.toLowerCase().startsWith("ssc338q_rubyfpv")) {
                addRubyFirmwareChoice(choices, name, soc);
            }
        }
        if (!choices.isEmpty() && "ssc338q".equalsIgnoreCase(soc)) {
            choices.computeIfAbsent("openipc", key -> new LinkedHashMap<>())
                    .computeIfAbsent("thinker-aio-wifi", key -> new LinkedHashMap<>())
                    .put("fpv", "ssc338q_fpv_openipc-thinker-aio-nor.tgz");
        }
        return choices;
    }

    private void addRubyFirmwareChoice(Map<String, Map<String, Map<String, String>>> choices, String name, String soc) {
        if (!"ssc338q".equalsIgnoreCase(soc) || choices.isEmpty()) {
            return;
        }
        if (name.contains("thinker_internal")) {
            choices.computeIfAbsent("openipc", key -> new LinkedHashMap<>())
                    .computeIfAbsent("thinker-aio-wifi", key -> new LinkedHashMap<>())
                    .put("rubyfpv", name);
            return;
        }
        for (Map<String, Map<String, String>> devices : choices.values()) {
            for (Map<String, String> packages : devices.values()) {
                packages.put("rubyfpv", name);
            }
        }
    }

    private void updateFirmwareManufacturerChoices() {
        if (automaticFirmwareChoices.isEmpty()) {
            setSpinnerItems(spinnerFirmwareManufacturer, "No firmware found");
            setSpinnerItems(spinnerFirmwareDevice, "");
            setSpinnerItems(spinnerFirmwareWfbRuby, "");
            return;
        }
        List<String> manufacturers = new ArrayList<>(automaticFirmwareChoices.keySet());
        Collections.sort(manufacturers);
        List<String> labels = new ArrayList<>();
        for (String manufacturer : manufacturers) {
            labels.add(firmwareManufacturerLabel(manufacturer));
        }
        setSpinnerItems(spinnerFirmwareManufacturer, labels.toArray(new String[0]));
        updateFirmwareDeviceChoices(labels.get(0));
    }

    private void updateFirmwareDeviceChoices() {
        updateFirmwareDeviceChoices(spinnerValue(spinnerFirmwareManufacturer));
    }

    private void updateFirmwareDeviceChoices(String manufacturer) {
        String manufacturerId = firmwareManufacturerId(manufacturer);
        Map<String, Map<String, String>> devices = automaticFirmwareChoices.get(manufacturerId);
        if (devices == null || devices.isEmpty()) {
            setSpinnerItems(spinnerFirmwareDevice, "");
            setSpinnerItems(spinnerFirmwareWfbRuby, "");
            return;
        }
        List<String> deviceNames = new ArrayList<>(devices.keySet());
        Collections.sort(deviceNames);
        List<String> labels = new ArrayList<>();
        for (String device : deviceNames) {
            labels.add(firmwareDeviceLabel(device));
        }
        setSpinnerItems(spinnerFirmwareDevice, labels.toArray(new String[0]));
        updateFirmwarePackageChoices(manufacturer, labels.get(0));
    }

    private void updateFirmwarePackageChoices() {
        updateFirmwarePackageChoices(spinnerValue(spinnerFirmwareManufacturer), spinnerValue(spinnerFirmwareDevice));
    }

    private void updateFirmwarePackageChoices(String manufacturer, String device) {
        Map<String, Map<String, String>> devices = automaticFirmwareChoices.get(firmwareManufacturerId(manufacturer));
        Map<String, String> packages = devices == null ? null : devices.get(firmwareDeviceId(device));
        if (packages == null || packages.isEmpty()) {
            setSpinnerItems(spinnerFirmwareWfbRuby, "");
            return;
        }
        List<String> packageNames = new ArrayList<>(packages.keySet());
        Collections.sort(packageNames);
        List<String> labels = new ArrayList<>();
        for (String packageName : packageNames) {
            labels.add(firmwareTypeLabel(packageName));
        }
        setSpinnerItems(spinnerFirmwareWfbRuby, labels.toArray(new String[0]));
    }

    private String firmwareManufacturerLabel(String id) {
        return FIRMWARE_MANUFACTURER_NAMES.containsKey(id) ? FIRMWARE_MANUFACTURER_NAMES.get(id) : id;
    }

    private String firmwareDeviceLabel(String id) {
        return FIRMWARE_DEVICE_NAMES.containsKey(id) ? FIRMWARE_DEVICE_NAMES.get(id) : id;
    }

    private String firmwareTypeLabel(String id) {
        return FIRMWARE_TYPE_NAMES.containsKey(id) ? FIRMWARE_TYPE_NAMES.get(id) : id;
    }

    private String firmwareManufacturerId(String label) {
        return firmwareIdFromLabel(FIRMWARE_MANUFACTURER_NAMES, label);
    }

    private String firmwareDeviceId(String label) {
        return firmwareIdFromLabel(FIRMWARE_DEVICE_NAMES, label);
    }

    private String firmwareTypeId(String label) {
        return firmwareIdFromLabel(FIRMWARE_TYPE_NAMES, label);
    }

    private String firmwareIdFromLabel(Map<String, String> labels, String value) {
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return value;
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
            applyNavIcon(navButtons[i], navIconRes[i], i == selectedIndex ? NAV_TEXT_SELECTED : NAV_TEXT_IDLE);
        }
        if (openIpcContentScroll != null) {
            openIpcContentScroll.post(() -> openIpcContentScroll.scrollTo(0, 0));
        }
    }

    private void applyNavIcon(Button button, int iconRes, int color) {
        Drawable icon = getDrawable(iconRes);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(color);
        }
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
        button.setCompoundDrawablePadding(dp(10));
    }

    private void updateSetupStage(boolean connected) {
        if (setupConfigPanel != null) {
            setupConfigPanel.setVisibility(connected ? View.VISIBLE : View.GONE);
        }
    }

    private String detectWifiCard(String lsusbOutput) {
        String text = lsusbOutput == null ? "" : lsusbOutput.toLowerCase();
        if (text.contains("0bda:8812") || text.contains("0bda:881a")
                || text.contains("0b05:17d2") || text.contains("2357:0101")
                || text.contains("2604:0012")) {
            return "88XXau";
        }
        if (text.contains("0bda:a81a")) {
            return "8812eu";
        }
        if (text.contains("0bda:f72b") || text.contains("0bda:b733")) {
            return "8733bu";
        }
        if (text.contains("0cf3:9271") || text.contains("040d:3801")) {
            return "ar9271";
        }
        if (text.contains("0bda:c812")) {
            return "88x2cu";
        }
        return "";
    }

    private void loadConnectionSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        editHost.setText(prefs.getString("host", "10.5.0.10"));
        editPort.setText(String.valueOf(prefs.getInt("port", 22)));
        editUsername.setText(prefs.getString("username", "root"));
        editPassword.setText(prefs.getBoolean("remember_openipc_password", true) ? prefs.getString("password", "12345") : "");
    }

    private void saveConnectionSettings(ConnectionConfig config) {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit()
                .putString("host", config.host)
                .putInt("port", config.port)
                .putString("username", config.username);
        if (prefs.getBoolean("remember_openipc_password", true)) {
            editor.putString("password", config.password);
        } else {
            editor.remove("password");
        }
        editor.apply();
    }

    private void loadPreferencesSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        checkUpdatesOnStartup.setChecked(prefs.getBoolean("check_updates_on_startup", true));
        checkFirmwareFocusedMode.setChecked(prefs.getBoolean("firmware_focused_mode", false));
        checkRememberOpenIpcPassword.setChecked(prefs.getBoolean("remember_openipc_password", true));
        boolean showRawConfigs = prefs.getBoolean("show_raw_configs", false);
        checkShowRawConfigs.setChecked(showRawConfigs);
        updateRawConfigVisibility(showRawConfigs);
        selectSpinnerValue(spinnerPreferredFirmwareSource, prefs.getString("preferred_firmware_source", "OpenIPC"));
    }

    private void updateRawConfigVisibility(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        rawCameraConfigPanel.setVisibility(visibility);
        rawWfbConfigPanel.setVisibility(visibility);
        rawTelemetryConfigPanel.setVisibility(visibility);
    }

    private void savePreferenceBoolean(String key, boolean value) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value);
        if ("remember_openipc_password".equals(key) && !value) {
            editor.remove("password");
        }
        editor.apply();
        textPreferencesInfo.setText("Preferences are saved automatically.");
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
                String netCard = detectWifiCard(exec(ssh, "lsusb 2>/dev/null || true"));
                String wfbYaml = exec(ssh, "test -f /etc/wfb.yaml && echo 'true' || echo 'false'").trim();
                activeWfbPath = "true".equalsIgnoreCase(wfbYaml) ? REMOTE_WFB_YAML : REMOTE_WFB_CONF;
                telemetryUsesWfbYaml = "true".equalsIgnoreCase(wfbYaml);
                connectedSoc = soc;
                String majestic = downloadText(ssh, REMOTE_MAJESTIC);
                String wfbContent = downloadText(ssh, activeWfbPath);
                String telemetryContent = telemetryUsesWfbYaml ? wfbContent : downloadText(ssh, REMOTE_TELEMETRY);
                String alinkInstalled = exec(ssh, "[ -f /usr/bin/alink_drone ] && echo 'INSTALLED' || echo 'NOT_INSTALLED'").trim();
                String alinkOnBoot = exec(ssh, ALINK_STATUS).trim();
                String alinkProcessCount = exec(ssh, "ps | grep alink_drone | grep -v grep | wc -l").trim();
                String alinkConfig = exec(ssh, "cat /etc/alink.conf 2>/dev/null || echo 'Configuration not found'");

                parseMajestic(majestic);
                parseWfbConfig(wfbContent);

                mainHandler.post(() -> {
                    resetRawConfigEditing();
                    textDeviceInfo.setText(emptyFallback(soc)
                            + "\n" + emptyFallback(sensor)
                            + "\n" + emptyFallback(netCard));
                    textMajesticPreview.setText(majestic);
                    editWfbConfig.setText(wfbContent);
                    editTelemetryConfig.setText(telemetryContent);
                    fillMajesticFields();
                    fillWfbFields();
                    fillTelemetryFields(telemetryContent);
                    checkAlinkDroneOnBoot.setChecked("true".equalsIgnoreCase(alinkOnBoot));
                    boolean alinkRunning = parseIntOrDefault(alinkProcessCount, 0) > 0;
                    textAdaptiveLinkStatus.setText("INSTALLED".equals(alinkInstalled)
                            ? "Adaptive Link is installed and " + (alinkRunning ? "running" : "not running") + ".\n\nConfiguration:\n" + alinkConfig.trim()
                            : "Adaptive Link is not installed on this device.");
                    btnSaveMajestic.setEnabled(true);
                    updateSetupStage(true);
                    setBusy(false, "Connected. Refreshed OpenIPC tabs.");
                    loadFirmwareBySocChoices();
                });
            } catch (Exception e) {
                postError("Connect/read failed: " + e.getMessage());
            }
        });
    }

    private void readMajesticConfig() {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }
        setBusy(true, "Reading camera config...");
        executor.execute(() -> {
            try {
                String majestic = downloadText(config, REMOTE_MAJESTIC);
                parseMajestic(majestic);
                mainHandler.post(() -> {
                    rawCameraConfigEditing = false;
                    setRawConfigEditing(textMajesticPreview, false);
                    textMajesticPreview.setText(majestic);
                    fillMajesticFields();
                    btnSaveMajestic.setEnabled(true);
                    setBusy(false, "Read " + REMOTE_MAJESTIC);
                });
            } catch (Exception e) {
                postError("Read camera config failed: " + e.getMessage());
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
                    rawWfbConfigEditing = false;
                    setRawConfigEditing(editWfbConfig, false);
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
        String content;
        try {
            content = buildWfbContentForSave();
        } catch (Exception e) {
            Toast.makeText(this, "Invalid WFB config: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        setBusy(true, "Saving WFB config...");
        executor.execute(() -> {
            try {
                uploadText(config, activeWfbPath, content);
                exec(config, WFB_RESTART);
                mainHandler.post(() -> {
                    rawWfbConfigEditing = false;
                    setRawConfigEditing(editWfbConfig, false);
                    editWfbConfig.setText(content);
                    fillWfbFields();
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
                    rawTelemetryConfigEditing = false;
                    setRawConfigEditing(editTelemetryConfig, false);
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
        String content;
        try {
            content = buildTelemetryContentForSave();
        } catch (Exception e) {
            Toast.makeText(this, "Invalid telemetry config: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
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
                    rawTelemetryConfigEditing = false;
                    setRawConfigEditing(editTelemetryConfig, false);
                    editTelemetryConfig.setText(content);
                    fillTelemetryFields(content);
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

    private void createFirmwareBackup() {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }

        setBusy(true, "Creating firmware backup...");
        textFirmwareOutput.setText("Stopping services on device...\n");
        setFirmwareBackupProgress(5, "Stopping services on device...");
        executor.execute(() -> {
            try (SSHClient ssh = openSsh(config)) {
                String soc = exec(ssh, "fw_printenv soc 2>/dev/null | awk -F= '{print $2}'").trim();
                if (TextUtils.isEmpty(soc)) {
                    soc = "unknown-soc";
                }
                String stamp = String.valueOf(System.currentTimeMillis());
                String backupName = "backup-" + soc.replaceAll("[^a-zA-Z0-9._-]", "-") + "-" + stamp;
                String remoteBase = "/tmp/mtd-backup";
                String remoteDir = remoteBase + "/" + backupName;
                String remoteArchive = "/tmp/" + backupName + ".tar.gz";

                exec(ssh, "killall -q -3 majestic; killall -q wfb_tx; killall -q wfb_rx; killall -q msposd; killall -q telemetry_rx; killall -q telemetry_tx; sleep 1; sync; echo 3 > /proc/sys/vm/drop_caches; true", 60);
                setFirmwareBackupProgress(8, "Preparing backup directory on device...");
                exec(ssh, "rm -rf " + shellQuote(remoteBase) + " " + shellQuote(remoteArchive) + " && mkdir -p " + shellQuote(remoteDir), 60);
                setFirmwareBackupProgress(12, "Backing up all MTD partitions on device...");
                exec(ssh, "for mtd in $(ls /dev/mtdblock*); do dd if=${mtd} of=" + shellQuote(remoteDir) + "/${mtd##/*/}.bin; done", 300);
                setFirmwareBackupProgress(70, "Syncing and generating checksum file on device...");
                exec(ssh, "sync && cd " + shellQuote(remoteDir) + " && md5sum mtdblock*.bin > md5sums.txt", 120);
                setFirmwareBackupProgress(82, "Creating backup archive on device...");
                exec(ssh, "tar -cf - -C " + shellQuote(remoteBase) + " " + shellQuote(backupName) + " | gzip > " + shellQuote(remoteArchive), 300);

                File target = new File(getCacheDir(), backupName + ".tar.gz");
                setFirmwareBackupProgress(92, "Downloading backup archive...");
                ssh.newSCPFileTransfer().download(remoteArchive, target.getAbsolutePath());
                exec(ssh, "rm -rf " + shellQuote(remoteBase) + " " + shellQuote(remoteArchive));

                mainHandler.post(() -> {
                    editFirmwareRestorePath.setText(target.getAbsolutePath());
                    progressFirmwareBackup.setProgress(100);
                    textFirmwareBackupProgress.setText("Backup saved to " + target.getAbsolutePath());
                    textFirmwareOutput.setText("Backup saved to:\n" + target.getAbsolutePath());
                    setBusy(false, "Firmware backup complete.");
                });
            } catch (Exception e) {
                postError("Firmware backup failed: " + e.getMessage());
            }
        });
    }

    private void restoreFirmwareBackup() {
        if (!checkFirmwareRestoreConfirm.isChecked()) {
            Toast.makeText(this, "Confirm restore first", Toast.LENGTH_SHORT).show();
            return;
        }
        String localPath = editFirmwareRestorePath.getText().toString().trim();
        if (TextUtils.isEmpty(localPath)) {
            Toast.makeText(this, "Backup path is required", Toast.LENGTH_SHORT).show();
            return;
        }
        File archive = new File(localPath);
        if (!archive.isFile()) {
            Toast.makeText(this, "Backup file not found", Toast.LENGTH_SHORT).show();
            return;
        }
        confirmDanger("Restore firmware?", "This will overwrite all MTD flash partitions on the device with data from:\n"
                + archive.getName()
                + "\n\nThis is irreversible. A power failure mid-flash can permanently brick the device. Continue?", () -> restoreFirmwareBackupConfirmed(archive));
    }

    private void restoreFirmwareBackupConfirmed(File archive) {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }
        setBusy(true, "Restoring firmware backup...");
        textFirmwareOutput.setText("Extracting backup archive...\n");
        setFirmwareRestoreProgress(2, "Extracting backup archive...");
        executor.execute(() -> {
            try (SSHClient ssh = openSsh(config)) {
                String remoteArchive = "/tmp/" + archive.getName();
                setFirmwareRestoreProgress(15, "Uploading " + archive.getName() + "...");
                ssh.newSCPFileTransfer().upload(archive.getAbsolutePath(), remoteArchive);
                setFirmwareRestoreProgress(50, "Verifying checksums...");
                String command = "rm -rf /tmp/mtd-restore && mkdir -p /tmp/mtd-restore"
                        + " && tar -xzf " + shellQuote(remoteArchive) + " -C /tmp/mtd-restore"
                        + " && backupdir=$(find /tmp/mtd-restore -mindepth 1 -maxdepth 1 -type d | head -n1)"
                        + " && cd \"$backupdir\""
                        + " && md5sum -c md5sums.txt"
                        + " && for f in mtdblock*.bin; do idx=${f#mtdblock}; idx=${idx%.bin}; [ -e /dev/mtd${idx} ] && flashcp -v \"$f\" /dev/mtd${idx}; done"
                        + " && sync && reboot";
                setFirmwareRestoreProgress(75, "Flashing backup partitions...");
                String output = exec(ssh, command, 600);
                mainHandler.post(() -> {
                    progressFirmwareRestore.setProgress(100);
                    textFirmwareRestoreProgress.setText("Restore complete. Device is rebooting.");
                    textFirmwareOutput.setText(output + "\nRestore command completed. Device should reboot.");
                    setBusy(false, "Restore command completed.");
                });
            } catch (Exception e) {
                postError("Firmware restore failed: " + e.getMessage());
            }
        });
    }

    private void replaceFirmwareBootloader() {
        if (!checkFirmwareBootloaderConfirm.isChecked()) {
            Toast.makeText(this, "Confirm bootloader first", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = editFirmwareBootloaderUrl.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, "Bootloader URL is required", Toast.LENGTH_SHORT).show();
            return;
        }
        confirmDanger("Replace bootloader?", "This will flash '" + url + "' to /dev/mtd0 and erase /dev/mtd1. Continue?", () -> replaceFirmwareBootloaderConfirmed(url));
    }

    private void replaceFirmwareBootloaderConfirmed(String url) {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }
        setBusy(true, "Replacing bootloader...");
        textFirmwareOutput.setText("Downloading bootloader...\n");
        executor.execute(() -> {
            try {
                String remotePath = "/tmp/android-bootloader.bin";
                String command = remoteDownloadCommand(url, remotePath)
                        + " && size=$(wc -c < " + shellQuote(remotePath) + ")"
                        + " && limit=$(cat /sys/class/mtd/mtd0/size 2>/dev/null || echo 0)"
                        + " && [ \"$limit\" = 0 -o \"$size\" -le \"$limit\" ]"
                        + " && flashcp -v " + shellQuote(remotePath) + " /dev/mtd0"
                        + " && flash_eraseall /dev/mtd1"
                        + " && sync && reboot";
                String output = exec(config, command, 600);
                mainHandler.post(() -> {
                    textFirmwareOutput.setText(output + "\nBootloader command completed. Device should reboot.");
                    setBusy(false, "Bootloader command completed.");
                });
            } catch (Exception e) {
                postError("Bootloader replace failed: " + e.getMessage());
            }
        });
    }

    private void performFirmwareSysupgrade() {
        if (checkFirmwareMethodAutomatic.isChecked()) {
            String firmwareName = selectedAutomaticFirmwarePackage();
            if (TextUtils.isEmpty(firmwareName)) {
                Toast.makeText(this, "Select firmware is required", Toast.LENGTH_SHORT).show();
                return;
            }
            confirmDanger("Start firmware update?", "Starting sysupgrade. Do not unplug the device.", () -> performFirmwareSysupgradeFromUrl(buildFirmwareDownloadUrl(firmwareName)));
            return;
        }
        if (checkFirmwareMethodBySoc.isChecked()) {
            String firmwareName = spinnerValue(spinnerFirmwareBySoc);
            if (TextUtils.isEmpty(firmwareName) || !firmwareName.endsWith(".tgz")) {
                Toast.makeText(this, "Select firmware is required", Toast.LENGTH_SHORT).show();
                return;
            }
            confirmDanger("Start firmware update?", "Starting sysupgrade. Do not unplug the device.", () -> performFirmwareSysupgradeFromUrl(buildFirmwareDownloadUrl(firmwareName)));
            return;
        }
        String packagePath = editFirmwareLocalPackage.getText().toString().trim();
        if (TextUtils.isEmpty(packagePath)) {
            Toast.makeText(this, "Local firmware package is required", Toast.LENGTH_SHORT).show();
            return;
        }
        File firmwarePackage = new File(packagePath);
        if (!firmwarePackage.isFile()) {
            Toast.makeText(this, "Local firmware package not found", Toast.LENGTH_SHORT).show();
            return;
        }
        confirmDanger("Start firmware update?", "Starting sysupgrade. Do not unplug the device.", () -> performFirmwareSysupgradeConfirmed(firmwarePackage));
    }

    private String selectedAutomaticFirmwarePackage() {
        Map<String, Map<String, String>> devices = automaticFirmwareChoices.get(firmwareManufacturerId(spinnerValue(spinnerFirmwareManufacturer)));
        if (devices == null) {
            return "";
        }
        Map<String, String> packages = devices.get(firmwareDeviceId(spinnerValue(spinnerFirmwareDevice)));
        if (packages == null) {
            return "";
        }
        return packages.get(firmwareTypeId(spinnerValue(spinnerFirmwareWfbRuby)));
    }

    private void performFirmwareSysupgradeFromUrl(String url) {
        setBusy(true, "Downloading firmware...");
        executor.execute(() -> {
            String name = url.substring(url.lastIndexOf('/') + 1);
            if (TextUtils.isEmpty(name)) {
                name = "firmware-package-" + System.currentTimeMillis() + ".tgz";
            }
            File target = new File(getCacheDir(), name.replaceAll("[^a-zA-Z0-9._-]", "-"));
            try (InputStream inputStream = new URL(url).openStream();
                 FileOutputStream outputStream = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                mainHandler.post(() -> {
                    editFirmwareLocalPackage.setText(target.getAbsolutePath());
                    performFirmwareSysupgradeConfirmed(target);
                });
            } catch (Exception e) {
                postError("Download firmware failed: " + e.getMessage());
            }
        });
    }

    private String buildFirmwareDownloadUrl(String firmwareName) {
        if ("Greg APFPV".equals(spinnerValue(spinnerFirmwareSource))) {
            return "https://raw.githubusercontent.com/sickgreg/OpenIPC_sickgregFPV_apfpv/main/" + firmwareName;
        }
        return "https://github.com/OpenIPC/builder/releases/download/latest/" + firmwareName;
    }

    private void performFirmwareSysupgradeConfirmed(File firmwarePackage) {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }
        setBusy(true, "Starting sysupgrade...");
        textFirmwareOutput.setText("Extracting firmware...\n");
        progressFirmwareUpdate.setProgress(20);
        executor.execute(() -> {
            try (SSHClient ssh = openSsh(config)) {
                String remotePackage = "/tmp/" + firmwarePackage.getName().replaceAll("[^a-zA-Z0-9._-]", "-");
                ssh.newSCPFileTransfer().upload(firmwarePackage.getAbsolutePath(), remotePackage);
                mainHandler.post(() -> progressFirmwareUpdate.setProgress(30));
                String command = "rm -rf /tmp/android-fw && mkdir -p /tmp/android-fw"
                        + " && tar -xzf " + shellQuote(remotePackage) + " -C /tmp/android-fw"
                        + " && kernel=$(find /tmp/android-fw -type f -name 'uImage*' ! -name '*.md5sum' | head -n1)"
                        + " && rootfs=$(find /tmp/android-fw -type f -name '*rootfs*' ! -name '*.md5sum' | head -n1)"
                        + " && test -n \"$kernel\" && test -n \"$rootfs\""
                        + " && nohup sysupgrade --force_ver -n -z --kernel=\"$kernel\" --rootfs=\"$rootfs\""
                        + " >/tmp/android-sysupgrade.log 2>&1 & echo 'sysupgrade started; monitor /tmp/android-sysupgrade.log until reboot'";
                String output = exec(ssh, command, 300);
                mainHandler.post(() -> {
                    progressFirmwareUpdate.setProgress(40);
                    textFirmwareOutput.setText(output + "\nDevice should disconnect and reboot if sysupgrade started successfully.");
                    setBusy(false, "Sysupgrade started.");
                });
            } catch (Exception e) {
                postError("Firmware update failed: " + e.getMessage());
            }
        });
    }

    private void selectLocalFirmwarePackage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        firmwarePackagePicker.launch(intent);
    }

    private void cacheSelectedFirmwarePackage(Uri uri) {
        setBusy(true, "Copying firmware package...");
        executor.execute(() -> {
            String name = "firmware-package-" + System.currentTimeMillis() + ".tar.gz";
            File target = new File(getCacheDir(), name);
            try (InputStream inputStream = getContentResolver().openInputStream(uri);
                 FileOutputStream outputStream = new FileOutputStream(target)) {
                if (inputStream == null) {
                    throw new IOException("Unable to open selected file");
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                mainHandler.post(() -> {
                    editFirmwareLocalPackage.setText(target.getAbsolutePath());
                    selectFirmwareMethod(checkFirmwareMethodLocal);
                    setBusy(false, "Firmware package selected.");
                });
            } catch (Exception e) {
                postError("Select firmware package failed: " + e.getMessage());
            }
        });
    }

    private void clearFirmwareForm() {
        editFirmwareLocalPackage.setText("");
        editFirmwareBootloaderUrl.setText("");
        checkFirmwareMethodAutomatic.setChecked(true);
        checkFirmwareMethodBySoc.setChecked(false);
        checkFirmwareMethodLocal.setChecked(false);
        updateFirmwareMethodPanels();
        checkFirmwareBootloaderConfirm.setChecked(false);
        checkFirmwareRestoreConfirm.setChecked(false);
        btnFirmwareBootloader.setEnabled(false);
        btnFirmwareRestore.setEnabled(false);
        progressFirmwareBackup.setProgress(0);
        progressFirmwareRestore.setProgress(0);
        progressFirmwareUpdate.setProgress(0);
        textFirmwareBackupProgress.setText("");
        textFirmwareRestoreProgress.setText("");
        textFirmwareOutput.setText("");
    }

    private void setFirmwareBackupProgress(int progress, String text) {
        mainHandler.post(() -> {
            progressFirmwareBackup.setProgress(progress);
            textFirmwareBackupProgress.setText(text);
        });
    }

    private void setFirmwareRestoreProgress(int progress, String text) {
        mainHandler.post(() -> {
            progressFirmwareRestore.setProgress(progress);
            textFirmwareRestoreProgress.setText(text);
        });
    }

    private void selectFirmwareMethod(CheckBox selected) {
        checkFirmwareMethodAutomatic.setChecked(selected == checkFirmwareMethodAutomatic);
        checkFirmwareMethodBySoc.setChecked(selected == checkFirmwareMethodBySoc);
        checkFirmwareMethodLocal.setChecked(selected == checkFirmwareMethodLocal);
        updateFirmwareMethodPanels();
    }

    private void updateFirmwareMethodPanels() {
        boolean automatic = checkFirmwareMethodAutomatic.isChecked();
        boolean bySoc = checkFirmwareMethodBySoc.isChecked();
        boolean local = checkFirmwareMethodLocal.isChecked();
        cardFirmwareMethodAutomatic.setSelected(automatic);
        cardFirmwareMethodBySoc.setSelected(bySoc);
        cardFirmwareMethodLocal.setSelected(local);
        panelFirmwareAutomatic.setVisibility(automatic ? View.VISIBLE : View.GONE);
        panelFirmwareBySoc.setVisibility(bySoc ? View.VISIBLE : View.GONE);
        panelFirmwareLocal.setVisibility(local ? View.VISIBLE : View.GONE);
    }

    private void confirmDanger(String title, String message, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Continue", (dialog, which) -> action.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String remoteDownloadCommand(String url, String remotePath) {
        String quotedUrl = shellQuote(url);
        String quotedPath = shellQuote(remotePath);
        return "rm -f " + quotedPath
                + " && (wget -O " + quotedPath + " " + quotedUrl
                + " || curl -L -o " + quotedPath + " " + quotedUrl + ")"
                + " && test -s " + quotedPath;
    }

    private void checkAdaptiveLinkStatus() {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            return;
        }
        setBusy(true, "Checking Adaptive Link status...");
        executor.execute(() -> {
            try (SSHClient ssh = openSsh(config)) {
                String installed = exec(ssh, "[ -f /usr/bin/alink_drone ] && echo 'INSTALLED' || echo 'NOT_INSTALLED'").trim();
                String onBoot = exec(ssh, ALINK_STATUS).trim();
                if (!"INSTALLED".equals(installed)) {
                    mainHandler.post(() -> {
                        checkAlinkDroneOnBoot.setChecked("true".equalsIgnoreCase(onBoot));
                        textAdaptiveLinkStatus.setText("Adaptive Link is not installed on this device.");
                        setBusy(false, "Adaptive Link is not installed.");
                    });
                    return;
                }

                String processCountText = exec(ssh, "ps | grep alink_drone | grep -v grep | wc -l").trim();
                boolean running = parseIntOrDefault(processCountText, 0) > 0;
                String alinkConfig = exec(ssh, "cat /etc/alink.conf 2>/dev/null || echo 'Configuration not found'");
                String status = "Adaptive Link is installed and " + (running ? "running" : "not running")
                        + ".\n\nConfiguration:\n" + alinkConfig.trim();
                mainHandler.post(() -> {
                    checkAlinkDroneOnBoot.setChecked("true".equalsIgnoreCase(onBoot));
                    textAdaptiveLinkStatus.setText(status);
                    setBusy(false, "Adaptive Link status checked.");
                });
            } catch (Exception e) {
                postError("Adaptive Link status failed: " + e.getMessage());
            }
        });
    }

    private void setAlinkDroneOnBoot(boolean enabled) {
        ConnectionConfig config = readConnectionConfig();
        if (config == null) {
            checkAlinkDroneOnBoot.setChecked(!enabled);
            return;
        }
        String command = enabled ? ALINK_ENABLE : ALINK_DISABLE;
        String label = enabled ? "Adaptive Link enabled on boot." : "Adaptive Link disabled on boot.";
        setBusy(true, label);
        executor.execute(() -> {
            try {
                exec(config, command);
                mainHandler.post(() -> {
                    textAdaptiveLinkStatus.setText(label);
                    setBusy(false, label);
                });
            } catch (Exception e) {
                mainHandler.post(() -> checkAlinkDroneOnBoot.setChecked(!enabled));
                postError("Toggle Adaptive Link failed: " + e.getMessage());
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

    private void scanSetupNetwork() {
        String[] hosts = buildScanTargets(editSetupScanSubnet.getText().toString());
        if (hosts.length == 0) {
            Toast.makeText(this, "Enter a valid prefix like 192.168., 192.168.1., or a full IP", Toast.LENGTH_LONG).show();
            return;
        }
        int port = parseIntOrDefault(editPort.getText().toString().trim(), 22);
        cancelSetupScan = false;
        setBusy(true, "Scanning " + hosts.length + " addresses...");
        textSetupScanResults.setText("");
        textSetupScanStatus.setText("Scanning " + hosts.length + " addresses on SSH port " + port + "...");
        executor.execute(() -> {
            StringBuilder result = new StringBuilder();
            for (String candidate : hosts) {
                if (cancelSetupScan) {
                    break;
                }
                if (isTcpPortOpen(candidate, port, 120)) {
                    result.append(candidate).append('\n');
                }
            }
            boolean canceled = cancelSetupScan;
            String output = result.length() == 0 ? "No hosts found." : result.toString().trim();
            mainHandler.post(() -> {
                textSetupScanResults.setText(output);
                textSetupScanStatus.setText(canceled ? "Scan canceled" : "Scan completed");
                setBusy(false, canceled ? "Scan canceled." : "Scan complete.");
            });
        });
    }

    private String[] buildScanTargets(String input) {
        if (TextUtils.isEmpty(input)) {
            return new String[0];
        }
        String trimmed = input.trim();
        while (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String[] parts = trimmed.split("\\.");
        if (parts.length < 2 || parts.length > 4) {
            return new String[0];
        }
        for (String part : parts) {
            int octet = parseIntOrDefault(part, -1);
            if (octet < 0 || octet > 255) {
                return new String[0];
            }
        }
        if (parts.length == 4) {
            return new String[]{trimmed};
        }
        if (parts.length == 3) {
            String prefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
            String[] targets = new String[254];
            for (int i = 1; i <= 254; i++) {
                targets[i - 1] = prefix + i;
            }
            return targets;
        }
        String[] targets = new String[256 * 254];
        int index = 0;
        String prefix = parts[0] + "." + parts[1] + ".";
        for (int third = 0; third <= 255; third++) {
            for (int fourth = 1; fourth <= 254; fourth++) {
                targets[index++] = prefix + third + "." + fourth;
            }
        }
        return targets;
    }

    private String defaultScanPrefix() {
        String localIp = getLocalIpv4Address();
        String source = TextUtils.isEmpty(localIp) ? editHost.getText().toString().trim() : localIp;
        int lastDot = source.lastIndexOf('.');
        if (lastDot > 0) {
            return source.substring(0, lastDot + 1);
        }
        return "192.168.1.";
    }

    private void updateSetupScannerDefaults() {
        String localIp = getLocalIpv4Address();
        textSetupLocalIp.setText(TextUtils.isEmpty(localIp) ? "Local IP: Unknown" : "Local IP: " + localIp);
        editSetupScanSubnet.setText(defaultScanPrefix());
    }

    private String getLocalIpv4Address() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        String value = address.getHostAddress();
                        if (!value.startsWith("169.254.")) {
                            return value;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private boolean isTcpPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException ignored) {
            return false;
        }
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
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove("host")
                .remove("port")
                .remove("username")
                .remove("password")
                .apply();
        loadConnectionSettings();
        textPreferencesInfo.setText("Saved OpenIPC connection settings cleared.");
    }

    private void saveAndRestartMajestic() {
        ConnectionConfig config = readConnectionConfig();
        if (config == null || majesticConfig == null) {
            return;
        }

        String yamlText;
        if (rawCameraConfigEditing) {
            yamlText = textMajesticPreview.getText().toString();
            try {
                parseMajestic(yamlText);
            } catch (Exception e) {
                Toast.makeText(this, "Invalid majestic YAML: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
        } else {
            updateMajesticFromFields();
            yamlText = dumpYaml(majesticConfig);
        }
        setBusy(true, "Uploading majestic.yaml...");

        executor.execute(() -> {
            try {
                uploadText(config, REMOTE_MAJESTIC, yamlText);
                exec(config, MAJESTIC_RESTART);
                mainHandler.post(() -> {
                    rawCameraConfigEditing = false;
                    setRawConfigEditing(textMajesticPreview, false);
                    textMajesticPreview.setText(yamlText);
                    fillMajesticFields();
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

    private String exec(ConnectionConfig config, String command, int timeoutSeconds) throws IOException {
        try (SSHClient ssh = openSsh(config)) {
            return exec(ssh, command, timeoutSeconds);
        }
    }

    private String exec(SSHClient ssh, String command) throws IOException {
        return exec(ssh, command, 15);
    }

    private String exec(SSHClient ssh, String command, int timeoutSeconds) throws IOException {
        try (Session session = ssh.startSession()) {
            Session.Command cmd = session.exec(command);
            String output = readStream(cmd.getInputStream());
            String error = readStream(cmd.getErrorStream());
            cmd.join(timeoutSeconds, TimeUnit.SECONDS);
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
        if (rawWfbConfigEditing) {
            String content = editWfbConfig.getText().toString();
            parseWfbConfig(content);
            return content;
        }
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
        if (rawTelemetryConfigEditing) {
            String content = editTelemetryConfig.getText().toString();
            if (telemetryUsesWfbYaml) {
                parseWfbConfig(content);
            }
            return content;
        }
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
        appendOpenIpcLog(message);
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

    private void appendOpenIpcLog(String message) {
        executor.execute(() -> {
            try {
                rotateOpenIpcLogsIfNeeded();
                File logFile = openIpcLogFile(0);
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
                String line = timestamp + " - " + message + "\n";
                try (FileOutputStream outputStream = new FileOutputStream(logFile, true)) {
                    outputStream.write(line.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void rotateOpenIpcLogsIfNeeded() {
        File current = openIpcLogFile(0);
        if (!current.exists() || current.length() < OPENIPC_LOG_MAX_BYTES) {
            return;
        }
        File oldest = openIpcLogFile(OPENIPC_LOG_MAX_FILES - 1);
        if (oldest.exists()) {
            oldest.delete();
        }
        for (int i = OPENIPC_LOG_MAX_FILES - 2; i >= 0; i--) {
            File source = openIpcLogFile(i);
            if (source.exists()) {
                source.renameTo(openIpcLogFile(i + 1));
            }
        }
    }

    private File openIpcLogFile(int index) {
        String name = index == 0 ? OPENIPC_LOG_PREFIX + ".log" : OPENIPC_LOG_PREFIX + "." + index + ".log";
        return new File(getFilesDir(), name);
    }

    private void showOpenIpcLogs() {
        executor.execute(() -> {
            String logs = readOpenIpcLogs();
            mainHandler.post(() -> {
                LinearLayout container = new LinearLayout(this);
                container.setOrientation(LinearLayout.VERTICAL);
                container.setBackgroundResource(R.drawable.openipc_log_dialog_bg);
                container.setPadding(18, 16, 18, 16);

                TextView title = new TextView(this);
                title.setText("OpenIPC Logs");
                title.setTextColor(getColor(R.color.openipc_text));
                title.setTextSize(18);
                title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                container.addView(title, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView subtitle = new TextView(this);
                subtitle.setText("Last 5 files, 10 MB each. Tap and hold text to copy.");
                subtitle.setTextColor(getColor(R.color.openipc_text_muted));
                subtitle.setTextSize(12);
                LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                subtitleParams.setMargins(0, 4, 0, 12);
                container.addView(subtitle, subtitleParams);

                TextView textView = new TextView(this);
                textView.setText(TextUtils.isEmpty(logs) ? "No OpenIPC logs yet." : logs);
                textView.setTextColor(getColor(R.color.openipc_text));
                textView.setTextSize(12);
                textView.setTypeface(Typeface.MONOSPACE);
                textView.setTextIsSelectable(true);
                textView.setPadding(12, 10, 12, 10);
                textView.setBackgroundResource(R.drawable.openipc_log_area);

                ScrollView scrollView = new ScrollView(this);
                scrollView.setFillViewport(false);
                scrollView.addView(textView);
                LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) (getResources().getDisplayMetrics().heightPixels * 0.55f));
                container.addView(scrollView, scrollParams);

                LinearLayout buttons = new LinearLayout(this);
                buttons.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
                buttons.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                buttonsParams.setMargins(0, 14, 0, 0);
                container.addView(buttons, buttonsParams);

                Button clearButton = new Button(this);
                clearButton.setText("Clear");
                clearButton.setTextColor(getColor(R.color.openipc_text_muted));
                clearButton.setTextSize(11);
                clearButton.setAllCaps(false);
                clearButton.setBackgroundResource(R.drawable.openipc_secondary_button);
                clearButton.setBackgroundTintList(null);
                clearButton.setTextColor(getColor(R.color.openipc_text_muted));
                clearButton.setMinWidth(0);
                clearButton.setMinHeight(0);
                clearButton.setPadding(dp(10), 0, dp(10), 0);
                buttons.addView(clearButton, new LinearLayout.LayoutParams(dp(72), dp(34)));

                Button closeButton = new Button(this);
                closeButton.setText("Close");
                closeButton.setTextColor(getColor(R.color.openipc_button_text));
                closeButton.setTextSize(11);
                closeButton.setAllCaps(false);
                closeButton.setBackgroundResource(R.drawable.openipc_primary_button);
                closeButton.setBackgroundTintList(null);
                closeButton.setTextColor(getColor(R.color.openipc_button_text));
                closeButton.setMinWidth(0);
                closeButton.setMinHeight(0);
                closeButton.setPadding(dp(10), 0, dp(10), 0);
                LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(78), dp(34));
                closeParams.setMargins(dp(8), 0, 0, 0);
                buttons.addView(closeButton, closeParams);

                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setView(container)
                        .create();
                clearButton.setOnClickListener(v -> {
                    clearOpenIpcLogs();
                    dialog.dismiss();
                });
                closeButton.setOnClickListener(v -> dialog.dismiss());
                dialog.show();
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }
            });
        });
    }

    private void clearOpenIpcLogs() {
        executor.execute(() -> {
            for (int i = 0; i < OPENIPC_LOG_MAX_FILES; i++) {
                File file = openIpcLogFile(i);
                if (file.exists()) {
                    file.delete();
                }
            }
            mainHandler.post(() -> Toast.makeText(this, "OpenIPC logs cleared", Toast.LENGTH_SHORT).show());
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String readOpenIpcLogs() {
        StringBuilder builder = new StringBuilder();
        for (int i = OPENIPC_LOG_MAX_FILES - 1; i >= 0; i--) {
            File file = openIpcLogFile(i);
            if (!file.exists()) {
                continue;
            }
            try {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append("--- ").append(file.getName()).append(" ---\n");
                builder.append(readFile(file));
            } catch (Exception ignored) {
            }
        }
        return builder.toString();
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

    private String readUrl(String url) throws IOException {
        try (InputStream inputStream = new URL(url).openStream()) {
            return readStream(inputStream);
        }
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
