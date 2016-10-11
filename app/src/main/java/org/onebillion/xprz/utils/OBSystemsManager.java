package org.onebillion.xprz.utils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.DeadObjectException;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Display;
import android.widget.Toast;

import org.onebillion.xprz.controls.OBLabel;
import org.onebillion.xprz.mainui.MainActivity;
import org.onebillion.xprz.receivers.OBBatteryReceiver;
import org.onebillion.xprz.receivers.OBDeviceAdminReceiver;
import org.onebillion.xprz.receivers.OBSettingsContentObserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by pedroloureiro on 31/08/16.
 */
public class OBSystemsManager
{
    public static OBSystemsManager sharedManager;

    private OBBatteryReceiver batteryReceiver;
    private OBBrightnessManager brightnessManager;
    private OBExpansionManager expansionManager;
    private Map<String, List<String>> memoryUsageMap;

    public Handler mainHandler;
    public OBLabel statusLabel;

    public OBSettingsContentObserver settingsContentObserver;
    public OBConnectionManager connectionManager;

    private boolean AppIsInForeground;
    private final int keepInForegroundInterval = 1000;
    private boolean suspended;
    private boolean kioskModeActive = false;


    public OBSystemsManager ()
    {
        batteryReceiver = new OBBatteryReceiver();
        settingsContentObserver = new OBSettingsContentObserver(MainActivity.mainActivity, new Handler());
        brightnessManager = new OBBrightnessManager();
        expansionManager = new OBExpansionManager();
        connectionManager = new OBConnectionManager();
        suspended = true;
        //
        memoryUsageMap = new HashMap<String, List<String>>();
        //
        sharedManager = this;
    }

    public boolean hasWriteSettingsPermission()
    {
        return Settings.System.canWrite(MainActivity.mainActivity);
    }

    public boolean isAppIsInForeground ()
    {
        return AppIsInForeground;
    }


