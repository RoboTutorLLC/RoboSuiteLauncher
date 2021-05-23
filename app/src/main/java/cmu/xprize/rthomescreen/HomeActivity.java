package cmu.xprize.rthomescreen;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.xprize.comp_configuration.Configuration;
import com.xprize.comp_configuration.ConfigurationItems;
import com.xprize.comp_configuration.ConfigurationQuickOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import cmu.xprize.comp_logging.CErrorManager;
import cmu.xprize.comp_logging.CInterventionLogManager;
import cmu.xprize.comp_logging.CLogManager;
import cmu.xprize.comp_logging.CPerfLogManager;
import cmu.xprize.comp_logging.CPreferenceCache;
import cmu.xprize.comp_logging.ILogManager;
import cmu.xprize.comp_logging.IPerfLogManager;
import cmu.xprize.rthomescreen.startup.CMasterContainer;
import cmu.xprize.rthomescreen.startup.CStartView;
import cmu.xprize.util.IRoboTutor;
import cmu.xprize.util.JSON_Helper;
import cmu.xprize.util.TCONST;

import static android.os.UserManager.DISALLOW_ADD_USER;
import static android.os.UserManager.DISALLOW_ADJUST_VOLUME;
import static android.os.UserManager.DISALLOW_FACTORY_RESET;
import static android.os.UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA;
import static android.os.UserManager.DISALLOW_SAFE_BOOT;
import static cmu.xprize.util.TCONST.GRAPH_MSG;

public class HomeActivity extends AppCompatActivity implements IRoboTutor{



    static public CMasterContainer masterContainer;

    private CStartView startView;

    // kiosk info
    private ComponentName mAdminComponentName;
    private DevicePolicyManager mDevicePolicyManager;
    private PackageManager mPackageManager;
    private String mPackageName;
    private ArrayList<String> mKioskPackages;
    private String flPackage = "com.example.iris.login1";
    private String rtPackage = "cmu.xprize.robotutor";
    private String ftpPackage = "cmu.xprize.service_ftp";
    private String ftpStartReceiver = ftpPackage + ".RoboTransferReceiver";

    private static final String TAG = "RTHomeActivity";
    private static final String DEBUG_TAG = "DEBUG_LAUNCH";

    // do we launch FaceLogin first? Or RoboTutor?
    private static final boolean LAUNCH_FACELOGIN = true;
    // launch vars
    public static final String STUDENT_ID_VAR = "studentId";
    public static final String SESSION_ID_VAR = "sessionId";

    private static ConfigurationItems configurationItems;

    static public String        VERSION_RT;

    static public ILogManager logManager;
    static public IPerfLogManager perfLogManager;

    private static String hotLogPath;
    private static String hotLogPathPerf;
    private static String readyLogPath;
    private static String readyLogPathPerf;
    private static String audioLogPath;
    private static String interventionLogPath;

    final static public String CacheSource = TCONST.ASSETS;                // assets or extern
    static public String        APP_PRIVATE_FILES;
    static public String        LOG_ID = "STARTUP";

    private static final String LOG_SEQUENCE_ID = "LOG_SEQUENCE_ID";

    // for devs, this is faster than changing the config file
    private static final boolean QUICK_DEBUG_CONFIG = false;
    private static final ConfigurationItems QUICK_DEBUG_CONFIG_OPTION = ConfigurationQuickOptions.DEBUG_EN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        APP_PRIVATE_FILES = getApplicationContext().getExternalFilesDir("").getPath();

        // Initialize the JSON Helper STATICS - just throw away the object.
        //
        new JSON_Helper(getAssets(), CacheSource, APP_PRIVATE_FILES);

        // Gives the dev the option to override the stored config file.
        configurationItems = QUICK_DEBUG_CONFIG ? QUICK_DEBUG_CONFIG_OPTION : new ConfigurationItems(); // OPEN_SOURCE opt to switch here.
        Configuration.saveConfigurationItems(this, configurationItems);

        // Prep the CPreferenceCache
        // Update the globally accessible id object for this engine instance.
        //
        LOG_ID = CPreferenceCache.initLogPreference(this);
        VERSION_RT   = BuildConfig.VERSION_NAME;

        //Start LogManager
        //
        initializeAndStartLogs();

