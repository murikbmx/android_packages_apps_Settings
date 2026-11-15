/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.display;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lists launchable apps and lets the user pick a display refresh rate for each. */
public class AppRefreshRateFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    /** Read by RefreshRatePolicy in system_server. Format: "pkg:rate,pkg:rate". */
    private static final String SETTING = "lineage_app_refresh_rate";
    private static final String VALUE_DEFAULT = "0";

    private CharSequence[] mEntries;
    private CharSequence[] mValues;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DISPLAY;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.placeholder_preference_screen);
        getActivity().setTitle(R.string.app_refresh_rate_title);

        final List<Integer> rates =
                AppRefreshRatePreferenceController.getSupportedRefreshRates(getActivity());
        mEntries = new CharSequence[rates.size() + 1];
        mValues = new CharSequence[rates.size() + 1];
        mEntries[0] = getString(R.string.app_refresh_rate_default);
        mValues[0] = VALUE_DEFAULT;
        for (int i = 0; i < rates.size(); i++) {
            mEntries[i + 1] = getString(R.string.app_refresh_rate_hz, rates.get(i));
            mValues[i + 1] = String.valueOf(rates.get(i));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        init();
    }

    private void init() {
        final PreferenceGroup screen = getPreferenceScreen();
        screen.removeAll();
        final Context context = getActivity();
        final PackageManager pm = context.getPackageManager();
        final Map<String, String> rates = readRates();

        final Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(launcherIntent, 0));
        final Collator collator = Collator.getInstance();
        final Map<ResolveInfo, CharSequence> labels = new LinkedHashMap<>();
        for (ResolveInfo app : apps) {
            labels.put(app, app.loadLabel(pm));
        }
        apps.sort((a, b) -> collator.compare(labels.get(a).toString(),
                labels.get(b).toString()));

        final Set<String> added = new HashSet<>();
        for (ResolveInfo app : apps) {
            final String packageName = app.activityInfo.packageName;
            if (!added.add(packageName)) {
                continue;
            }
            final ListPreference p = new ListPreference(getPrefContext());
            p.setKey(packageName);
            p.setTitle(labels.get(app));
            p.setDialogTitle(labels.get(app));
            p.setIcon(app.loadIcon(pm));
            p.setEntries(mEntries);
            p.setEntryValues(mValues);
            p.setValue(rates.getOrDefault(packageName, VALUE_DEFAULT));
            if (p.findIndexOfValue(p.getValue()) < 0) {
                p.setValue(VALUE_DEFAULT);
            }
            p.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            p.setOnPreferenceChangeListener(this);
            screen.addPreference(p);
        }
    }

    private Map<String, String> readRates() {
        final Map<String, String> rates = new LinkedHashMap<>();
        final String value = Settings.System.getString(getContentResolver(), SETTING);
        if (TextUtils.isEmpty(value)) {
            return rates;
        }
        for (String entry : value.split(",")) {
            final int sep = entry.lastIndexOf(':');
            if (sep > 0) {
                rates.put(entry.substring(0, sep), entry.substring(sep + 1));
            }
        }
        return rates;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final Map<String, String> rates = readRates();
        if (VALUE_DEFAULT.equals(newValue)) {
            rates.remove(preference.getKey());
        } else {
            rates.put(preference.getKey(), (String) newValue);
        }
        final List<String> entries = new ArrayList<>();
        for (Map.Entry<String, String> e : rates.entrySet()) {
            entries.add(e.getKey() + ":" + e.getValue());
        }
        Settings.System.putString(getContentResolver(), SETTING,
                TextUtils.join(",", entries));
        return true;
    }
}