    public boolean isScreenOn (Context context)
    {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH)
        {
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            boolean screenOn = false;
            for (Display display : dm.getDisplays())
            {
                if (display.getState() != Display.STATE_OFF)
                {
                    screenOn = true;
                }
            }
            return screenOn;
        }
        else
        {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            //noinspection deprecation
            return pm.isScreenOn();
        }
    }


    private boolean isMyServiceRunning (Class<?> serviceClass)
    {
        ActivityManager manager = (ActivityManager) MainActivity.mainActivity.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
        {
            if (serviceClass.getName().equals(service.service.getClassName()))
            {
                return true;
            }
        }
        return false;
    }


    public void killAllServices ()
    {
        MainActivity.log("OBSystemsManager.killing all services");
        //
        MainActivity.mainActivity.stopService(new Intent(MainActivity.mainActivity, OBAutoStartActivityService.class));
    }


    public void startServices ()
    {
        if (suspended) return;
        if (!OBSystemsManager.isFirstSetupComplete()) return;
        //
        MainActivity.log("OBSystemsManager.starting all services");
        //
        String restartAfterCrash = MainActivity.mainActivity.configStringForKey(MainActivity.CONFIG_RESTART_AFTER_CRASH);
        boolean shouldRestartAfterCrash = restartAfterCrash != null && restartAfterCrash.equals("true");
        //
        if (!isMyServiceRunning(OBAutoStartActivityService.class) && shouldRestartAfterCrash)
        {
            MainActivity.log("OBAutoStartActivityService was not running. Starting now");
            //
            Intent serviceIntent = new Intent(MainActivity.mainActivity, OBAutoStartActivityService.class);
            MainActivity.mainActivity.startService(serviceIntent);
        }
        //
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_USER_PRESENT);
    }


    public static boolean isFirstSetupComplete ()
    {
        String result = MainActivity.mainActivity.getPreferences("firstSetupComplete");
        return result != null && result.equals("true");
    }


    public void runChecks ()
    {
        OBUtils.runOnOtherThread(new OBUtils.RunLambda()
        {
            @Override
            public void run () throws Exception
            {
                disableNavigationBar(); // may cause restart
                //
                pinApplication();
                //
                startServices();
                //
                if (mainHandler == null)
                {
                    mainHandler = new Handler(MainActivity.mainActivity.getMainLooper());
                }
                //
                connectionManager.sharedManager.checkForConnection();
                //
                OBSQLiteHelper.getSqlHelper().runMaintenance();
                runChecksumComparisonTest();
            }
        });
    }


    public void printMemoryStatus (String message)
    {
        ActivityManager activityManager = (ActivityManager) MainActivity.mainActivity.getSystemService(MainActivity.ACTIVITY_SERVICE);
        int id = android.os.Process.myPid();
        int[] list = {id};
        Debug.MemoryInfo result[] = activityManager.getProcessMemoryInfo(list);

        if (message != null) MainActivity.log("Memory status: " + message);

        for (Debug.MemoryInfo info : result)
        {
            Map<String, String> map = info.getMemoryStats();
            for (String key : map.keySet())
            {
                List<String> memoryEntry = memoryUsageMap.get(key);
                if (memoryEntry == null) memoryEntry = new ArrayList<String>();
                memoryEntry.add(map.get(key));
                memoryUsageMap.put(key, memoryEntry);
            }
        }
        //
        for (String key : memoryUsageMap.keySet())
        {
            List<String> values = memoryUsageMap.get(key);
            if (values == null) continue;
            if (values.isEmpty()) continue;
            //
            long firstValue = Long.parseLong(values.get(0));
            long lastValue = Long.parseLong(values.get(values.size() - 1));
            if (values.size() > 2)
            {
                long secondLastValue = Long.parseLong(values.get(values.size() - 2));
                long diff = lastValue - secondLastValue;
                long fullDiff = lastValue - firstValue;
                MainActivity.log("Memory status: " + key + " --> " + lastValue + " (" + ((diff > 0) ? "+" : "") + diff + ")  --> since beginning (" + ((fullDiff > 0) ? "+" : "") + fullDiff + ")");
            }
            else
            {
                long diff = lastValue - firstValue;
                MainActivity.log("Memory status: " + key + " --> " + lastValue + " (" + ((diff > 0) ? "+" : "") + diff + ")");
            }
        }
    }


    public void runBatterySavingMode ()
    {
        OBConnectionManager.sharedManager.disconnectWifiIfAllowed();
        OBConnectionManager.sharedManager.setBluetooth(false);
        //
        killBackgroundProcesses();
    }


    public void killBackgroundProcesses ()
    {
        ActivityManager activityManager = (ActivityManager) MainActivity.mainActivity.getSystemService(MainActivity.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> procInfo = activityManager.getRunningAppProcesses();
        if (procInfo != null)
        {
            for (ActivityManager.RunningAppProcessInfo process : procInfo)
            {
                int importance = process.importance;
                int pid = process.pid;
                String name = process.processName;
                //
                if (name.equals("manager.main")) continue;
                if (importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE)
                {
                    continue;
                }
                activityManager.killBackgroundProcesses(name);
            }
        }
    }


    public void onResume ()
    {
        MainActivity.log("OBSystemsManager.onResume detected");
        //
        AppIsInForeground = true;
        //
        if (batteryReceiver != null)
        {
            MainActivity.mainActivity.registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        }
        //
        if (settingsContentObserver != null)
        {
            settingsContentObserver.onResume();
        }
        if (expansionManager != null)
        {
            MainActivity.mainActivity.registerReceiver(OBExpansionManager.sharedManager.downloadCompleteReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
        OBBrightnessManager.sharedManager.onResume();
    }


    public void onPause ()
    {
        MainActivity.log("OBSystemsManager.onPause detected" + (MainActivity.mainActivity.isFinishing() ? " and is finishing!" : ""));
        //
        AppIsInForeground = false;
        //
//        startForegroundCheck();
        //
        if (batteryReceiver != null)
        {
            MainActivity.mainActivity.unregisterReceiver(batteryReceiver);
        }
        //
        if (settingsContentObserver != null)
        {
            settingsContentObserver.onPause();
        }
        if (OBExpansionManager.sharedManager.downloadCompleteReceiver != null)
        {
            MainActivity.mainActivity.unregisterReceiver(OBExpansionManager.sharedManager.downloadCompleteReceiver);
        }
        OBBrightnessManager.sharedManager.onPause();
        //
//        OBSQLiteHelper.getSqlHelper().emergencyRestore();
    }


    public void onDestroy ()
    {
        MainActivity.log("OBSystemsManager.onDestroy detected");
    }


    public void onStop ()
    {
        if (MainActivity.mainActivity.isFinishing())
        {
            MainActivity.log("OBSystemsManager.onStop detected");
            //
            shutdownProcedures();
            //
            OBBrightnessManager.sharedManager.onStop();
        }
    }


    public void onSuspend ()
    {
        MainActivity.log("OBSystemsManager.onSuspend detected");
        //
        suspended = true;
        //
        killAllServices();
        //
        OBBrightnessManager.sharedManager.onSuspend();
    }


    public void onContinue ()
    {
        MainActivity.log("OBSystemsManager.onContinue detected");
        //
        suspended = false;
        //
        OBBrightnessManager.sharedManager.onContinue();
        //
        runBatterySavingMode();
    }


    public void refreshStatus ()
    {
        String info = String.format("Battery [%s]    Brightness[%s]    Volume[%s]", batteryReceiver.printStatus(), brightnessManager.printStatus(), settingsContentObserver.printVolumeStatus());
        //
        if (statusLabel != null)
        {
            statusLabel.setString(info);
        }
    }


    public void setStatusLabel (OBLabel label)
    {
        statusLabel = label;
        refreshStatus();
    }


    private void runChecksumComparisonTest ()
    {
        MainActivity.log("Checksum comparison BEGIN");
        OBXMLManager xmlManager = new OBXMLManager();
        List<File> checkSumFiles = OBExpansionManager.sharedManager.getChecksumFiles();
        for (File file : checkSumFiles)
        {
            try
            {
                List<OBXMLNode> xml = xmlManager.parseFile(new FileInputStream(file));
                OBXMLNode rootNode = xml.get(0);
                //
                for (OBXMLNode node : rootNode.children)
                {
                    String path = node.attributeStringValue("id");
                    String md5 = node.attributeStringValue("md5");
                    InputStream is = OBUtils.getInputStreamForPath(path);
                    Boolean result = checkMD5(md5, is);
                    if (!result)
                    {
                        MainActivity.log("Checksum comparison: Problem detected with asset " + path);
                    }
//                    MainActivity.log("CHECKSUM COMPARISON --> " + md5 + "\t" + ((is == null) ? "NOT FOUND" : "FOUND" + "\t" + (result ? "OK" : "ERROR") + "\t" + path));
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        MainActivity.log("Checksum comparison END");
    }


    public static boolean checkMD5 (String md5, InputStream is)
    {
        if (TextUtils.isEmpty(md5) || is == null)
        {
            MainActivity.log("MD5 string empty or inputStream null");
            return false;
        }
        //
        String calculatedDigest = calculateMD5(is);
        if (calculatedDigest == null)
        {
            MainActivity.log("calculatedDigest null");
            return false;
        }
//        MainActivity.log("Calculated digest: " + calculatedDigest);
//        MainActivity.log("Provided digest: " + md5);
        //
        return calculatedDigest.equalsIgnoreCase(md5);
    }


    public static String calculateMD5 (InputStream is)
    {
        MessageDigest digest;
        try
        {
            digest = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e)
        {
            MainActivity.log("Exception while getting digest");
            e.printStackTrace();
            return null;
        }

        byte[] buffer = new byte[8192];
        int read;
        try
        {
            while ((read = is.read(buffer)) > 0)
            {
                digest.update(buffer, 0, read);
            }
            byte[] md5sum = digest.digest();
            BigInteger bigInt = new BigInteger(1, md5sum);
            String output = bigInt.toString(16);
            // Fill to 32 chars
            output = String.format("%32s", output).replace(' ', '0');
            return output;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to process file for MD5", e);
        }
        finally
        {
            try
            {
                is.close();
            }
            catch (IOException e)
            {
                MainActivity.log("Exception on closing MD5 input stream");
                e.printStackTrace();
            }
        }
    }


    public boolean enableAdminstratorPrivileges ()
    {
        MainActivity.log("OBSystemsManager.enableAdminstratorPrivileges");
        //
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) MainActivity.mainActivity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminReceiver = OBDeviceAdminReceiver.getComponentName(MainActivity.mainActivity);
        //
        if (!devicePolicyManager.isAdminActive(adminReceiver))
        {
            MainActivity.log("OBSystemsManager.enableAdminstratorPrivileges.requesting administrator privileges");
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminReceiver);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "");
            MainActivity.mainActivity.startActivityForResult(intent, MainActivity.REQUEST_FIRST_SETUP_ADMINISTRATOR_PRIVILEGES);
            return false;
        }
        if (!devicePolicyManager.isDeviceOwnerApp(MainActivity.mainActivity.getPackageName()))
        {
            MainActivity.log("OBSystemsManager.enableAdminstratorPrivileges.requesting device owner");
            String packageName = MainActivity.mainActivity.getPackageName();
            try
            {
//                String resetCommand = "su -c rm /data/system/device_owner.xml; su -c rm /data/system/device_policies.xml;";
                String command = "su -c dpm set-device-owner " + packageName + "/" + adminReceiver.getClassName();
                MainActivity.log("OBSystemsManager.not a device owner. running " + command);
                //
                Process process = Runtime.getRuntime().exec(command);
                process.waitFor();
                //
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder log = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null)
                {
                    log.append(line + "\n");
                }
                bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                while ((line = bufferedReader.readLine()) != null)
                {
                    log.append(line + "\n");
                }
                String output = log.toString();
                MainActivity.log("OBSystemsManager.output from process: " + output);
                //
                if (output.contains("Active admin set to component"))
                {
                    MainActivity.log("OBSystemsManager. App is now set to device owner");
                    return true;
                }
                else
                {
                    MainActivity.log("OBSystemsManager. Another account was detected. Requesting user to remove them"); // no idea how to do this programmatically
                    //
                    Toast.makeText(MainActivity.mainActivity, "Please remove all accounts before going back", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
                    MainActivity.mainActivity.startActivityForResult(intent, MainActivity.REQUEST_FIRST_SETUP_ADMINISTRATOR_PRIVILEGES);
                    //
                    return false;
                }
            }
            catch (Exception e)
            {
                MainActivity.log("OBSystemsManager.device is not rooted. No point in continuing enableAdminstratorPrivileges");
                //
                e.printStackTrace();
                //
                return true;
            }
        }
        MainActivity.log("OBSystemsManager.enableAdminstratorPrivileges. all good");
        return true;
    }


    public void disableAdministratorPrivileges ()
    {
        MainActivity.log("OBSystemsManager.disableAdministratorPrivileges");
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) MainActivity.mainActivity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminReceiver = OBDeviceAdminReceiver.getComponentName(MainActivity.mainActivity);
        //
        if (devicePolicyManager.isDeviceOwnerApp(MainActivity.mainActivity.getPackageName()))
        {
            devicePolicyManager.setKeyguardDisabled(adminReceiver, false);
            MainActivity.log("OBSystemsManager.keyguard restored");
            //
            devicePolicyManager.setStatusBarDisabled(adminReceiver, false);
            MainActivity.log("OBSystemsManager.status bar restored");
        }
        //
        try
        {
            devicePolicyManager.removeActiveAdmin(adminReceiver);
            devicePolicyManager.clearDeviceOwnerApp(MainActivity.mainActivity.getPackageName());
        }
        catch (Exception e)
        {
            // App might not be the device owner at this point
        }
        if (kioskModeActive)
        {
            MainActivity.mainActivity.stopLockTask();
            kioskModeActive = false;
        }
    }


    public void pinApplication ()
    {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) MainActivity.mainActivity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminReceiver = OBDeviceAdminReceiver.getComponentName(MainActivity.mainActivity);
        //
        String hideStatusBar = MainActivity.mainActivity.configStringForKey(MainActivity.CONFIG_HIDE_STATUS_BAR);
        if (hideStatusBar != null && hideStatusBar.equals("true"))
        {
            if (devicePolicyManager.isDeviceOwnerApp(MainActivity.mainActivity.getPackageName()))
            {
                MainActivity.log("OBSystemsManager.pinning app");
                String[] packages = {MainActivity.mainActivity.getPackageName()};
                devicePolicyManager.setLockTaskPackages(adminReceiver, packages);
                //
                if (devicePolicyManager.isLockTaskPermitted(MainActivity.mainActivity.getPackageName()))
                {
                    MainActivity.log("OBSystemsManager.starting locked task");
                    MainActivity.mainActivity.startLockTask();
                    kioskModeActive = true;
                }
            }
            else
            {
                MainActivity.log("OBSystemsManager.unable to disable status bar. not a device owner");
            }
        }
        toggleKeyguardAndStatusBar(false);
    }


    public void toggleKeyguardAndStatusBar (boolean status)
    {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) MainActivity.mainActivity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminReceiver = OBDeviceAdminReceiver.getComponentName(MainActivity.mainActivity);
        //
        if (devicePolicyManager.isDeviceOwnerApp(MainActivity.mainActivity.getPackageName()))
        {
            devicePolicyManager.setKeyguardDisabled(adminReceiver, !status);
            MainActivity.log("OBSystemsManager.keyguard has been " + (status ? "enabled" : "disabled"));
            //
            devicePolicyManager.setStatusBarDisabled(adminReceiver, !status);
            MainActivity.log("OBSystemsManager.status bar has been " + (status ? "enabled" : "disabled"));
        }
    }


    public void screenLock()
    {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) MainActivity.mainActivity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        devicePolicyManager.lockNow();
    }

    public void shutdownProcedures ()
    {
        MainActivity.log("OBSystemManager.shutdownProcedures");
        // the shutdown procedure should only be triggered for debug mode, as we want the app to restart automatically and not lose any setting
        if (MainActivity.mainActivity.isDebugMode())
        {
            // enable keyguard and status bar
            toggleKeyguardAndStatusBar(true);
            //
            // removing administrator privileges (so we can uninstall the app)
            String restartAfterCrash = MainActivity.mainActivity.configStringForKey(MainActivity.CONFIG_RESTART_AFTER_CRASH);
            if (restartAfterCrash == null || restartAfterCrash.equals("false"))
            {
                disableAdministratorPrivileges();
            }
            //
            // reset the screen timeout to 10mins
            OBBrightnessManager.sharedManager.setScreenTimeout(10 * 60 * 1000);
            //
            // kill services to prevent restart
            killAllServices();
            //
            // enable navigation bar
            enableNavigationBar();
        }
        //
        saveLogToFile();
    }


    public void toggleNavigationBar (final int value)
    {
        MainActivity.log("OBSystemsManager." + (value == 0 ? "enabling" : "disabling") + " navigation bar");
        try
        {
            String command = "getprop qemu.hw.mainkeys";
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            //
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder log = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null)
            {
                log.append(line + "\n");
            }
            bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = bufferedReader.readLine()) != null)
            {
                log.append(line + "\n");
            }
            String output = log.toString();
            MainActivity.log("OBSystemsManager.output from toggleNavigationBar: " + output);
            //
            if (output.contains(String.format("%d", value)))
            {
                MainActivity.log("OBSystemsManager.toggleNavigationBar. the system value is the same. nothing to do here");
                return;
            }
            command = "su -c setprop qemu.hw.mainkeys " + value + "; su -c stop; su -c start";
            MainActivity.log("OBSystemsManager.running:" + command);
            //
            try
            {
                Runtime.getRuntime().exec(command);
            }
            catch (Exception e)
            {
                MainActivity.log("OBSystemsManager.toggleNavigationBar. unable to execute command. Device is probably not rooted");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }


    public void disableNavigationBar ()
    {
        String hideNavigationBar = MainActivity.mainActivity.configStringForKey(MainActivity.CONFIG_HIDE_NAVIGATION_BAR);
        //
        if (hideNavigationBar != null && hideNavigationBar.equals("true"))
        {
            toggleNavigationBar(1);
        }
    }


    public void enableNavigationBar ()
    {
        toggleNavigationBar(0);
    }


    public void saveLogToFile ()
    {
        File sd = new File(Environment.getExternalStorageDirectory(), "//onebillion//logs//");
        sd.mkdirs();
        //
        if (sd.canWrite())
        {
            DateFormat df = new android.text.format.DateFormat();
            String date = df.format("yyyy.MM.dd.hh.mm.ss", new java.util.Date()).toString();
            File file = new File(sd, String.format("%s.txt", date));
            try
            {
                MainActivity.log("Log exported to " + file.getAbsolutePath());
                @SuppressWarnings("unused")
                Process process = Runtime.getRuntime().exec("logcat -df " + file.getAbsolutePath());
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }


    public boolean usesAdministratorServices()
    {
        String value = MainActivity.mainActivity.configStringForKey(MainActivity.CONFIG_USE_ADMINISTRATOR_SERVICES);
        return (value != null && value.equals("true"));
    }

}
