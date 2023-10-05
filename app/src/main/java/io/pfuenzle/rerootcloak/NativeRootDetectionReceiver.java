package io.pfuenzle.rerootcloak;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class NativeRootDetectionReceiver extends BroadcastReceiver {
    private static RootUtil mRootShell;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        mRootShell = new RootUtil();
        if (!mRootShell.haveRootShell()) {
            return;
        }

        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            upgradeLibrary(context);
            return;
        }

        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && !Common.REFRESH_APPS_INTENT.equals(intent.getAction())) {
            return;
        }

        if (Common.REFRESH_APPS_INTENT.equals(intent.getAction())) {
            resetNativeHooks(context);
        }

        applyNativeHooks(context);
    }

    private void applyNativeHooks(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> nativeHookingApps = prefs.getStringSet("remove_native_root_detection_apps",
                new HashSet<String>());
        boolean libraryInstalled = prefs.getBoolean("native_library_installed", false);

        for (String app : nativeHookingApps) {
            String property = packageNameToProperty(app);
            String command = "setprop " + property + " 'logwrapper /data/local/rootcloak-wrapper.sh'";
            mRootShell.runCommand(command, context);
            mRootShell.runCommand("am force-stop " + app, context);
        }

        if (libraryInstalled && !nativeHookingApps.isEmpty()) {
            mRootShell.runCommand("chmod 755 /data/local/", context);
            mRootShell.runCommand("chmod 755 /data/local/librootcloak.so", context);
            mRootShell.runCommand("chmod 755 /data/local/rootcloak-wrapper.sh", context);

/*            WrappingSELinuxPolicy policy = new WrappingSELinuxPolicy();
            policy.inject();
            if (!policy.haveInjected()) {
                // try to use alternative tool
                mRootShell.runCommand("sepolicy-inject -s untrusted_app -t zygote -c fifo_file -p write -l", context);
            }*/
            //TODO find modern alternative
        }
    }

    private void resetNativeHooks(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> nativeHookingApps = prefs.getStringSet("reset_native_root_detection_apps",
                new HashSet<String>());

        for (String app : nativeHookingApps) {
            String property = packageNameToProperty(app);
            String command = "setprop " + property + " ''";

            mRootShell.runCommand(command, context);
            mRootShell.runCommand("am force-stop " + app, context);
        }
    }

    private String packageNameToProperty(String packageName) {
        String property = "wrap." + packageName;
        if (property.length() > 31) {
            // Avoid creating an illegal property name when truncating.
            if (property.charAt(30) != '.') {
                property = property.substring(0, 31);
            } else {
                property = property.substring(0, 30);
            }
        }

        return property;
    }

    private void upgradeLibrary(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean libraryInstalled = prefs.getBoolean("native_library_installed", false);
        String library = context.getApplicationInfo().nativeLibraryDir + File.separator + "librootcloak.so";

        if (!libraryInstalled && !new File(library).exists()) {
            return;
        }

        mRootShell.runCommand("cp '" + library + "' /data/local/", context);
        mRootShell.runCommand("chmod 755 /data/local/librootcloak.so", context);
    }

/*    private class WrappingSELinuxPolicy extends eu.chainfire.libsuperuser.Policy {
        @Override
        protected String[] getPolicies() {
            return new String[]{
                    "allow untrusted_app zygote fifo_file { write }"
            };
        }
    }*/
}
