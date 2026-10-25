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

package com.android.settings.backup;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.utils.ThreadUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Saves system settings and quick settings tiles to a file and restores them. */
public class SystemSettingsBackupFragment extends SettingsPreferenceFragment {
    private static final String TAG = "SystemSettingsBackup";

    private static final String KEY_BACKUP = "system_settings_backup_create";
    private static final String KEY_RESTORE = "system_settings_backup_restore";

    private static final String MIME_TYPE = "application/json";

    private ActivityResultLauncher<String> mCreateDocument;
    private ActivityResultLauncher<String[]> mOpenDocument;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCreateDocument = registerForActivityResult(
                new ActivityResultContracts.CreateDocument(MIME_TYPE), this::onBackupFileChosen);
        mOpenDocument = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::onRestoreFileChosen);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.system_settings_backup);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (KEY_BACKUP.equals(preference.getKey())) {
            final String date = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
                    .format(new Date());
            mCreateDocument.launch("settings-backup-" + date + ".json");
            return true;
        }
        if (KEY_RESTORE.equals(preference.getKey())) {
            mOpenDocument.launch(new String[] {MIME_TYPE, "application/octet-stream",
                    "text/plain"});
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void onBackupFileChosen(Uri uri) {
        if (uri == null) {
            return;
        }
        final Context context = requireContext().getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            String message;
            try {
                final int count = SystemSettingsBackup.backup(context, uri);
                message = context.getResources().getQuantityString(
                        R.plurals.system_settings_backup_done, count, count);
            } catch (Exception e) {
                Log.e(TAG, "Backup failed", e);
                message = context.getString(R.string.system_settings_backup_failed);
            }
            final String result = message;
            ThreadUtils.postOnMainThread(
                    () -> Toast.makeText(context, result, Toast.LENGTH_LONG).show());
        });
    }

    private void onRestoreFileChosen(Uri uri) {
        if (uri == null) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.system_settings_restore_confirm_title)
                .setMessage(R.string.system_settings_restore_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.system_settings_restore_confirm_button,
                        (dialog, which) -> restore(uri))
                .show();
    }

    private void restore(Uri uri) {
        final Context context = requireContext().getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            String message;
            try {
                final int count = SystemSettingsBackup.restore(context, uri);
                message = context.getResources().getQuantityString(
                        R.plurals.system_settings_restore_done, count, count);
            } catch (Exception e) {
                Log.e(TAG, "Restore failed", e);
                message = context.getString(R.string.system_settings_restore_failed);
            }
            final String result = message;
            ThreadUtils.postOnMainThread(
                    () -> Toast.makeText(context, result, Toast.LENGTH_LONG).show());
        });
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.BACKUP_SETTINGS;
    }
}
