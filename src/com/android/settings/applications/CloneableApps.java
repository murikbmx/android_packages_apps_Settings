/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.applications;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Apps that can be cloned into the clone profile: the framework allowlist plus every
 * user-installed (non-system) app with a launcher activity, e.g. a second Telegram or WhatsApp.
 */
public final class CloneableApps {

    private CloneableApps() {}

    private static Intent launcherIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    }

    private static boolean isUserApp(Context context, ApplicationInfo info) {
        if (info == null) return false;
        if ((info.flags & (ApplicationInfo.FLAG_SYSTEM
                | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
            return false;
        }
        return !context.getPackageName().equals(info.packageName);
    }

    private static Set<String> allowlist(Context context) {
        return new HashSet<>(Arrays.asList(context.getResources()
                .getStringArray(com.android.internal.R.array.cloneable_apps)));
    }

    /** All cloneable package names for the current user. Do not call on the main thread. */
    public static Set<String> get(Context context) {
        final Set<String> result = allowlist(context);
        final List<ResolveInfo> activities =
                context.getPackageManager().queryIntentActivities(launcherIntent(), 0);
        for (ResolveInfo ri : activities) {
            if (ri.activityInfo != null && isUserApp(context, ri.activityInfo.applicationInfo)) {
                result.add(ri.activityInfo.packageName);
            }
        }
        return result;
    }

    /** Whether {@code packageName} can be cloned. Do not call on the main thread. */
    public static boolean isCloneable(Context context, String packageName) {
        if (allowlist(context).contains(packageName)) return true;
        final PackageManager pm = context.getPackageManager();
        final List<ResolveInfo> activities =
                pm.queryIntentActivities(launcherIntent().setPackage(packageName), 0);
        for (ResolveInfo ri : activities) {
            if (ri.activityInfo != null && isUserApp(context, ri.activityInfo.applicationInfo)) {
                return true;
            }
        }
        return false;
    }
}
