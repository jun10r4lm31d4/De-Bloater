package com.sunilpaulmathew.debloater.utils;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.view.inputmethod.InputMethodManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/*
 * Created by sunilpaulmathew <sunil.kde@gmail.com> on October 27, 2020
 */

public class PackageTasks {

    static void createModuleParent() {
        Utils.runCommand(Utils.magiskBusyBox() + "mkdir " + Common.getModuleParent());
    }

    public static List<PackageItem> getRawData(Context context) {
        List<PackageItem> mData = new ArrayList<>();
        List<ApplicationInfo> packages = getPackageManager(context).getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo packageInfo: packages) {
            if ((packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                mData.add(new PackageItem(
                        getAppName(packageInfo.packageName, context),
                        isUpdatedSystemApp(packageInfo.packageName, context) ? findSystemAPKPath(packageInfo.packageName,
                                context) : getAPKPath(packageInfo.packageName, context),
                        getAppIcon(packageInfo.packageName, context),
                        packageInfo.packageName,
                        isUpdatedSystemApp(packageInfo.packageName, context)));
            }
        }
        return mData;
    }

    public static List<PackageItem> getActivePackageData(Context context) {
        List<PackageItem> mData = new ArrayList<>();
        for (PackageItem item : Common.getRawData()) {
            if (getSupportedAppsList(item.getAPKPath(), context)) {
                if (Common.getSearchText() == null) {
                    mData.add(item);
                } else if (Common.isTextMatched(item.getAppName())) {
                    mData.add(item);
                }
            }
        }
        if (Utils.getBoolean("sort_name", true, context)) {
            Collections.sort(mData, (lhs, rhs) -> String.CASE_INSENSITIVE_ORDER.compare(lhs.getAppName(), rhs.getAppName()));
        } else {
            Collections.sort(mData, (lhs, rhs) -> String.CASE_INSENSITIVE_ORDER.compare(lhs.getPackageName(), rhs.getPackageName()));
        }
        if (Utils.getBoolean("reverse_order", false, context)) {
            Collections.reverse(mData);
        }
        return mData;
    }

    public static List<String> getInactivePackageData() {
        List<String> mData = new ArrayList<>();
        for (String line : Utils.runAndGetOutput(Utils.magiskBusyBox() + "find " + Common.getModuleParent() + "/system -type f -name *.apk").split("\\r?\\n")) {
            if (line.endsWith(".apk")) {
                mData.add(line);
            }
        }
        return mData;
    }