        //Log current config data
        //
        Configuration.logConfigurationItems(this);

        // stuff needed for kiosk mode
        // TODO move this to one class
        mAdminComponentName = AdminReceiver.getComponentName(this);
        mDevicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        mPackageManager = getPackageManager();
        mPackageName = getPackageName();

        // mKisokPackages
        mKioskPackages = new ArrayList<>();
        mKioskPackages.add(getPackageName());
        mKioskPackages.add(flPackage);
        mKioskPackages.add(rtPackage);
        mKioskPackages.add(mPackageName);

        try {
            setDefaultKioskPolicies(true);
        } catch (SecurityException e) {
            Toast.makeText(getApplicationContext(), "WARNING: This app is not the Device Owner. Kiosk mode not enabled.", Toast.LENGTH_LONG).show();
        }

        //setAppPermissions();


        // Get the primary container for tutors
        setContentView(R.layout.activity_home);
        masterContainer = (CMasterContainer)findViewById(R.id.master_container);

        setFullScreen();

        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Create the start dialog
        //
        startView = (CStartView)inflater.inflate(R.layout.start_layout, null );
        startView.setCallback(this);

        //
        masterContainer.addAndShow(startView);
        startView.startTapTutor();
        setFullScreen();

        try {
            Settings.Global.putInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0);
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        launchFtpService();
    }


    /**
     * create log paths.
     * initialize times and other IDs
     * start logging
     */
    private void initializeAndStartLogs() {

        hotLogPath   = Environment.getExternalStorageDirectory() + TCONST.HOT_LOG_FOLDER;
        readyLogPath = Environment.getExternalStorageDirectory() + TCONST.READY_LOG_FOLDER;

        hotLogPathPerf = Environment.getExternalStorageDirectory() + TCONST.HOT_LOG_FOLDER_PERF;
        readyLogPathPerf = Environment.getExternalStorageDirectory() + TCONST.READY_LOG_FOLDER_PERF;

        audioLogPath = Environment.getExternalStorageDirectory() + TCONST.AUDIO_LOG_FOLDER;

        interventionLogPath = Environment.getExternalStorageDirectory() + TCONST.INTERVENTION_LOG_FOLDER;

        Calendar calendar = Calendar.getInstance(Locale.US);
        String initTime     = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss", Locale.US).format(calendar.getTime());
        String sequenceIdString = String.format(Locale.US, "%06d", getNextLogSequenceId());
        // NOTE: Need to include the configuration name when that is fully merged
        String logFilename  = "RTSuiteHome_" + // TODO TODO TODO there should be a version name in here!!!
                Configuration.configVersion(this) + "_" + BuildConfig.VERSION_NAME + "_" + sequenceIdString +
                "_" + initTime + "_" + Build.SERIAL;

        Log.w("LOG_DEBUG", "Beginning new session with LOG_FILENAME = " + logFilename);

        logManager = CLogManager.getInstance();
        logManager.transferHotLogs(hotLogPath, readyLogPath);
        logManager.transferHotLogs(hotLogPathPerf, readyLogPathPerf);

        logManager.startLogging(hotLogPath, logFilename);
        CErrorManager.setLogManager(logManager);

        perfLogManager = CPerfLogManager.getInstance();
        perfLogManager.startLogging(hotLogPathPerf, "PERF_" + logFilename);

        CInterventionLogManager.getInstance().startLogging(interventionLogPath,
                "INT_" + logFilename);

        // TODO : implement time stamps
        logManager.postDateTimeStamp(GRAPH_MSG, "RTSuiteHome:SessionStart");
        logManager.postEvent_I(GRAPH_MSG, "EngineVersion:" + VERSION_RT);
    }

    private int getNextLogSequenceId() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);

        // grab the current sequence id (the one we should use for this current run
        // of the app
        final int logSequenceId = prefs.getInt(LOG_SEQUENCE_ID, 0);

        // increase the log sequence id by 1 for the next usage
        prefs.edit()
                .putInt(LOG_SEQUENCE_ID, logSequenceId + 1)
                .apply();

        return logSequenceId;
    }

    /**
     * launch RoboTransfer, a service to transfer log files
     */
    private void launchFtpService() {

        Intent ftpIntent = new Intent();
        ftpIntent.setComponent(
                new ComponentName(ftpPackage , ftpStartReceiver)
        );

        ftpIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

        Log.i(DEBUG_TAG, "Launching RoboTransfer... " + ftpIntent.getComponent());

        sendBroadcast(ftpIntent);
    }


    /**
     *
     *
     * @param active
     * @throws SecurityException if this package doesn't have Device Owner privileges
     */
    private void setDefaultKioskPolicies (boolean active) throws SecurityException {

        // set user restrictions
        setUserRestriction(DISALLOW_SAFE_BOOT, active);
        setUserRestriction(DISALLOW_FACTORY_RESET, active);
        setUserRestriction(DISALLOW_ADD_USER, active);
        setUserRestriction(DISALLOW_MOUNT_PHYSICAL_MEDIA, active);

        // XXX disable keyguard and status bar
        mDevicePolicyManager.setKeyguardDisabled(mAdminComponentName, active);
        mDevicePolicyManager.setStatusBarDisabled(mAdminComponentName, active);


        // XXX default home screen
        if (active) {
            // create an intent filter
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
            intentFilter.addCategory(Intent.CATEGORY_HOME);
            intentFilter.addCategory(Intent.CATEGORY_DEFAULT);

            // set as default home screen
            mDevicePolicyManager.addPersistentPreferredActivity(mAdminComponentName, intentFilter,
                    new ComponentName(mPackageName, HomeActivity.class.getName()));
        } else {
            // otherwise clear deafult home screen
            mDevicePolicyManager.clearPackagePersistentPreferredActivities(mAdminComponentName, mPackageName);
        }

        // XXX setLockTaskPackages
        mDevicePolicyManager.setLockTaskPackages(mAdminComponentName,
                active ? mKioskPackages.toArray(new String[]{}) : new String[]{});


    }

    /**
     * for setting user restrictions
     *
     * @param restriction
     * @param disallow
     */
    private void setUserRestriction(String restriction, boolean disallow) {
        if (disallow) {
            mDevicePolicyManager.addUserRestriction(mAdminComponentName, restriction);
        } else {
            mDevicePolicyManager.clearUserRestriction(mAdminComponentName, restriction);
        }
    }

    /**
     * Backdoor to allow exiting kiosk mode
     */
    public void onBackdoorPressed() {
        stopLockTask();
        setDefaultKioskPolicies(false);

        mPackageManager.setComponentEnabledSetting(
                new ComponentName(getPackageName(), getClass().getName()),
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP);

        //finish();
    }


    private void setFullScreen() {


        ((View) masterContainer).setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }


    @Override
    public void onStartTutor() {

        Intent launchIntent;
        if (LAUNCH_FACELOGIN) {
            Log.w(DEBUG_TAG, "Starting FaceLogin");
            launchIntent = mPackageManager.getLaunchIntentForPackage(flPackage);
        } else {
            Log.w(DEBUG_TAG, "Starting RoboTutor");
            launchIntent = mPackageManager.getLaunchIntentForPackage(rtPackage);
            Bundle sessionBundle = new Bundle();
            String uniqueUserID = Build.SERIAL;
            sessionBundle.putString(STUDENT_ID_VAR, uniqueUserID);

            String newSessId = generateSessionID();
            sessionBundle.putString(SESSION_ID_VAR, newSessId);
            launchIntent.putExtras(sessionBundle);
            launchIntent.setFlags(0);
        }

        if(launchIntent != null) {
            stopLockTask();
            startActivity(launchIntent);
        } else {
            Toast.makeText(getApplicationContext(), "Please install FaceLogin", Toast.LENGTH_LONG).show();
        }

    }

    /**
     * Generates a unique SessionID for RoboTutor
     *
     * @return
     */
    private String generateSessionID() {
        String deviceId = Build.SERIAL;
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return deviceId + "_" + timestamp;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Configuration.getPinningMode(this)) {
            // start lock task mode if it's not already active
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            // ActivityManager.getLockTaskModeState api is not available in pre-M
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                if (!am.isInLockTaskMode()) {
                    startLockTask();
                }
            } else {
                if (am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                    startLockTask();
                }
            }
        }

        setFullScreen();
    }
}
