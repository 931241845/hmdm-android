/*
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hmdm.launcher.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.github.anrwatchdog.ANRWatchDog;
import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.databinding.ActivityMainBinding;
import com.hmdm.launcher.databinding.DialogAccessibilityServiceBinding;
import com.hmdm.launcher.databinding.DialogAdministratorModeBinding;
import com.hmdm.launcher.databinding.DialogEnterPasswordBinding;
import com.hmdm.launcher.databinding.DialogFileDownloadingFailedBinding;
import com.hmdm.launcher.databinding.DialogHistorySettingsBinding;
import com.hmdm.launcher.databinding.DialogMiuiPermissionsBinding;
import com.hmdm.launcher.databinding.DialogOverlaySettingsBinding;
import com.hmdm.launcher.databinding.DialogPermissionsBinding;
import com.hmdm.launcher.databinding.DialogSystemSettingsBinding;
import com.hmdm.launcher.databinding.DialogUnknownSourcesBinding;
import com.hmdm.launcher.db.DatabaseHelper;
import com.hmdm.launcher.db.RemoteFileTable;
import com.hmdm.launcher.helper.CryptoHelper;
import com.hmdm.launcher.helper.MigrationHelper;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.Application;
import com.hmdm.launcher.json.DeviceInfo;
import com.hmdm.launcher.json.RemoteFile;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.pro.ProUtils;
import com.hmdm.launcher.pro.service.CheckForegroundAppAccessibilityService;
import com.hmdm.launcher.pro.service.CheckForegroundApplicationService;
import com.hmdm.launcher.pro.worker.DetailedInfoWorker;
import com.hmdm.launcher.server.ServerService;
import com.hmdm.launcher.server.ServerServiceKeeper;
import com.hmdm.launcher.server.UnsafeOkHttpClient;
import com.hmdm.launcher.service.LocationService;
import com.hmdm.launcher.service.PluginApiService;
import com.hmdm.launcher.service.StatusControlService;
import com.hmdm.launcher.task.ConfirmDeviceResetTask;
import com.hmdm.launcher.task.ConfirmPasswordResetTask;
import com.hmdm.launcher.task.ConfirmRebootTask;
import com.hmdm.launcher.task.GetRemoteLogConfigTask;
import com.hmdm.launcher.task.GetServerConfigTask;
import com.hmdm.launcher.task.SendDeviceInfoTask;
import com.hmdm.launcher.util.AppInfo;
import com.hmdm.launcher.util.CrashLoopProtection;
import com.hmdm.launcher.util.DeviceInfoProvider;
import com.hmdm.launcher.util.InstallUtils;
import com.hmdm.launcher.util.PushNotificationMqttWrapper;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.SystemUtils;
import com.hmdm.launcher.util.Utils;
import com.hmdm.launcher.worker.PushNotificationWorker;
import com.jakewharton.picasso.OkHttp3Downloader;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.ResponseBody;
import retrofit2.Response;

public class MainActivity
        extends BaseActivity
        implements View.OnLongClickListener, AppListAdapter.OnAppChooseListener, View.OnClickListener {

    private static final int PERMISSIONS_REQUEST = 1000;

    private ActivityMainBinding binding;
    private SettingsHelper settingsHelper;

    private Dialog fileNotDownloadedDialog;
    private DialogFileDownloadingFailedBinding dialogFileDownloadingFailedBinding;

    private Dialog enterPasswordDialog;
    private DialogEnterPasswordBinding dialogEnterPasswordBinding;

    private Dialog overlaySettingsDialog;
    private DialogOverlaySettingsBinding dialogOverlaySettingsBinding;

    private Dialog historySettingsDialog;
    private DialogHistorySettingsBinding dialogHistorySettingsBinding;

    private Dialog miuiPermissionsDialog;
    private DialogMiuiPermissionsBinding dialogMiuiPermissionsBinding;

    private Dialog unknownSourcesDialog;
    private DialogUnknownSourcesBinding dialogUnknownSourcesBinding;

    private Dialog administratorModeDialog;
    private DialogAdministratorModeBinding dialogAdministratorModeBinding;

    private Dialog accessibilityServiceDialog;
    private DialogAccessibilityServiceBinding dialogAccessibilityServiceBinding;

    private Dialog systemSettingsDialog;
    private DialogSystemSettingsBinding dialogSystemSettingsBinding;

    private Dialog permissionsDialog;
    private DialogPermissionsBinding dialogPermissionsBinding;

    private Handler handler = new Handler();
    private View applicationNotAllowed;
    private View lockScreen;

    private SharedPreferences preferences;

    private AppListAdapter appListAdapter;
    private int spanCount;

    private static boolean configInitialized = false;
    private static boolean configInitializing = false;
    private static boolean interruptResumeFlow = false;
    private static List< Application > applicationsForInstall = new LinkedList();
    private static List< Application > applicationsForRun = new LinkedList();
    private static List< RemoteFile > filesForInstall = new LinkedList();
    private static final int BOOT_DURATION_SEC = 120;
    private static final int PAUSE_BETWEEN_AUTORUNS_SEC = 5;
    private static final int SEND_DEVICE_INFO_PERIOD_MINS = 15;
    private static final String WORK_TAG_DEVICEINFO = "com.hmdm.launcher.WORK_TAG_DEVICEINFO";
    private boolean sendDeviceInfoScheduled = false;
    // This flag notifies "download error" dialog what we're downloading: application or file
    // We cannot send this flag as the method parameter because dialog calls MainActivity methods
    private boolean downloadingFile = false;

    private int kioskUnlockCounter = 0;

    private boolean configFault = false;

    private boolean needSendDeviceInfoAfterReconfigure = false;
    private boolean needRedrawContentAfterReconfigure = false;

    private int REQUEST_CODE_GPS_STATE_CHANGE = 1;

    // This flag is used by the broadcast receiver to determine what to do if it gets a policy violation report
    private boolean isBackground;

    private ANRWatchDog anrWatchDog;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive( Context context, Intent intent ) {
            switch ( intent.getAction() ) {
                case Const.ACTION_UPDATE_CONFIGURATION:
                    RemoteLogger.log(context, Const.LOG_DEBUG, "Update configuration");
                    updateConfig(false);
                    break;
                case Const.ACTION_HIDE_SCREEN:
                    ServerConfig serverConfig = SettingsHelper.getInstance(MainActivity.this).getConfig();
                    if (serverConfig.getLock() != null && serverConfig.getLock()) {
                        // Device is locked by the server administrator!
                        showLockScreen();
                    } else if ( applicationNotAllowed != null &&
                            (!ProUtils.kioskModeRequired(MainActivity.this) || !ProUtils.isKioskAppInstalled(MainActivity.this)) ) {
                        TextView textView = ( TextView ) applicationNotAllowed.findViewById( R.id.message );
                        textView.setText( String.format( getString(R.string.access_to_app_denied),
                                intent.getStringExtra( Const.PACKAGE_NAME ) ) );

                        applicationNotAllowed.setVisibility( View.VISIBLE );
                        handler.postDelayed( new Runnable() {
                            @Override
                            public void run() {
                                applicationNotAllowed.setVisibility( View.GONE );
                            }
                        }, 5000 );
                    }
                    break;

                case Const.ACTION_DISABLE_BLOCK_WINDOW:
                    if ( applicationNotAllowed != null) {
                        applicationNotAllowed.setVisibility(View.GONE);
                    }
                    break;

                case Const.ACTION_EXIT:
                    finish();
                    break;

                case Const.ACTION_POLICY_VIOLATION:
                    if (isBackground) {
                        // If we're in the background, let's bring Headwind MDM to top and the notification will be raised in onResume
                        Intent restoreLauncherIntent = new Intent(context, MainActivity.class);
                        restoreLauncherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(restoreLauncherIntent);
                    } else {
                        // Calling startActivity always calls onPause / onResume which is not what we want
                        // So just show dialog if it isn't already shown
                        if (systemSettingsDialog == null || !systemSettingsDialog.isShowing()) {
                            notifyPolicyViolation(intent.getIntExtra(Const.POLICY_VIOLATION_CAUSE, 0));
                        }
                    }
                    break;
            }

        }
    };

    private final BroadcastReceiver stateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                checkSystemSettings(settingsHelper.getConfig());
            } catch (Exception e) {
            }
        }
    };

    private ImageView exitView;
    private ImageView infoView;
    private ImageView updateView;

    private View statusBarView;
    private View rightToolbarView;

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );

        if (CrashLoopProtection.isCrashLoopDetected(this)) {
            Toast.makeText(MainActivity.this, R.string.fault_loop_detected, Toast.LENGTH_LONG).show();
            return;
        }

        // Disable crashes to avoid "select a launcher" popup
        // Crashlytics will show an exception anyway!
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                e.printStackTrace();

                ProUtils.sendExceptionToCrashlytics(e);

                CrashLoopProtection.registerFault(MainActivity.this);
                // Restart launcher if there's a launcher restarter (and we're not in a crash loop)
                if (!CrashLoopProtection.isCrashLoopDetected(MainActivity.this)) {
                    Intent intent = getPackageManager().getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID);
                    if (intent != null) {
                        startActivity(intent);
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    finishAffinity();
                }
                System.exit(0);
            }
        });

        // Crashlytics is not included in the open-source version
        ProUtils.initCrashlytics(this);

        if (BuildConfig.ANR_WATCHDOG) {
            anrWatchDog = new ANRWatchDog();
            anrWatchDog.start();
        }

        if (BuildConfig.TRUST_ANY_CERTIFICATE) {
            InstallUtils.initUnsafeTrustManager();
        }

        Utils.lockSafeBoot(this);
        Utils.initPasswordReset(this);

        DetailedInfoWorker.schedule(MainActivity.this);
        if (BuildConfig.ENABLE_PUSH) {
            PushNotificationWorker.schedule(MainActivity.this);
        }

        // Prevent showing the lock screen during the app download/installation
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        binding = DataBindingUtil.setContentView( this, R.layout.activity_main );
        binding.setMessage( getString( R.string.main_start_preparations ) );
        binding.setLoading( true );

        settingsHelper = SettingsHelper.getInstance( this );
        preferences = getSharedPreferences( Const.PREFERENCES, MODE_PRIVATE );

        // Try to start services in onCreate(), this may fail, we will try again on each onResume.
        startServicesWithRetry();

        initReceiver();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(stateChangeReceiver, intentFilter);

        if (!getIntent().getBooleanExtra(Const.RESTORED_ACTIVITY, false)) {
            startAppsAtBoot();
        }
    }

    @Override
    protected void onSaveInstanceState( Bundle outState ) {
        int orientation = getResources().getConfiguration().orientation;
        outState.putInt( Const.ORIENTATION, orientation );

        super.onSaveInstanceState( outState );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_GPS_STATE_CHANGE) {
            // User changed GPS state, let's update location service
            startLocationServiceWithRetry();
        }
    }

    private void initReceiver() {
        IntentFilter intentFilter = new IntentFilter(Const.ACTION_UPDATE_CONFIGURATION);
        intentFilter.addAction(Const.ACTION_HIDE_SCREEN);
        intentFilter.addAction(Const.ACTION_EXIT);
        intentFilter.addAction(Const.ACTION_POLICY_VIOLATION);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, intentFilter);

        // Here we handle the completion of the silent app installation in the device owner mode
        // These intents are not delivered to LocalBroadcastManager
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Const.ACTION_INSTALL_COMPLETE)) {
                    int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0);
                    switch (status) {
                        case PackageInstaller.STATUS_PENDING_USER_ACTION:
                            RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Request user confirmation to install");
                            Intent confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                            confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            try {
                                startActivity(confirmationIntent);
                            } catch (Exception e) {
                            }
                            break;
                        case PackageInstaller.STATUS_SUCCESS:
                            RemoteLogger.log(MainActivity.this, Const.LOG_DEBUG, "App installed successfully");
                            if (Utils.isDeviceOwner(MainActivity.this)){
                                // Always grant all dangerous rights to the app
                                // TODO: in the future, the rights must be configurable on the server
                                String packageName = intent.getStringExtra(Const.PACKAGE_NAME);
                                if (packageName != null) {
                                    Log.i(Const.LOG_TAG, "Install complete: " + packageName);
                                    Utils.autoGrantRequestedPermissions(MainActivity.this, packageName);
                                }
                            }
                            break;
                        default:
                            // Installation failure
                            String extraMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                            String statusMessage = InstallUtils.getPackageInstallerStatusMessage(status);
                            String logRecord = "Install failed: " + statusMessage;
                            if (extraMessage != null && extraMessage.length() > 0) {
                                logRecord += ", extra: " + extraMessage;
                            }
                            RemoteLogger.log(MainActivity.this, Const.LOG_ERROR, logRecord);
                            break;
                    }
                    checkAndStartLauncher();
                }
            }
        }, new IntentFilter(Const.ACTION_INSTALL_COMPLETE));

    }

    @Override
    protected void onResume() {
        super.onResume();

        isBackground = false;

        // Protection against NPE crash (why it could become null?!)
        if (preferences == null) {
            settingsHelper = SettingsHelper.getInstance(this);
            preferences = getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE);
        }

        // Lock orientation of progress activity to avoid hangups on rotation while initial configuration
        setRequestedOrientation(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ?
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        startServicesWithRetry();

        if (interruptResumeFlow) {
            interruptResumeFlow = false;
            return;
        }

        if (!BuildConfig.SYSTEM_PRIVILEGES) {
            checkAndStartLauncher();
        } else {
            setSelfAsDeviceOwner();
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (appListAdapter != null && event.getAction() == KeyEvent.ACTION_UP) {
            return appListAdapter.onKey(keyCode);
        }
        return super.onKeyUp(keyCode, event);
    }

    // Workaround against crash "App is in background" on Android 9: this is an Android OS bug
    // https://stackoverflow.com/questions/52013545/android-9-0-not-allowed-to-start-service-app-is-in-background-after-onresume
    private void startServicesWithRetry() {
        try {
            startServices();
        } catch (Exception e) {
            // Android OS bug!!!
            e.printStackTrace();

            // Repeat an attempt to start services after one second
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    try {
                        startServices();
                    } catch (Exception e) {
                        // Still failed, now give up!
                        // startService may fail after resuming, but the service may be already running (there's a WorkManager)
                        // So if we get an exception here, just ignore it and hope the app will work further
                        e.printStackTrace();
                    }
                }
            }, 1000);
        }
    }

    private void startAppsAtBoot() {
        // Let's assume that we start within two minutes after boot
        // This should work even for slow devices
        long uptimeMillis = SystemClock.uptimeMillis();
        if (uptimeMillis > BOOT_DURATION_SEC * 1000) {
            return;
        }
        final ServerConfig config = settingsHelper.getConfig();
        if (config == null || config.getApplications() == null) {
            // First start
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                boolean appStarted = false;
                for (Application application : config.getApplications()) {
                    if (application.isRunAtBoot()) {
                        // Delay start of each application to 5 sec
                        try {
                            Thread.sleep(PAUSE_BETWEEN_AUTORUNS_SEC * 1000);
                        } catch (InterruptedException e) {
                        }
                        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(application.getPkg());
                        if (launchIntent != null) {
                            startActivity(launchIntent);
                            appStarted = true;
                        }
                    }
                }
                // Hide apps after start to avoid users confusion
                if (appStarted) {
                    try {
                        Thread.sleep(PAUSE_BETWEEN_AUTORUNS_SEC * 1000);
                    } catch (InterruptedException e) {
                    }
                    // Notice: if MainActivity will be destroyed after running multiple apps at startup,
                    // we can get the looping here, because startActivity will create a new instance!
                    // That's why we put a boolean extra preventing apps from start
                    Intent intent = new Intent(MainActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.putExtra(Const.RESTORED_ACTIVITY, true);
                    startActivity(intent);
                }

                return null;
            }
        }.execute();

    }

    // Does not seem to work, though. See the comment to SystemUtils.becomeDeviceOwner()
    private void setSelfAsDeviceOwner() {
        // We set self as device owner only once
        if (preferences.getInt(Const.PREFERENCES_DEVICE_OWNER, -1) != -1) {
            checkAndStartLauncher();
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                SystemUtils.becomeDeviceOwner(MainActivity.this);
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                checkAndStartLauncher();
            }
        }.execute();
    }

    private void startServices() {
        // Foreground apps checks are not available in a free version: services are the stubs
        if (preferences.getInt(Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            startService(new Intent(MainActivity.this, CheckForegroundApplicationService.class));
        }
        if (preferences.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            startService(new Intent(MainActivity.this, CheckForegroundAppAccessibilityService.class));
        }
        startService(new Intent(MainActivity.this, StatusControlService.class));

        // Moved to onResume!
        // https://stackoverflow.com/questions/51863600/java-lang-illegalstateexception-not-allowed-to-start-service-intent-from-activ
        startService(new Intent(MainActivity.this, PluginApiService.class));

        // Send pending logs to server
        RemoteLogger.sendLogsToServer(MainActivity.this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (Utils.isDeviceOwner(this)) {
                // This may be called on Android 10, not sure why; just continue the flow
                Log.i(Const.LOG_TAG, "Called onRequestPermissionsResult: permissions=" + Arrays.toString(permissions) +
                        ", grantResults=" + Arrays.toString(grantResults));
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                return;
            }
            boolean noPermissions = false;
            for (int n = 0; n < permissions.length; n++) {
                if (permissions[n].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    if (grantResults[n] != PackageManager.PERMISSION_GRANTED) {
                        // The user didn't allow to determine location, this is not critical, just ignore it
                        preferences.edit().putInt(Const.PREFERENCES_DISABLE_LOCATION, Const.PREFERENCES_ON).commit();
                    }
                } else {
                    if (grantResults[n] != PackageManager.PERMISSION_GRANTED) {
                        // This is critical, let's user know
                        createAndShowPermissionsDialog();
                    }
                }
            }
        }
    }

    private void checkAndStartLauncher() {

        boolean deviceOwner = Utils.isDeviceOwner(this);
        preferences.edit().putInt(Const.PREFERENCES_DEVICE_OWNER, deviceOwner ?
            Const.PREFERENCES_ON : Const.PREFERENCES_OFF).commit();
        if (deviceOwner) {
            Utils.autoGrantRequestedPermissions(this, getPackageName());
        }

        int miuiPermissionMode = preferences.getInt(Const.PREFERENCES_MIUI_PERMISSIONS, -1);
        if (miuiPermissionMode == -1) {
            preferences.
                    edit().
                    putInt( Const.PREFERENCES_MIUI_PERMISSIONS, Const.PREFERENCES_ON ).
                    commit();
            if (checkMiuiPermissions(Const.MIUI_PERMISSIONS)) {
                // Permissions dialog opened, break the flow!
                return;
            }
        }

        int miuiDeveloperMode = preferences.getInt(Const.PREFERENCES_MIUI_DEVELOPER, -1);
        if (miuiDeveloperMode == -1) {
            preferences.
                    edit().
                    putInt( Const.PREFERENCES_MIUI_DEVELOPER, Const.PREFERENCES_ON ).
                    commit();
            if (checkMiuiPermissions(Const.MIUI_DEVELOPER)) {
                // Permissions dialog opened, break the flow!
                return;
            }
        }

        int miuiOptimizationMode = preferences.getInt(Const.PREFERENCES_MIUI_OPTIMIZATION, -1);
        if (miuiOptimizationMode == -1) {
            preferences.
                    edit().
                    putInt( Const.PREFERENCES_MIUI_OPTIMIZATION, Const.PREFERENCES_ON ).
                    commit();
            if (checkMiuiPermissions(Const.MIUI_OPTIMIZATION)) {
                // Permissions dialog opened, break the flow!
                return;
            }
        }

        int unknownSourceMode = preferences.getInt(Const.PREFERENCES_UNKNOWN_SOURCES, -1);
        if (!deviceOwner && unknownSourceMode == -1) {
            if (checkUnknownSources()) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_UNKNOWN_SOURCES, Const.PREFERENCES_ON ).
                        commit();
            } else {
                return;
            }
        }

        int administratorMode = preferences.getInt( Const.PREFERENCES_ADMINISTRATOR, - 1 );
//        RemoteLogger.log(this, Const.LOG_DEBUG, "Saved device admin state: " + administratorMode);
        if ( administratorMode == -1 ) {
            if (checkAdminMode()) {
                RemoteLogger.log(this, Const.LOG_DEBUG, "Saving device admin state as 1 (TRUE)");
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_ON ).
                        commit();
            } else {
                return;
            }
        }

        int overlayMode = preferences.getInt( Const.PREFERENCES_OVERLAY, - 1 );
        if ( overlayMode == -1 ) {
            if ( checkAlarmWindow() ) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_OVERLAY, Const.PREFERENCES_ON ).
                        commit();
            } else {
                return;
            }
        }

        int usageStatisticsMode = preferences.getInt( Const.PREFERENCES_USAGE_STATISTICS, - 1 );
        if ( usageStatisticsMode == -1 ) {
            if ( checkUsageStatistics() ) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_ON ).
                        commit();

                // If usage statistics is on, there's no need to turn on accessibility services
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF ).
                        commit();
            } else {
                return;
            }
        }

        int accessibilityService = preferences.getInt( Const.PREFERENCES_ACCESSIBILITY_SERVICE, - 1 );
        if ( accessibilityService == -1 ) {
            if ( checkAccessibilityService() ) {
                preferences.
                        edit().
                        putInt( Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_ON ).
                        commit();
            } else {
                createAndShowAccessibilityServiceDialog();
                return;
            }
        }

        if (settingsHelper != null && settingsHelper.getConfig() != null && settingsHelper.getConfig().getLockStatusBar() != null && settingsHelper.getConfig().getLockStatusBar()) {
            // If the admin requested status bar lock (may be required for some early Samsung devices), block the status bar and right bar (App list) expansion
            statusBarView = ProUtils.preventStatusBarExpansion(this);
            rightToolbarView = ProUtils.preventApplicationsList(this);
        }

        createApplicationNotAllowedScreen();
        createLockScreen();
        startLauncher();
    }

    private void createAndShowPermissionsDialog() {
        dismissDialog(permissionsDialog);
        permissionsDialog = new Dialog( this );
        dialogPermissionsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_permissions,
                null,
                false );
        permissionsDialog.setCancelable( false );
        permissionsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        permissionsDialog.setContentView( dialogPermissionsBinding.getRoot() );
        permissionsDialog.show();
    }

    public void permissionsRetryClicked(View view) {
        dismissDialog(permissionsDialog);
        startLauncher();
    }

    public void permissionsExitClicked(View view) {
        dismissDialog(permissionsDialog);
        finish();
    }

    private void createAndShowAccessibilityServiceDialog() {
        dismissDialog(accessibilityServiceDialog);
        accessibilityServiceDialog = new Dialog( this );
        dialogAccessibilityServiceBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_accessibility_service,
                null,
                false );
        accessibilityServiceDialog.setCancelable( false );
        accessibilityServiceDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        accessibilityServiceDialog.setContentView( dialogAccessibilityServiceBinding.getRoot() );
        accessibilityServiceDialog.show();
    }

    public void skipAccessibilityService( View view ) {
        try { accessibilityServiceDialog.dismiss(); }
        catch ( Exception e ) { e.printStackTrace(); }
        accessibilityServiceDialog = null;

        preferences.
                edit().
                putInt( Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF ).
                commit();

        checkAndStartLauncher();
    }

    public void setAccessibilityService( View view ) {
        try { accessibilityServiceDialog.dismiss(); }
        catch ( Exception e ) { e.printStackTrace(); }
        accessibilityServiceDialog = null;

        Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivityForResult(intent, 0);
    }

    // Accessibility services are needed in the Pro-version only
    private boolean checkAccessibilityService() {
        return ProUtils.checkAccessibilityService(this);
    }

    private void createLauncherButtons() {
        createExitButton();
        createInfoButton();
        createUpdateButton();
    }

    private void createButtons() {
        ServerConfig config = settingsHelper.getConfig();
        if (ProUtils.kioskModeRequired(this) && !settingsHelper.getConfig().getMainApp().equals(getPackageName())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !Settings.canDrawOverlays( this )) {
                Toast.makeText(this, R.string.kiosk_mode_requires_overlays, Toast.LENGTH_LONG).show();
                config.setKioskMode(false);
                settingsHelper.updateConfig(config);
                createLauncherButtons();
                return;
            }
            View kioskUnlockButton = ProUtils.createKioskUnlockButton(this);
            kioskUnlockButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    kioskUnlockCounter++;
                    if (kioskUnlockCounter >= Const.KIOSK_UNLOCK_CLICK_COUNT ) {
                        // We are in the main app: let's open launcher activity
                        interruptResumeFlow = true;
                        Intent restoreLauncherIntent = new Intent(MainActivity.this, MainActivity.class);
                        restoreLauncherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(restoreLauncherIntent);
                        createAndShowEnterPasswordDialog();
                        kioskUnlockCounter = 0;
                    }
                }
            });
        } else {
            createLauncherButtons();
        }
    }

    private void startLauncher() {
        createButtons();

        if ( applicationsForInstall.size() > 0 ) {
            loadAndInstallApplications();
        } else if ( !checkPermissions(true)) {
            // Permissions are requested inside checkPermissions, so do nothing here
            Log.i(Const.LOG_TAG, "startLauncher: requesting permissions");
        } else if (!Utils.isDeviceOwner(this) && !settingsHelper.isBaseUrlSet() &&
                (BuildConfig.FLAVOR.equals("master") || BuildConfig.FLAVOR.equals("opensource") || BuildConfig.FLAVOR.equals("whitelabel"))) {
            // For common public version, here's an option to change the server in non-MDM mode
            createAndShowServerDialog(false, settingsHelper.getBaseUrl(), settingsHelper.getServerProject());
        } else if ( settingsHelper.getDeviceId().length() == 0 ) {
            createAndShowEnterDeviceIdDialog( false, null );
        } else if ( ! configInitialized ) {
            Log.i(Const.LOG_TAG, "Updating configuration in startLauncher()");
            if (settingsHelper.getConfig() != null) {
                // If it's not the first start, let's update in the background, show the content first!
                showContent(settingsHelper.getConfig());
            }
            updateConfig( false );
        } else if ( ! configInitializing ) {
            Log.i(Const.LOG_TAG, "Showing content");
            showContent( settingsHelper.getConfig() );
        } else {
            Log.i(Const.LOG_TAG, "Do nothing in startLauncher: configInitializing=true");
        }
    }

    private boolean checkAdminMode() {
        if (!Utils.checkAdminMode(this)) {
            createAndShowAdministratorDialog();
            return false;
        }
        return true;
    }

    // Access to usage statistics is required in the Pro-version only
    private boolean checkUsageStatistics() {
        if (!ProUtils.checkUsageStatistics(this)) {
            createAndShowHistorySettingsDialog();
            return false;
        }
        return true;
    }

    private boolean checkAlarmWindow() {
        if (!Utils.canDrawOverlays(this)) {
            createAndShowOverlaySettingsDialog();
            return false;
        } else {
            return true;
        }
    }

    private boolean checkMiuiPermissions(int screen) {
        // Permissions to open popup from background first appears in MIUI 11 (Android 9)
        // Also a workaround against https://qa.h-mdm.com/3119/
        if (Utils.isMiui(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_ENABLE_SETTINGS));
            createAndShowMiuiPermissionsDialog(screen);
            // It is not known how to check this setting programmatically, so return true
            return true;
        }
        return false;
    }

    private boolean checkUnknownSources() {
        if ( !Utils.canInstallPackages(this) ) {
            createAndShowUnknownSourcesDialog();
            return false;
        } else {
            return true;
        }
    }

    private WindowManager.LayoutParams overlayLockScreenParams() {
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = Utils.OverlayWindowType();
        layoutParams.gravity = Gravity.RIGHT;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        layoutParams.format = PixelFormat.TRANSPARENT;

        return layoutParams;
    }

    private void createApplicationNotAllowedScreen() {
        if ( applicationNotAllowed != null ) {
            return;
        }
        WindowManager manager = ((WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE));

        applicationNotAllowed = LayoutInflater.from( this ).inflate( R.layout.layout_application_not_allowed, null );
        applicationNotAllowed.findViewById( R.id.layout_application_not_allowed_continue ).setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View v ) {
                applicationNotAllowed.setVisibility( View.GONE );
            }
        } );
        applicationNotAllowed.findViewById( R.id.layout_application_not_allowed_admin ).setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View v ) {
                applicationNotAllowed.setVisibility( View.GONE );
                createAndShowEnterPasswordDialog();
            }
        } );

        applicationNotAllowed.setVisibility( View.GONE );

        try {
            manager.addView( applicationNotAllowed, overlayLockScreenParams() );
        } catch ( Exception e ) {
            // No permission to show overlays; let's try to add view to main view
            try {
                RelativeLayout root = findViewById(R.id.activity_main);
                root.addView(applicationNotAllowed);
            } catch ( Exception e1 ) {
                e1.printStackTrace();
            }
        }
    }

    private void createLockScreen() {
        if ( lockScreen != null ) {
            return;
        }

        WindowManager manager = ((WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE));

        // Reuse existing "Application not allowed" screen but hide buttons
        lockScreen = LayoutInflater.from( this ).inflate( R.layout.layout_application_not_allowed, null );
        lockScreen.findViewById( R.id.layout_application_not_allowed_continue ).setVisibility(View.GONE);
        lockScreen.findViewById( R.id.layout_application_not_allowed_admin ).setVisibility(View.GONE);
        TextView textView = lockScreen.findViewById( R.id.message );
        textView.setText(getString(R.string.device_locked));

        lockScreen.setVisibility( View.GONE );

        try {
            manager.addView( lockScreen, overlayLockScreenParams() );
        } catch ( Exception e ) {
            // No permission to show overlays; let's try to add view to main view
            try {
                RelativeLayout root = findViewById(R.id.activity_main);
                root.addView(lockScreen);
            } catch ( Exception e1 ) {
                e1.printStackTrace();
            }
        }
    }

    // This is an overlay button which is not necessary! We need normal buttons in the launcher window.
    /*private ImageView createManageButton(int imageResource, int imageResourceBlack, int offset) {
        WindowManager manager = ((WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE));

        WindowManager.LayoutParams localLayoutParams = new WindowManager.LayoutParams();
        localLayoutParams.type = Utils.OverlayWindowType();
        localLayoutParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        localLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        localLayoutParams.height = getResources().getDimensionPixelOffset( R.dimen.activity_main_exit_button_size );
        localLayoutParams.width = getResources().getDimensionPixelOffset( R.dimen.activity_main_exit_button_size );
        localLayoutParams.format = PixelFormat.TRANSPARENT;
        localLayoutParams.y = offset;

        boolean dark = true;
        try {
            ServerConfig config = settingsHelper.getConfig();
            if (config.getBackgroundColor() != null) {
                int color = Color.parseColor(config.getBackgroundColor());
                dark = !Utils.isLightColor(color);
            }
        } catch (Exception e) {
        }

        ImageView manageButton = new ImageView( this );
        manageButton.setImageResource(dark ? imageResource : imageResourceBlack);

        try {
            manager.addView( manageButton, localLayoutParams );
        } catch ( Exception e ) { e.printStackTrace(); }
        return manageButton;
    } */

    private ImageView createManageButton(int imageResource, int imageResourceBlack, int offset) {
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

        boolean dark = true;
        try {
            ServerConfig config = settingsHelper.getConfig();
            if (config.getBackgroundColor() != null) {
                int color = Color.parseColor(config.getBackgroundColor());
                dark = !Utils.isLightColor(color);
            }
        } catch (Exception e) {
        }

        int offsetRight = 0;
        if (settingsHelper != null && settingsHelper.getConfig() != null && settingsHelper.getConfig().getLockStatusBar() != null && settingsHelper.getConfig().getLockStatusBar()) {
            // If we lock the right bar, let's shift buttons to avoid overlapping
            offsetRight = getResources().getDimensionPixelOffset(R.dimen.prevent_applications_list_width);
        }

        RelativeLayout view = new RelativeLayout(this);
        // Offset is multiplied by 2 because the view is centered. Yeah I know its an Induism)
        view.setPadding(0, offset * 2, offsetRight, 0);
        view.setLayoutParams(layoutParams);

        ImageView manageButton = new ImageView( this );
        manageButton.setImageResource(dark ? imageResource : imageResourceBlack);
        view.addView(manageButton);

        try {
            RelativeLayout root = findViewById(R.id.activity_main);
            root.addView(view);
        } catch ( Exception e ) { e.printStackTrace(); }
        return manageButton;
    }

    private void createExitButton() {
        if ( exitView != null ) {
            return;
        }
        exitView = createManageButton(R.drawable.ic_vpn_key_opaque_24dp, R.drawable.ic_vpn_key_black_24dp, 0);
        exitView.setOnLongClickListener(this);
    }

    private void createInfoButton() {
        if ( infoView != null ) {
            return;
        }
        infoView = createManageButton(R.drawable.ic_info_opaque_24dp, R.drawable.ic_info_black_24dp,
                getResources().getDimensionPixelOffset(R.dimen.info_icon_margin));
        infoView.setOnClickListener(this);
    }

    private void createUpdateButton() {
        if ( updateView != null ) {
            return;
        }
        updateView = createManageButton(R.drawable.ic_system_update_opaque_24dp, R.drawable.ic_system_update_black_24dp,
                (int)(2.05f * getResources().getDimensionPixelOffset(R.dimen.info_icon_margin)));
        updateView.setOnClickListener(this);
    }

    private void updateConfig( final boolean forceShowErrorDialog ) {
        if ( configInitializing ) {
            Log.i(Const.LOG_TAG, "updateConfig(): configInitializing=true, exiting");
            return;
        }

        needSendDeviceInfoAfterReconfigure = true;
        needRedrawContentAfterReconfigure = true;

        Log.i(Const.LOG_TAG, "updateConfig(): set configInitializing=true");
        configInitializing = true;
        DetailedInfoWorker.requestConfigUpdate(this);

        // Work around a strange bug with stale SettingsHelper instance: re-read its value
        settingsHelper = SettingsHelper.getInstance(this);

        binding.setMessage( getString( R.string.main_activity_update_config ) );
        GetServerConfigTask task = new GetServerConfigTask( this ) {
            @Override
            protected void onPostExecute( Integer result ) {
                super.onPostExecute( result );
                configInitializing = false;
                Log.i(Const.LOG_TAG, "updateConfig(): set configInitializing=false after getting config");

                switch ( result ) {
                    case Const.TASK_SUCCESS:
                        updateRemoteLogConfig();
                        break;
                    case Const.TASK_ERROR:
                        if ( enterDeviceIdDialog != null ) {
                            enterDeviceIdDialogBinding.setError( true );
                            enterDeviceIdDialog.show();
                        } else {
                            createAndShowEnterDeviceIdDialog( true, settingsHelper.getDeviceId() );
                        }
                        break;
                    case Const.TASK_NETWORK_ERROR:
                        RemoteLogger.log(MainActivity.this, Const.LOG_WARN, "Failed to update config: network error");
                        if ( settingsHelper.getConfig() != null && !forceShowErrorDialog ) {
                            updateRemoteLogConfig();
                        } else {
                            // Do not show the reset button if the launcher is installed by scanning a QR code
                            // Only show the reset button on manual setup at first start (when config is not yet loaded)
                            createAndShowNetworkErrorDialog(settingsHelper.getBaseUrl(), settingsHelper.getServerProject(),
                                    settingsHelper.getConfig() == null && !settingsHelper.isQrProvisioning());
                        }
                        break;
                }
            }
        };
        task.execute();
    }

    private void updateRemoteLogConfig() {
        Log.i(Const.LOG_TAG, "updateRemoteLogConfig(): get logging configuration");

        GetRemoteLogConfigTask task = new GetRemoteLogConfigTask( this ) {
            @Override
            protected void onPostExecute( Integer result ) {
                super.onPostExecute( result );
                Log.i(Const.LOG_TAG, "updateRemoteLogConfig(): result=" + result);
                RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Device owner: " + Utils.isDeviceOwner(MainActivity.this));
                checkServerMigration();
            }
        };
        task.execute();
    }

    private void checkServerMigration() {
        if (settingsHelper != null && settingsHelper.getConfig() != null && settingsHelper.getConfig().getNewServerUrl() != null &&
                !settingsHelper.getConfig().getNewServerUrl().trim().equals("")) {
            try {
                final MigrationHelper migrationHelper = new MigrationHelper(settingsHelper.getConfig().getNewServerUrl().trim());
                if (migrationHelper.needMigrating(this)) {
                    // Before migration, test that new URL is working well
                    migrationHelper.tryNewServer(this, new MigrationHelper.CompletionHandler() {
                        @Override
                        public void onSuccess() {
                            // Everything is OK, migrate!
                            RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Migrated to " + settingsHelper.getConfig().getNewServerUrl().trim());
                            settingsHelper.setBaseUrl(migrationHelper.getBaseUrl());
                            settingsHelper.setSecondaryBaseUrl(migrationHelper.getBaseUrl());
                            settingsHelper.setServerProject(migrationHelper.getServerProject());
                            ServerServiceKeeper.resetServices();
                            configInitializing = false;
                            updateConfig(false);
                        }

                        @Override
                        public void onError(String cause) {
                            RemoteLogger.log(MainActivity.this, Const.LOG_WARN, "Failed to migrate to " + settingsHelper.getConfig().getNewServerUrl().trim() + ": " + cause);
                            setupPushService();
                        }
                    });
                    return;
                }
            } catch (Exception e) {
                // Malformed URL
                RemoteLogger.log(MainActivity.this, Const.LOG_WARN, "Failed to migrate to " + settingsHelper.getConfig().getNewServerUrl().trim() + ": malformed URL");
            }
        }
        setupPushService();
    }

    private void setupPushService() {
        String pushOptions = null;
        if (settingsHelper != null && settingsHelper.getConfig() != null) {
            pushOptions = settingsHelper.getConfig().getPushOptions();
        }
        if (BuildConfig.ENABLE_PUSH && pushOptions != null && (pushOptions.equals(ServerConfig.PUSH_OPTIONS_MQTT_WORKER)
                || pushOptions.equals(ServerConfig.PUSH_OPTIONS_MQTT_ALARM))) {
            try {
                URL url = new URL(settingsHelper.getBaseUrl());
                Runnable nextRunnable = () -> {
                    checkFactoryReset();
                };
                PushNotificationMqttWrapper.getInstance().connect(this, url.getHost(), BuildConfig.MQTT_PORT,
                        pushOptions, settingsHelper.getDeviceId(), nextRunnable, nextRunnable);
            } catch (Exception e) {
                e.printStackTrace();
                checkFactoryReset();
            }
        } else {
            checkFactoryReset();
        }
    }

    private void checkFactoryReset() {
        ServerConfig config = settingsHelper != null ? settingsHelper.getConfig() : null;
        if (config != null && config.getFactoryReset() != null && config.getFactoryReset()) {
            // We got a factory reset request, let's confirm and erase everything!
            RemoteLogger.log(this, Const.LOG_INFO, "Device reset by server request");
            ConfirmDeviceResetTask confirmTask = new ConfirmDeviceResetTask(this) {
                @Override
                protected void onPostExecute( Integer result ) {
                    // Do a factory reset if we can
                    if (result == null || result != Const.TASK_SUCCESS ) {
                        RemoteLogger.log(MainActivity.this, Const.LOG_WARN, "Failed to confirm device reset on server");
                    } else if (Utils.checkAdminMode(MainActivity.this)) {
                        if (!Utils.factoryReset(MainActivity.this)) {
                            RemoteLogger.log(MainActivity.this, Const.LOG_WARN, "Device reset failed");
                        }
                    } else {
                        RemoteLogger.log(MainActivity.this, Const.LOG_WARN, "Device reset failed: no permissions");
                    }
                    // If we can't, proceed the initialization flow
                    checkRemoteReboot();
                }
            };

            DeviceInfo deviceInfo = DeviceInfoProvider.getDeviceInfo(this, true, true);
            deviceInfo.setFactoryReset(Utils.checkAdminMode(this));
            confirmTask.execute(deviceInfo);

        } else {
            checkRemoteReboot();
        }
    }

    private void checkRemoteReboot() {
        ServerConfig config = settingsHelper != null ? settingsHelper.getConfig() : null;
        if (config != null && config.getReboot() != null && config.getReboot()) {
            // Log and confirm reboot before rebooting
            RemoteLogger.log(this, Const.LOG_INFO, "Rebooting by server request");
            ConfirmRebootTask confirmTask = new ConfirmRebootTask(this) {
                @Override
                protected void onPostExecute( Integer result ) {
                    if (result == null || result != Const.TASK_SUCCESS ) {
                        RemoteLogger.log(MainActivity.this, Const.LOG_WARN, "Failed to confirm reboot on server");
                    } else if (Utils.checkAdminMode(MainActivity.this)) {
                        if (!Utils.reboot(MainActivity.this)) {
                            RemoteLogger.log(MainActivity.this, Const.LOG_WARN, "Reboot failed");
                        }
                    } else {
                        RemoteLogger.log(MainActivity.this, Const.LOG_WARN, "Reboot failed: no permissions");
                    }
                    checkPasswordReset();
                }
            };

            DeviceInfo deviceInfo = DeviceInfoProvider.getDeviceInfo(this, true, true);
            confirmTask.execute(deviceInfo);

        } else {
            checkPasswordReset();
        }

    }

    private void checkPasswordReset() {
        ServerConfig config = settingsHelper != null ? settingsHelper.getConfig() : null;
        if (config != null && config.getPasswordReset() != null) {
            if (Utils.passwordReset(this, config.getPasswordReset())) {
                RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Password successfully changed");
            } else {
                RemoteLogger.log(MainActivity.this, Const.LOG_WARN, "Failed to reset password");
            }

            ConfirmPasswordResetTask confirmTask = new ConfirmPasswordResetTask(this) {
                @Override
                protected void onPostExecute( Integer result ) {
                    setDefaultLauncher();
                }
            };

            DeviceInfo deviceInfo = DeviceInfoProvider.getDeviceInfo(this, true, true);
            confirmTask.execute(deviceInfo);

        } else {
            setDefaultLauncher();
        }

    }

    private void setDefaultLauncher() {
        ServerConfig config = settingsHelper != null ? settingsHelper.getConfig() : null;
        if (Utils.isDeviceOwner(this) && config != null && (config.getRunDefaultLauncher() == null || !config.getRunDefaultLauncher())) {
            String defaultLauncher = Utils.getDefaultLauncher(this);
            if (!getPackageName().equalsIgnoreCase(defaultLauncher)) {
                Utils.setDefaultLauncher(this);
            }
        }
        updateLocationService();
    }

    private void updateLocationService() {
        startLocationServiceWithRetry();
        checkAndUpdateFiles();
    }

    private class RemoteFileStatus {
        public RemoteFile remoteFile;
        public boolean installed;
    }

    private void checkAndUpdateFiles() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                ServerConfig config = settingsHelper.getConfig();
                // This may be a long procedure due to checksum calculation so execute it in the background thread
                InstallUtils.generateFilesForInstallList(MainActivity.this, config.getFiles(), filesForInstall);
                return null;
            }

            @Override
            protected void onPostExecute(Void v) {
                loadAndInstallFiles();
            }
        }.execute();
    }

    private void loadAndInstallFiles() {
        if ( filesForInstall.size() > 0 ) {
            RemoteFile remoteFile = filesForInstall.remove(0);

            new AsyncTask<RemoteFile, Void, RemoteFileStatus>() {

                @Override
                protected RemoteFileStatus doInBackground(RemoteFile... remoteFiles) {
                    final RemoteFile remoteFile = remoteFiles[0];
                    RemoteFileStatus remoteFileStatus = null;

                    if (remoteFile.isRemove()) {
                        RemoteLogger.log(MainActivity.this, Const.LOG_DEBUG, "Removing file: " + remoteFile.getPath());
                        File file = new File(Environment.getExternalStorageDirectory(), remoteFile.getPath());
                        try {
                            file.delete();
                            RemoteFileTable.deleteByPath(DatabaseHelper.instance(MainActivity.this).getWritableDatabase(), remoteFile.getPath());
                        } catch (Exception e) {
                            RemoteLogger.log(MainActivity.this, Const.LOG_WARN, "Failed to remove file: " +
                                    remoteFile.getPath() + ": " + e.getMessage());
                            e.printStackTrace();
                        }

                    } else if (remoteFile.getUrl() != null) {
                        updateMessageForFileDownloading(remoteFile.getPath());

                        File file = null;
                        try {
                            RemoteLogger.log(MainActivity.this, Const.LOG_DEBUG, "Downloading file: " + remoteFile.getPath());
                            file = InstallUtils.downloadFile(MainActivity.this, remoteFile.getUrl(),
                                    new InstallUtils.DownloadProgress() {
                                        @Override
                                        public void onDownloadProgress(final int progress, final long total, final long current) {
                                            handler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    binding.progress.setMax(100);
                                                    binding.progress.setProgress(progress);

                                                    binding.setFileLength(total);
                                                    binding.setDownloadedLength(current);
                                                }
                                            });
                                        }
                                    });
                        } catch (Exception e) {
                            RemoteLogger.log(MainActivity.this, Const.LOG_WARN,
                                    "Failed to download file " + remoteFile.getPath() + ": " + e.getMessage());
                            e.printStackTrace();
                        }

                        remoteFileStatus = new RemoteFileStatus();
                        remoteFileStatus.remoteFile = remoteFile;
                        if (file != null) {
                            File finalFile = new File(Environment.getExternalStorageDirectory(), remoteFile.getPath());
                            try {
                                if (finalFile.exists()) {
                                    finalFile.delete();
                                }
                                FileUtils.moveFile(file, finalFile);
                                RemoteFileTable.insert(DatabaseHelper.instance(MainActivity.this).getWritableDatabase(), remoteFile);
                                remoteFileStatus.installed = true;
                            } catch (Exception e) {
                                RemoteLogger.log(MainActivity.this, Const.LOG_WARN,
                                        "Failed to create file " + remoteFile.getPath() + ": " + e.getMessage());
                                e.printStackTrace();
                                remoteFileStatus.installed = false;
                            }
                        } else {
                            remoteFileStatus.installed = false;
                        }
                    }

                    return remoteFileStatus;
                }

                @Override
                protected void onPostExecute(RemoteFileStatus fileStatus) {
                    if (fileStatus != null) {
                        if (!fileStatus.installed) {
                            filesForInstall.add( 0, fileStatus.remoteFile );
                            if (!ProUtils.kioskModeRequired(MainActivity.this)) {
                                // Notify the error dialog that we're downloading a file, not an app
                                downloadingFile = true;
                                createAndShowFileNotDownloadedDialog(fileStatus.remoteFile.getUrl());
                                binding.setDownloading( false );
                            } else {
                                // Avoid user interaction in kiosk mode, just ignore download error and keep the old version
                                // Note: view is not used in this method so just pass null there
                                confirmDownloadFailureClicked(null);
                            }
                            return;
                        }
                    }
                    Log.i(Const.LOG_TAG, "loadAndInstallFiles(): proceed to next file");
                    loadAndInstallFiles();
                }

            }.execute(remoteFile);
        } else {
            Log.i(Const.LOG_TAG, "Proceed to application update");
            checkAndUpdateApplications();
        }
    }

    // Workaround against crash "App is in background" on Android 9: this is an Android OS bug
    // https://stackoverflow.com/questions/52013545/android-9-0-not-allowed-to-start-service-app-is-in-background-after-onresume
    private void startLocationServiceWithRetry() {
        try {
            startLocationService();
        } catch (Exception e) {
            // Android OS bug!!!
            e.printStackTrace();

            // Repeat an attempt to start service after one second
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    try {
                        startLocationService();
                    } catch (Exception e) {
                        // Still failed, now give up!
                        e.printStackTrace();
                    }
                }
            }, 1000);
        }
    }

    private void startLocationService() {
        ServerConfig config = settingsHelper.getConfig();
        Intent intent = new Intent(this, LocationService.class);
        intent.setAction(config.getRequestUpdates() != null ? config.getRequestUpdates() : LocationService.ACTION_STOP);
        startService(intent);
    }

    private void checkAndUpdateApplications() {
        Log.i(Const.LOG_TAG, "checkAndUpdateApplications(): starting update applications");
        binding.setMessage( getString( R.string.main_activity_applications_update ) );

        configInitialized = true;
        configInitializing = false;

        ServerConfig config = settingsHelper.getConfig();
        InstallUtils.generateApplicationsForInstallList(this, config.getApplications(), applicationsForInstall);

        Log.i(Const.LOG_TAG, "checkAndUpdateApplications(): list size=" + applicationsForInstall.size());

        loadAndInstallApplications();
    }

    private class ApplicationStatus {
        public Application application;
        public boolean installed;
    }

    // Here we avoid ConcurrentModificationException by executing all operations with applicationForInstall list in a main thread
    private void loadAndInstallApplications() {
        if ( applicationsForInstall.size() > 0 ) {
            Application application = applicationsForInstall.remove(0);

            new AsyncTask<Application, Void, ApplicationStatus>() {

                @Override
                protected ApplicationStatus doInBackground(Application... applications) {
                    final Application application = applications[0];
                    ApplicationStatus applicationStatus = null;

                    if (application.isRemove()) {
                        // Remove the app
                        RemoteLogger.log(MainActivity.this, Const.LOG_DEBUG, "Removing app: " + application.getPkg());
                        updateMessageForApplicationRemoving( application.getName() );
                        uninstallApplication(application.getPkg());

                    } else if ( application.getUrl() != null && !application.getUrl().startsWith("market://details") ) {
                        updateMessageForApplicationDownloading(application.getName());

                        File file = null;
                        try {
                            RemoteLogger.log(MainActivity.this, Const.LOG_DEBUG, "Downloading app: " + application.getPkg());
                            file = InstallUtils.downloadFile(MainActivity.this, application.getUrl(),
                                    new InstallUtils.DownloadProgress() {
                                        @Override
                                        public void onDownloadProgress(final int progress, final long total, final long current) {
                                            handler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    binding.progress.setMax(100);
                                                    binding.progress.setProgress(progress);

                                                    binding.setFileLength(total);
                                                    binding.setDownloadedLength(current);
                                                }
                                            });
                                        }
                                    });
                        } catch (Exception e) {
                            RemoteLogger.log(MainActivity.this, Const.LOG_WARN, "Failed to download app " + application.getPkg() + ": " + e.getMessage());
                            e.printStackTrace();
                        }

                        applicationStatus = new ApplicationStatus();
                        applicationStatus.application = application;
                        if (file != null) {
                            updateMessageForApplicationInstalling(application.getName());
                            installApplication(file, application.getPkg());
                            applicationStatus.installed = true;
                        } else {
                            applicationStatus.installed = false;
                        }
                    } else if (application.getUrl().startsWith("market://details")) {
                        RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Installing app " + application.getPkg() + " from Google Play");
                        installApplicationFromPlayMarket(application.getUrl(), application.getPkg());
                        applicationStatus = new ApplicationStatus();
                        applicationStatus.application = application;
                        applicationStatus.installed = true;
                    } else {
                        handler.post( new Runnable() {
                            @Override
                            public void run() {
                                Log.i(Const.LOG_TAG, "loadAndInstallApplications(): proceed to next app");
                                loadAndInstallApplications();
                            }
                        } );
                    }

                    return applicationStatus;
                }

                @Override
                protected void onPostExecute(ApplicationStatus applicationStatus) {
                    if (applicationStatus != null) {
                        if (applicationStatus.installed) {
                            if (applicationStatus.application.isRunAfterInstall()) {
                                applicationsForRun.add(applicationStatus.application);
                            }
                        } else {
                            applicationsForInstall.add( 0, applicationStatus.application );
                            if (!ProUtils.kioskModeRequired(MainActivity.this)) {
                                // Notify the error dialog that we're downloading an app
                                downloadingFile = false;
                                createAndShowFileNotDownloadedDialog(applicationStatus.application.getName());
                                binding.setDownloading( false );
                            } else {
                                // Avoid user interaction in kiosk mode, just ignore download error and keep the old version
                                // Note: view is not used in this method so just pass null there
                                confirmDownloadFailureClicked(null);
                            }
                        }
                    }
                }

            }.execute(application);
        } else {
            RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Configuration updated");
            Log.i(Const.LOG_TAG, "Showing content from loadAndInstallApplications()");
            showContent( settingsHelper.getConfig() );
        }
    }

    private void installApplicationFromPlayMarket(final String uri, final String packageName) {
        RemoteLogger.log(MainActivity.this, Const.LOG_DEBUG, "Asking user to install app " + packageName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(uri));
        startActivity(intent);
    }

    // This function is called from a background thread
    private void installApplication( File file, final String packageName ) {
        if (packageName.equals(getPackageName()) &&
                getPackageManager().getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID) != null) {
            // Restart self in EMUI: there's no auto restart after update in EMUI, we must use a helper app
            startLauncherRestarter();
        }
        if (Utils.isDeviceOwner(this) || BuildConfig.SYSTEM_PRIVILEGES) {
                RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Silently installing app " + packageName);
            InstallUtils.silentInstallApplication(this, file, packageName, new InstallUtils.InstallErrorHandler() {
                    @Override
                    public void onInstallError() {
                        Log.i(Const.LOG_TAG, "installApplication(): error installing app " + packageName);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                new AlertDialog.Builder(MainActivity.this)
                                    .setMessage(getString(R.string.install_error) + " " + packageName)
                                    .setPositiveButton(R.string.dialog_administrator_mode_continue, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            checkAndStartLauncher();
                                        }
                                    })
                                    .create()
                                    .show();
                            }
                        });
                    }
                });
        } else {
            RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Asking user to install app " + packageName);
            InstallUtils.requestInstallApplication(MainActivity.this, file, new InstallUtils.InstallErrorHandler() {
                @Override
                public void onInstallError() {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            checkAndStartLauncher();
                        }
                    });
                }
            });
        }
    }

    private void uninstallApplication(final String packageName) {
        if (Utils.isDeviceOwner(this) || BuildConfig.SYSTEM_PRIVILEGES) {
            RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Silently uninstall app " + packageName);
            InstallUtils.silentUninstallApplication(this, packageName);
        } else {
            RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Asking user to uninstall app " + packageName);
            InstallUtils.requestUninstallApplication(this, packageName);
        }
    }

    private void updateMessageForApplicationInstalling( final String name ) {
        handler.post( new Runnable() {
            @Override
            public void run() {
                binding.setMessage( getString(R.string.main_app_installing) + " " + name );
                binding.setDownloading( false );
            }
        } );
    }

    private void updateMessageForFileDownloading( final String name ) {
        handler.post( new Runnable() {
            @Override
            public void run() {
                binding.setMessage( getString(R.string.main_file_downloading) + " " + name );
                binding.setDownloading( true );
            }
        } );
    }

    private void updateMessageForApplicationDownloading( final String name ) {
        handler.post( new Runnable() {
            @Override
            public void run() {
                binding.setMessage( getString(R.string.main_app_downloading) + " " + name );
                binding.setDownloading( true );
            }
        } );
    }

    private void updateMessageForApplicationRemoving( final String name ) {
        handler.post( new Runnable() {
            @Override
            public void run() {
                binding.setMessage( getString(R.string.main_app_removing) + " " + name );
                binding.setDownloading( false );
            }
        } );
    }

    private boolean checkSystemSettings(ServerConfig config) {
        if (config.getSystemUpdateType() != null &&
                config.getSystemUpdateType() != ServerConfig.SYSTEM_UPDATE_DEFAULT &&
                Utils.isDeviceOwner(this)) {
            Utils.setSystemUpdatePolicy(this, config.getSystemUpdateType(), config.getSystemUpdateFrom(), config.getSystemUpdateTo());
        }

        if (config.getBluetooth() != null) {
            try {
                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (bluetoothAdapter != null) {
                    boolean enabled = bluetoothAdapter.isEnabled();
                    if (config.getBluetooth() && !enabled) {
                        bluetoothAdapter.enable();
                    } else if (!config.getBluetooth() && enabled) {
                        bluetoothAdapter.disable();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Note: SecurityException here on Mediatek
        // Looks like com.mediatek.permission.CTA_ENABLE_WIFI needs to be explicitly granted
        // or even available to system apps only
        // By now, let's just ignore this issue
        if (config.getWifi() != null) {
            try {
                WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    boolean enabled = wifiManager.isWifiEnabled();
                    if (config.getWifi() && !enabled) {
                        wifiManager.setWifiEnabled(true);
                    } else if (!config.getWifi() && enabled) {
                        wifiManager.setWifiEnabled(false);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // To delay opening the settings activity
        boolean dialogWillShow = false;

        if (config.getGps() != null) {
            LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                boolean enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                if (config.getGps() && !enabled) {
                    dialogWillShow = true;
                    // System settings dialog should return result so we could re-initialize location service
                    postDelayedSystemSettingDialog(getString(R.string.message_turn_on_gps),
                            new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_CODE_GPS_STATE_CHANGE);

                } else if (!config.getGps() && enabled) {
                    dialogWillShow = true;
                    postDelayedSystemSettingDialog(getString(R.string.message_turn_off_gps),
                            new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_CODE_GPS_STATE_CHANGE);
                }
            }
        }

        if (config.getMobileData() != null) {
            ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && !dialogWillShow) {
                try {
                    boolean enabled = Utils.isMobileDataEnabled(this);
                    //final Intent mobileDataSettingsIntent = new Intent();
                    // One more hack: open the data transport activity
                    // https://stackoverflow.com/questions/31700842/which-intent-should-open-data-usage-screen-from-settings
                    //mobileDataSettingsIntent.setComponent(new ComponentName("com.android.settings",
                    //        "com.android.settings.Settings$DataUsageSummaryActivity"));
                    //Intent mobileDataSettingsIntent = new Intent(Intent.ACTION_MAIN);
                    //mobileDataSettingsIntent.setClassName("com.android.phone", "com.android.phone.NetworkSetting");
                    Intent mobileDataSettingsIntent = new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
                    // Mobile data are turned on/off in the status bar! No settings (as the user can go back in settings and do something nasty)
                    if (config.getMobileData() && !enabled) {
                        postDelayedSystemSettingDialog(getString(R.string.message_turn_on_mobile_data), /*mobileDataSettingsIntent*/null);
                    } else if (!config.getMobileData() && enabled) {
                        postDelayedSystemSettingDialog(getString(R.string.message_turn_off_mobile_data), /*mobileDataSettingsIntent*/null);
                    }
                } catch (Exception e) {
                    // Some problem accessible private API
                }
            }
        }

        if (!Utils.setPasswordMode(config.getPasswordMode(), this)) {
            Intent updatePasswordIntent = new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD);
            // Different Android versions/builds use different activities to setup password
            // So we have to enable temporary access to settings here (and only here!)
            postDelayedSystemSettingDialog(getString(R.string.message_set_password), updatePasswordIntent, null, true);
        }

        if (config.getTimeZone() != null) {
            Utils.setTimeZone(config.getTimeZone(), this);
        }

        if (config.getUsbStorage() != null) {
            Utils.lockUsbStorage(config.getUsbStorage(), this);
        }

        // Null value is processed here, it means unlock brightness
        Utils.setBrightnessPolicy(config.getAutoBrightness(), config.getBrightness(), this);

        if (config.getManageTimeout() != null) {
            Utils.setScreenTimeoutPolicy(config.getManageTimeout(), config.getTimeout(), this);
        }

        if (config.getLockVolume() != null) {
            Utils.lockVolume(config.getLockVolume(), this);
        }

        return true;
    }

    private void showContent(ServerConfig config ) {
        if (!checkSystemSettings(config)) {
            // Here we go when the settings window is opened;
            // Next time we're here after we returned from the Android settings through onResume()
            return;
        }

        sendDeviceInfoAfterReconfigure();
        scheduleDeviceInfoSending();
        scheduleInstalledAppsRun();

        if (config.getLock() != null && config.getLock()) {
            showLockScreen();
            return;
        } else {
            hideLockScreen();
        }

        // Run default launcher option
        if (config.getRunDefaultLauncher() != null && config.getRunDefaultLauncher() &&
            !getPackageName().equals(Utils.getDefaultLauncher(this)) && !Utils.isLauncherIntent(getIntent())) {
            openDefaultLauncher();
            return;
        }

        Utils.setOrientation(this, config);

        if (ProUtils.kioskModeRequired(this)) {
            String kioskApp = settingsHelper.getConfig().getMainApp();
            if (kioskApp != null && kioskApp.trim().length() > 0 &&
                    // If Headwind MDM itself is set as kiosk app, the kiosk mode is already turned on;
                    // So here we just proceed to drawing the content
                    (!kioskApp.equals(getPackageName()) || !ProUtils.isKioskModeRunning(this))) {
                if (ProUtils.startCosuKioskMode(kioskApp, this)) {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    return;
                } else {
                    Log.e(Const.LOG_TAG, "Kiosk mode failed, proceed with the default flow");
                }
            } else {
                Log.e(Const.LOG_TAG, "Kiosk mode disabled: please setup the main app!");
            }
        } else {
            if (ProUtils.isKioskModeRunning(this)) {
                // Turn off kiosk and show desktop if it is turned off in the configuration
                ProUtils.unlockKiosk(this);
                openDefaultLauncher();
            }
        }

        if ( config.getBackgroundColor() != null ) {
            try {
                binding.activityMainContentWrapper.setBackgroundColor(Color.parseColor(config.getBackgroundColor()));
            } catch (Exception e) {
                // Invalid color
                e.printStackTrace();
                binding.activityMainContentWrapper.setBackgroundColor( getResources().getColor(R.color.defaultBackground));
            }
        } else {
            binding.activityMainContentWrapper.setBackgroundColor( getResources().getColor(R.color.defaultBackground));
        }
        updateTitle(config);

        if (appListAdapter == null || needRedrawContentAfterReconfigure) {
            needRedrawContentAfterReconfigure = false;

            if ( config.getBackgroundImageUrl() != null && config.getBackgroundImageUrl().length() > 0 ) {
                Picasso.Builder builder = new Picasso.Builder(this);
                if (BuildConfig.TRUST_ANY_CERTIFICATE) {
                    builder.downloader(new OkHttp3Downloader(UnsafeOkHttpClient.getUnsafeOkHttpClient()));
                }
                builder.listener(new Picasso.Listener()
                {
                    @Override
                    public void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception)
                    {
                        // On fault, get the background image from the cache
                        // This is a workaround against a bug in Picasso: it doesn't display cached images by default!
                        Picasso.with(MainActivity.this)
                            .load(config.getBackgroundImageUrl())
                            .networkPolicy(NetworkPolicy.OFFLINE)
                            .into(binding.activityMainBackground);
                    }
                });
                builder.build()
                    .load(config.getBackgroundImageUrl())
                    .into(binding.activityMainBackground);

            } else {
                binding.activityMainBackground.setImageDrawable(null);
            }

            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);

            int width = size.x;
            int itemWidth = getResources().getDimensionPixelSize(R.dimen.app_list_item_size);

            spanCount = (int) (width * 1.0f / itemWidth);
            appListAdapter = new AppListAdapter(this, this);
            appListAdapter.setSpanCount(spanCount);

            binding.activityMainContent.setLayoutManager(new GridLayoutManager(this, spanCount));
            binding.activityMainContent.setAdapter(appListAdapter);
            appListAdapter.notifyDataSetChanged();
        }
        binding.setShowContent(true);
        // We can now sleep, uh
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void showLockScreen() {
        if (lockScreen == null) {
            createLockScreen();
            if (lockScreen == null) {
                // Why cannot we create the lock screen? Give up and return
                // The locked device will show the launcher, but still cannot run any application
                return;
            }
        }
        String lockAdminMessage = settingsHelper.getConfig().getLockMessage();
        String lockMessage = getString(R.string.device_locked);
        if (lockAdminMessage != null) {
            lockMessage += " " + lockAdminMessage;
        }
        TextView textView = lockScreen.findViewById( R.id.message );
        textView.setText(lockMessage);
        lockScreen.setVisibility(View.VISIBLE);
    }

    private void hideLockScreen() {
        if (lockScreen != null && lockScreen.getVisibility() == View.VISIBLE) {
            lockScreen.setVisibility(View.GONE);
        }
    }

    private void notifyPolicyViolation(int cause) {
        switch (cause) {
            case Const.GPS_ON_REQUIRED:
                postDelayedSystemSettingDialog(getString(R.string.message_turn_on_gps),
                        new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_CODE_GPS_STATE_CHANGE);
                break;
            case Const.GPS_OFF_REQUIRED:
                postDelayedSystemSettingDialog(getString(R.string.message_turn_off_gps),
                        new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_CODE_GPS_STATE_CHANGE);
                break;
            case Const.MOBILE_DATA_ON_REQUIRED:
                createAndShowSystemSettingDialog(getString(R.string.message_turn_on_mobile_data), null, 0);
                break;
            case Const.MOBILE_DATA_OFF_REQUIRED:
                createAndShowSystemSettingDialog(getString(R.string.message_turn_off_mobile_data), null, 0);
                break;
        }
    }

    // Run default launcher (Headwind MDM) as if the user clicked Home button
    private void openDefaultLauncher() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    public static class SendDeviceInfoWorker extends Worker {

        private Context context;
        private SettingsHelper settingsHelper;

        public SendDeviceInfoWorker(
                @NonNull final Context context,
                @NonNull WorkerParameters params) {
            super(context, params);
            this.context = context;
            settingsHelper = SettingsHelper.getInstance(context);
        }

        @Override
        // This is running in a background thread by WorkManager
        public Result doWork() {
            if (settingsHelper == null || settingsHelper.getConfig() == null) {
                return Result.failure();
            }

            DeviceInfo deviceInfo = DeviceInfoProvider.getDeviceInfo(context, true, true);

            ServerService serverService = ServerServiceKeeper.getServerServiceInstance(context);
            ServerService secondaryServerService = ServerServiceKeeper.getSecondaryServerServiceInstance(context);
            Response<ResponseBody> response = null;

            try {
                response = serverService.sendDevice(settingsHelper.getServerProject(), deviceInfo).execute();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (response == null) {
                    response = secondaryServerService.sendDevice(settingsHelper.getServerProject(), deviceInfo).execute();
                }
                if ( response.isSuccessful() ) {
                    SettingsHelper.getInstance(context).setExternalIp(response.headers().get(Const.HEADER_IP_ADDRESS));
                    return Result.success();
                }
            }
            catch ( Exception e ) { e.printStackTrace(); }

            return Result.failure();
        }
    }

    // If we updated the configuration, let's send the final state to the server
    private void sendDeviceInfoAfterReconfigure() {
        if (needSendDeviceInfoAfterReconfigure) {
            needSendDeviceInfoAfterReconfigure = false;
            SendDeviceInfoTask sendDeviceInfoTask = new SendDeviceInfoTask(this);
            DeviceInfo deviceInfo = DeviceInfoProvider.getDeviceInfo(this, true, true);
            sendDeviceInfoTask.execute(deviceInfo);
        }
    }

    private void scheduleDeviceInfoSending() {
        if (sendDeviceInfoScheduled) {
            return;
        }
        sendDeviceInfoScheduled = true;
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(SendDeviceInfoWorker.class, SEND_DEVICE_INFO_PERIOD_MINS, TimeUnit.MINUTES)
                        .addTag(Const.WORK_TAG_COMMON)
                        .setInitialDelay(SEND_DEVICE_INFO_PERIOD_MINS, TimeUnit.MINUTES)
                        .build();
        WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(WORK_TAG_DEVICEINFO, ExistingPeriodicWorkPolicy.REPLACE, request);
    }

    private void scheduleInstalledAppsRun() {
        if (applicationsForRun.size() == 0) {
            return;
        }
        Handler handler = new Handler();
        int pause = PAUSE_BETWEEN_AUTORUNS_SEC;
        while (applicationsForRun.size() > 0) {
            final Application application = applicationsForRun.get(0);
            applicationsForRun.remove(0);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(application.getPkg());
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    }
                }
            }, pause * 1000);
            pause += PAUSE_BETWEEN_AUTORUNS_SEC;
        }
    }

    private void updateTitle(ServerConfig config) {
        String titleType = config.getTitle();
        if (titleType != null && titleType.equals(ServerConfig.TITLE_DEVICE_ID)) {
            if (config.getTextColor() != null) {
                try {
                    binding.activityMainTitle.setTextColor(Color.parseColor(settingsHelper.getConfig().getTextColor()));
                } catch (Exception e) {
                    // Invalid color
                    e.printStackTrace();
                }
            }
            binding.activityMainTitle.setVisibility(View.VISIBLE);
            binding.activityMainTitle.setText(SettingsHelper.getInstance(this).getDeviceId());
        } else {
            binding.activityMainTitle.setVisibility(View.GONE);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        WindowManager manager = ((WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE));
        if ( applicationNotAllowed != null ) {
            try { manager.removeView( applicationNotAllowed ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        if ( statusBarView != null ) {
            try { manager.removeView( statusBarView ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        if ( rightToolbarView != null ) {
            try { manager.removeView( rightToolbarView ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        if ( exitView != null ) {
            try { manager.removeView( exitView ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        if ( infoView != null ) {
            try { manager.removeView( infoView ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        if ( updateView != null ) {
            try { manager.removeView( updateView ); }
            catch ( Exception e ) { e.printStackTrace(); }
        }

        LocalBroadcastManager.getInstance( this ).unregisterReceiver( receiver );
        unregisterReceiver(stateChangeReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();

        isBackground = true;

        dismissDialog(fileNotDownloadedDialog);
        dismissDialog(enterDeviceIdDialog);
        dismissDialog(networkErrorDialog);
        dismissDialog(enterPasswordDialog);
        dismissDialog(historySettingsDialog);
        dismissDialog(unknownSourcesDialog);
        dismissDialog(overlaySettingsDialog);
        dismissDialog(administratorModeDialog);
        dismissDialog(deviceInfoDialog);
        dismissDialog(accessibilityServiceDialog);
        dismissDialog(systemSettingsDialog);
        dismissDialog(permissionsDialog);

        LocalBroadcastManager.getInstance( this ).sendBroadcast( new Intent( Const.ACTION_SHOW_LAUNCHER ) );
    }

    private void createAndShowAdministratorDialog() {
        dismissDialog(administratorModeDialog);
        administratorModeDialog = new Dialog( this );
        dialogAdministratorModeBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_administrator_mode,
                null,
                false );
        administratorModeDialog.setCancelable( false );
        administratorModeDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        administratorModeDialog.setContentView( dialogAdministratorModeBinding.getRoot() );
        administratorModeDialog.show();
    }

    public void skipAdminMode( View view ) {
        dismissDialog(administratorModeDialog);

        RemoteLogger.log(this, Const.LOG_INFO, "Manually skipped the device admin permissions setup");
        preferences.
                edit().
                putInt( Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_OFF ).
                commit();

        checkAndStartLauncher();
    }

    public void setAdminMode( View view ) {
        dismissDialog(administratorModeDialog);

        Intent intent = new Intent( android.provider.Settings.ACTION_SECURITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void createAndShowFileNotDownloadedDialog(String fileName) {
        dismissDialog(fileNotDownloadedDialog);
        fileNotDownloadedDialog = new Dialog( this );
        dialogFileDownloadingFailedBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_file_downloading_failed,
                null,
                false );
        int errorTextResource = this.downloadingFile ? R.string.main_file_downloading_error : R.string.main_app_downloading_error;
        dialogFileDownloadingFailedBinding.title.setText( getString(errorTextResource) + " " + fileName );
        fileNotDownloadedDialog.setCancelable( false );
        fileNotDownloadedDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        fileNotDownloadedDialog.setContentView( dialogFileDownloadingFailedBinding.getRoot() );
        try {
            fileNotDownloadedDialog.show();
        } catch (Exception e) {
            // BadTokenException ignored
        }
    }

    public void repeatDownloadClicked( View view ) {
        dismissDialog(fileNotDownloadedDialog);
        if (downloadingFile) {
            loadAndInstallFiles();
        } else {
            loadAndInstallApplications();
        }
    }

    public void confirmDownloadFailureClicked( View view ) {
        dismissDialog(fileNotDownloadedDialog);

        if (downloadingFile) {
            if (filesForInstall.size() > 0) {
                RemoteFile remoteFile = filesForInstall.remove(0);
                settingsHelper.removeRemoteFile(remoteFile);
            }
            loadAndInstallFiles();
        } else {
            if (applicationsForInstall.size() > 0) {
                Application application = applicationsForInstall.remove(0);
                settingsHelper.removeApplication(application);
            }
            loadAndInstallApplications();
        }
    }

    private void createAndShowHistorySettingsDialog() {
        dismissDialog(historySettingsDialog);
        historySettingsDialog = new Dialog( this );
        dialogHistorySettingsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_history_settings,
                null,
                false );
        historySettingsDialog.setCancelable( false );
        historySettingsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        historySettingsDialog.setContentView( dialogHistorySettingsBinding.getRoot() );
        historySettingsDialog.show();
    }

    public void historyWithoutPermission( View view ) {
        dismissDialog(historySettingsDialog);

        preferences.
                edit().
                putInt( Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_OFF ).
                commit();
        checkAndStartLauncher();
    }

    public void continueHistory( View view ) {
        dismissDialog(historySettingsDialog);

        startActivity( new Intent( Settings.ACTION_USAGE_ACCESS_SETTINGS ) );
    }

    private void createAndShowOverlaySettingsDialog() {
        dismissDialog(overlaySettingsDialog);
        overlaySettingsDialog = new Dialog( this );
        dialogOverlaySettingsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_overlay_settings,
                null,
                false );
        overlaySettingsDialog.setCancelable( false );
        overlaySettingsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        overlaySettingsDialog.setContentView( dialogOverlaySettingsBinding.getRoot() );
        overlaySettingsDialog.show();
    }

    public void overlayWithoutPermission( View view ) {
        dismissDialog(overlaySettingsDialog);

        preferences.
                edit().
                putInt( Const.PREFERENCES_OVERLAY, Const.PREFERENCES_OFF ).
                commit();
        checkAndStartLauncher();
    }

    public void continueOverlay( View view ) {
        dismissDialog(overlaySettingsDialog);

        Intent intent = new Intent( Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse( "package:" + getPackageName() ) );
        startActivityForResult( intent, 1001 );
    }


    public void saveDeviceId( View view ) {
        String deviceId = enterDeviceIdDialogBinding.deviceId.getText().toString();
        if ( "".equals( deviceId ) ) {
            return;
        } else {
            settingsHelper.setDeviceId( deviceId );
            enterDeviceIdDialogBinding.setError( false );

            dismissDialog(enterDeviceIdDialog);

            if ( checkPermissions( true ) ) {
                Log.i(Const.LOG_TAG, "saveDeviceId(): calling updateConfig()");
                updateConfig( false );
            }
        }
    }


    public void saveServerUrl( View view ) {
        if (saveServerUrlBase()) {
            ServerServiceKeeper.resetServices();
            checkAndStartLauncher();
        }
    }


    public void networkErrorRepeatClicked( View view ) {
        dismissDialog(networkErrorDialog);

        Log.i(Const.LOG_TAG, "networkErrorRepeatClicked(): calling updateConfig()");
        updateConfig( true );
    }

    public void networkErrorResetClicked( View view ) {
        dismissDialog(networkErrorDialog);

        Log.i(Const.LOG_TAG, "networkErrorResetClicked(): calling updateConfig()");
        settingsHelper.setDeviceId("");
        settingsHelper.setBaseUrl("");
        settingsHelper.setSecondaryBaseUrl("");
        settingsHelper.setServerProject("");
        createAndShowServerDialog(false, settingsHelper.getBaseUrl(), settingsHelper.getServerProject());
    }

    public void networkErrorCancelClicked(View view) {
        dismissDialog(networkErrorDialog);

        if (configFault) {
            Log.i(Const.LOG_TAG, "networkErrorCancelClicked(): no configuration available, quit");
            Toast.makeText(this, R.string.critical_server_failure, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Log.i(Const.LOG_TAG, "networkErrorCancelClicked()");
        if ( settingsHelper.getConfig() != null ) {
            showContent( settingsHelper.getConfig() );
        } else {
            Log.i(Const.LOG_TAG, "networkErrorCancelClicked(): no configuration available, retrying");
            Toast.makeText(this, R.string.empty_configuration, Toast.LENGTH_LONG).show();
            configFault = true;
            updateConfig( false );
        }
    }

    private boolean checkPermissions( boolean startSettings ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        // If the user didn't grant permissions, let him know and do not request until he confirms he want to retry
        if (permissionsDialog != null && permissionsDialog.isShowing()) {
            return false;
        }

        if (Utils.isDeviceOwner(this)) {
            // Do not request permissions if we're the device owner
            // They are added automatically
            return true;
        }

        if (preferences.getInt(Const.PREFERENCES_DISABLE_LOCATION, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {

                if (startSettings) {
                    requestPermissions(new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_PHONE_STATE
                    }, PERMISSIONS_REQUEST);
                }
                return false;
            } else {
                return true;
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {

                if (startSettings) {
                    requestPermissions(new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.READ_PHONE_STATE
                    }, PERMISSIONS_REQUEST);
                }
                return false;
            } else {
                return true;
            }
        }
    }

    private void createAndShowEnterPasswordDialog() {
        dismissDialog(enterPasswordDialog);
        enterPasswordDialog = new Dialog( this );
        dialogEnterPasswordBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_enter_password,
                null,
                false );
        enterPasswordDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );
        enterPasswordDialog.setCancelable( false );

        enterPasswordDialog.setContentView( dialogEnterPasswordBinding.getRoot() );
        dialogEnterPasswordBinding.setLoading( false );
        try {
            enterPasswordDialog.show();
        } catch (Exception e) {
            // Sometimes here we get a Fatal Exception: android.view.WindowManager$BadTokenException
            // Unable to add window -- token android.os.BinderProxy@f307de for displayid = 0 is not valid; is your activity running?
            Toast.makeText(getApplicationContext(), R.string.internal_error, Toast.LENGTH_LONG).show();
        }
    }

    public void closeEnterPasswordDialog( View view ) {
        dismissDialog(enterPasswordDialog);
        if (ProUtils.kioskModeRequired(this)) {
            checkAndStartLauncher();
            updateConfig(false);
        }
    }

    public void checkAdministratorPassword( View view ) {
        dialogEnterPasswordBinding.setLoading( true );
        GetServerConfigTask task = new GetServerConfigTask( this ) {
            @Override
            protected void onPostExecute( Integer result ) {
                dialogEnterPasswordBinding.setLoading( false );

                String masterPassword = CryptoHelper.getMD5String( "12345678" );
                if ( settingsHelper.getConfig() != null && settingsHelper.getConfig().getPassword() != null ) {
                    masterPassword = settingsHelper.getConfig().getPassword();
                }

                if ( CryptoHelper.getMD5String( dialogEnterPasswordBinding.password.getText().toString() ).
                        equals( masterPassword ) ) {
                    dismissDialog(enterPasswordDialog);
                    dialogEnterPasswordBinding.setError( false );
                    if (ProUtils.kioskModeRequired(MainActivity.this)) {
                        ProUtils.unlockKiosk(MainActivity.this);
                    }
                    RemoteLogger.log(MainActivity.this, Const.LOG_INFO, "Administrator panel opened");
                    startActivity( new Intent( MainActivity.this, AdminActivity.class ) );
                } else {
                    dialogEnterPasswordBinding.setError( true );
                }
            }
        };
        task.execute();
    }

    private void createAndShowUnknownSourcesDialog() {
        dismissDialog(unknownSourcesDialog);
        unknownSourcesDialog = new Dialog( this );
        dialogUnknownSourcesBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_unknown_sources,
                null,
                false );
        unknownSourcesDialog.setCancelable( false );
        unknownSourcesDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        unknownSourcesDialog.setContentView( dialogUnknownSourcesBinding.getRoot() );
        unknownSourcesDialog.show();
    }

    public void continueUnknownSources( View view ) {
        dismissDialog(unknownSourcesDialog);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS));
        } else {
            // In Android Oreo and above, permission to install packages are set per each app
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
        }
    }

    private void createAndShowMiuiPermissionsDialog(int screen) {
        dismissDialog(miuiPermissionsDialog);
        miuiPermissionsDialog = new Dialog( this );
        dialogMiuiPermissionsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_miui_permissions,
                null,
                false );
        miuiPermissionsDialog.setCancelable( false );
        miuiPermissionsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );

        switch (screen) {
            case Const.MIUI_PERMISSIONS:
                dialogMiuiPermissionsBinding.title.setText(R.string.dialog_miui_permissions_title);
                break;
            case Const.MIUI_DEVELOPER:
                dialogMiuiPermissionsBinding.title.setText(R.string.dialog_miui_developer_title);
                break;
            case Const.MIUI_OPTIMIZATION:
                dialogMiuiPermissionsBinding.title.setText(R.string.dialog_miui_optimization_title);
                break;
        }

        miuiPermissionsDialog.setContentView( dialogMiuiPermissionsBinding.getRoot() );
        miuiPermissionsDialog.show();
    }

    public void continueMiuiPermissions( View view ) {
        String titleText = dialogMiuiPermissionsBinding.title.getText().toString();
        dismissDialog(miuiPermissionsDialog);

        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_ENABLE_SETTINGS));
        Intent intent;
        if (titleText.equals(getString(R.string.dialog_miui_permissions_title))) {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
        } else if (titleText.equals(getString(R.string.dialog_miui_developer_title))) {
            intent = new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS);
        } else {
            // if (titleText.equals(getString(R.string.dialog_miui_optimization_title))
            intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {}

    @Override
    public void onAppChoose( @NonNull AppInfo resolveInfo ) {

    }

    @Override
    public boolean onLongClick( View v ) {
        createAndShowEnterPasswordDialog();
        return true;

    }

    @Override
    public void onClick( View v ) {
        if (v.equals(infoView)) {
            createAndShowInfoDialog();
        } else if (v.equals(updateView)) {
            if (enterDeviceIdDialog != null && enterDeviceIdDialog.isShowing()) {
                Log.i(Const.LOG_TAG, "Occasional update request when device info is entered, ignoring!");
                return;
            }
            Log.i(Const.LOG_TAG, "updating config on request");
            binding.setShowContent(false);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            updateConfig( true );
        }
    }

    private void postDelayedSystemSettingDialog(final String message, final Intent settingsIntent) {
        postDelayedSystemSettingDialog(message, settingsIntent, null);
    }

    private void postDelayedSystemSettingDialog(final String message, final Intent settingsIntent, final Integer requestCode) {
        postDelayedSystemSettingDialog(message, settingsIntent, requestCode, false);
    }

    private void postDelayedSystemSettingDialog(final String message, final Intent settingsIntent, final Integer requestCode, final boolean forceEnableSettings) {
        if (settingsIntent != null) {
            // If settings are controlled by usage stats, safe settings are allowed, so we need to enable settings in accessibility mode only
            // Accessibility mode is only enabled when usage stats is off
            if (preferences.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON || forceEnableSettings) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_ENABLE_SETTINGS));
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Const.ACTION_STOP_CONTROL));
        }
        // Delayed start prevents the race of ENABLE_SETTINGS handle and tapping "Next" button
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                createAndShowSystemSettingDialog(message, settingsIntent, requestCode);
            }
        }, 5000);
    }

    private void createAndShowSystemSettingDialog(final String message, final Intent settingsIntent, final Integer requestCode) {
        dismissDialog(systemSettingsDialog);
        systemSettingsDialog = new Dialog( this );
        dialogSystemSettingsBinding = DataBindingUtil.inflate(
                LayoutInflater.from( this ),
                R.layout.dialog_system_settings,
                null,
                false );
        systemSettingsDialog.requestWindowFeature( Window.FEATURE_NO_TITLE );
        systemSettingsDialog.setCancelable( false );

        systemSettingsDialog.setContentView( dialogSystemSettingsBinding.getRoot() );

        dialogSystemSettingsBinding.setMessage(message);

        // Since we need to send Intent to the listener, here we don't use "event" attribute in XML resource as everywhere else
        systemSettingsDialog.findViewById(R.id.continueButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissDialog(systemSettingsDialog);
                if (settingsIntent == null) {
                    return;
                }
                // Enable settings once again, because the dialog may be shown more than 3 minutes
                // This is not necessary: the problem is resolved by clicking "Continue" in a popup window
                /*LocalBroadcastManager.getInstance( MainActivity.this ).sendBroadcast( new Intent( Const.ACTION_ENABLE_SETTINGS ) );
                // Open settings with a slight delay so Broadcast would certainly be handled
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startActivity(settingsIntent);
                    }
                }, 300);*/
                try {
                    startActivityOptionalResult(settingsIntent, requestCode);
                } catch (/*ActivityNotFound*/Exception e) {
                    // Open settings by default
                    startActivityOptionalResult(new Intent(android.provider.Settings.ACTION_SETTINGS), requestCode);
                }
            }
        });

        try {
            systemSettingsDialog.show();
        } catch (Exception e) {
            // BadTokenException: activity closed before dialog is shown
            RemoteLogger.log(this, Const.LOG_WARN, "Failed to open a popup system dialog! " + e.getMessage());
            e.printStackTrace();
            systemSettingsDialog = null;
        }
    }

    private void startActivityOptionalResult(Intent intent, Integer requestCode) {
        if (requestCode != null) {
            startActivityForResult(intent, requestCode);
        } else {
            startActivity(intent);
        }
    }

    // The following algorithm of launcher restart works in EMUI:
    // Run EMUI_LAUNCHER_RESTARTER activity once and send the old version number to it.
    // The restarter application will check the launcher version each second, and restart it
    // when it is changed.
    private void startLauncherRestarter() {
        // Sending an intent before updating, otherwise the launcher may be terminated at any time
        Intent intent = getPackageManager().getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID);
        if (intent == null) {
            Log.i("LauncherRestarter", "No restarter app, please add it in the config!");
            return;
        }
        intent.putExtra(Const.LAUNCHER_RESTARTER_OLD_VERSION, BuildConfig.VERSION_NAME);
        startActivity(intent);
        Log.i("LauncherRestarter", "Calling launcher restarter from the launcher");
    }
}