    public static boolean isSystemApp(String packageName, Context context) {
        try {
            return (Objects.requireNonNull(getAppInfo(packageName, context)).flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (NullPointerException ignored) {}
        return false;
    }

    public static boolean isUpdatedSystemApp(String packageName, Context context) {
        try {
            return (Objects.requireNonNull(getAppInfo(packageName, context)).flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        } catch (NullPointerException ignored) {}
        return false;
    }

    private static boolean getSupportedAppsList(String apkPath, Context context) {
        String mStatus = Utils.getString("appTypes", "all", context);
        boolean systemApps = apkPath.startsWith("/system/app") || apkPath.startsWith("/system/priv-app")
                || apkPath.startsWith("/system/product/app") || apkPath.startsWith("/system/product/priv-app")
                || apkPath.startsWith("/system/vendor/app") || apkPath.startsWith("/system/vendor/overlay")
                || apkPath.startsWith("/system/product/overlay") || apkPath.startsWith("/system/system_ext/app")
                || apkPath.startsWith("/system/system_ext/priv-app") || apkPath.startsWith("/system_ext/app")
                || apkPath.startsWith("/system_ext/priv-app");
        boolean vendorApps = apkPath.startsWith("/vendor/overlay") || apkPath.startsWith("/vendor/app");
        boolean productApps = apkPath.startsWith("/product/app") || apkPath.startsWith("/product/priv-app")
                || apkPath.startsWith("/product/overlay");
        switch (mStatus) {
            case "system":
                return systemApps;
            case "product":
                return productApps;
            case "vendor":
                return vendorApps;
            default:
                return true;
        }
    }

    public static PackageManager getPackageManager(Context context) {
        return context.getApplicationContext().getPackageManager();
    }

    public static ApplicationInfo getAppInfo(String packageName, Context context) {
        try {
            return getPackageManager(context).getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String getAppName(String packageName, Context context) {
        return getPackageManager(context).getApplicationLabel(Objects.requireNonNull(getAppInfo(
                packageName, context))).toString();
    }

    public static Drawable getAppIcon(String packageName, Context context) {
        return getPackageManager(context).getApplicationIcon(Objects.requireNonNull(getAppInfo(packageName, context)));
    }

    public static String getAPKPath(String packageName, Context context) {
        return Objects.requireNonNull(getAppInfo(packageName, context)).sourceDir;
    }

    public static String findSystemAPKPath(String packageName, Context context) {
        String mAPKPath = null;
        for (String line : Utils.runAndGetOutput("dumpsys package " + packageName + " | grep resourcePath").replace("resourcePath=","").split("\\r?\\n")) {
            if (!line.startsWith("/data/")) {
                mAPKPath = line.replaceAll("\\s+","");
                for (File mFile : Objects.requireNonNull(new File(mAPKPath).listFiles())) {
                    if (Objects.equals(getAPKId(mFile.getAbsolutePath(), context), packageName)) {
                        mAPKPath = mAPKPath + File.separator + mFile.getName();
                    }
                }
            }
        }
        if (Utils.exist(mAPKPath)) {
            return mAPKPath;
        } else {
            return getAPKPath(packageName, context);
        }
    }

    public static String getAdjAPKPath(String apkPath) {
        if (apkPath.startsWith("/product/")) {
            apkPath = apkPath.replace("/product", "/system/product");
        } else if (apkPath.startsWith("/vendor/")) {
            apkPath = apkPath.replace("/vendor", "/system/vendor");
        } else if (apkPath.startsWith("/system_ext/")) {
            apkPath = apkPath.replace("/system_ext", "/system/system_ext");
        }
        return apkPath;
    }

    private static PackageInfo getPackageInfo(String apkPath, Context context) {
        return getPackageManager(context).getPackageArchiveInfo(apkPath, 0);
    }

    public static CharSequence getAPKName(String apkPath, Context context) {
        if (getPackageInfo(apkPath, context) != null) {
            return getPackageInfo(apkPath, context).applicationInfo.loadLabel(getPackageManager(context));
        } else {
            return null;
        }
    }

    public static String getAPKId(String apkPath, Context context) {
        if (getPackageInfo(apkPath, context) != null) {
            return getPackageInfo(apkPath, context).applicationInfo.packageName;
        } else {
            return null;
        }
    }

    public static Drawable getAPKIcon(String apkPath, Context context) {
        if (getPackageInfo(apkPath, context) != null) {
            return getPackageInfo(apkPath, context).applicationInfo.loadIcon(getPackageManager(context));
        } else {
            return null;
        }
    }

    public static String getStoragePath() {
        return Environment.getExternalStorageDirectory().getPath();
    }

    public static String getModulePath() {
        return Common.getModuleParent();
    }

    public static void initializeModule() {
        if (!Utils.exist(Common.getModuleParent())) {
            createModuleParent();
            Utils.chmod("755", Common.getModuleParent());
            Utils.create("id=De-bloater\n" +
                            "name=De-bloater\n" +
                            "version=v1.0\n" +
                            "versionCode=1\n" +
                            "author=sunilpaulmathew\n" +
                            "description=De-bloat apps Systemless-ly",
                    Common.getModuleParent() + "/module.prop");
            Utils.chmod("644", Common.getModuleParent() + "/module.prop");
        }
    }

    public static void removeModule(Activity activity) {
        Utils.delete(activity.getFilesDir().getPath() + "/De-bloater");
        Utils.delete(Common.getModuleParent());
        Utils.saveBoolean("tomatot_extreme", false, activity);
        Utils.saveBoolean("tomatot_invisible", false, activity);
        Utils.saveBoolean("tomatot_light", false, activity);
    }

    public static boolean isModuleInitialized() {
        return Utils.exist(Common.getModuleParent()) && Utils.exist(Common.getModuleParent() + "/module.prop");
    }

    public static void setToDelete(String path, String name, Context context) {
        initializeModule();
        new File(context.getFilesDir().getPath() + "/De-bloater" + new File(path).getParentFile()).mkdirs();
        Utils.create(name, context.getFilesDir().getPath() + "/De-bloater" + path);
        Utils.copy(context.getFilesDir().getPath() + "/De-bloater/*", Common.getModuleParent());
        Utils.delete(context.getFilesDir().getPath() + "/De-bloater/*");
    }

    public static void revertDelete(String path) {
        Utils.delete(Common.getModuleParent() + path);
    }

    public static void toggleKeyboard(int mode, Context context) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (mode == 1) {
            if (Common.getSearchWord().requestFocus()) {
                imm.showSoftInput(Common.getSearchWord(), InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            imm.hideSoftInputFromWindow(Common.getSearchWord().getWindowToken(), 0);
        }
    }

}