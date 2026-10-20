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

package com.android.settings.privacy;

import android.content.Context;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

/**
 * Controller for the preference that picks after how long the clipboard is cleared.
 */
public class ClipboardAutoClearPreferenceController extends BasePreferenceController
        implements Preference.OnPreferenceChangeListener {

    public ClipboardAutoClearPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void updateState(Preference preference) {
        final ListPreference listPreference = (ListPreference) preference;
        final long timeout = Settings.Secure.getLong(mContext.getContentResolver(),
                Settings.Secure.CLIPBOARD_AUTO_CLEAR_TIMEOUT, 0);
        int index = listPreference.findIndexOfValue(String.valueOf(timeout));
        if (index < 0) {
            index = listPreference.findIndexOfValue("0");
        }
        listPreference.setValueIndex(index);
        listPreference.setSummary(listPreference.getEntries()[index]);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Settings.Secure.putLong(mContext.getContentResolver(),
                Settings.Secure.CLIPBOARD_AUTO_CLEAR_TIMEOUT, Long.parseLong((String) newValue));
        updateState(preference);
        return true;
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_privacy;
    }
}
