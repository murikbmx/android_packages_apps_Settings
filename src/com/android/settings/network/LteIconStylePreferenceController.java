/*
 * Copyright (C) 2026 The LineageOS Project
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

package com.android.settings.network;

import android.content.Context;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settings.core.BasePreferenceController;

/**
 * Controller for the preference that picks whether LTE is shown as LTE or 4G in the status bar.
 */
public class LteIconStylePreferenceController extends BasePreferenceController
        implements Preference.OnPreferenceChangeListener {

    public LteIconStylePreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void updateState(Preference preference) {
        final ListPreference listPreference = (ListPreference) preference;
        final int style = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.STATUS_BAR_LTE_ICON_STYLE, 0);
        int index = listPreference.findIndexOfValue(String.valueOf(style));
        if (index < 0) {
            index = listPreference.findIndexOfValue("0");
        }
        listPreference.setValueIndex(index);
        listPreference.setSummary(listPreference.getEntries()[index]);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.STATUS_BAR_LTE_ICON_STYLE, Integer.parseInt((String) newValue));
        updateState(preference);
        return true;
    }
}
