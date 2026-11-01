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

package com.android.settings.biometrics.fingerprint;

import android.content.Context;
import android.content.Intent;
import android.hardware.fingerprint.Fingerprint;
import android.hardware.fingerprint.FingerprintManager;
import android.os.UserHandle;
import android.text.TextUtils;

import androidx.preference.Preference;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.core.BasePreferenceController;

/** "Panic finger" entry on the device unlock page. */
public class PanicFingerprintPreferenceController extends BasePreferenceController {

    public PanicFingerprintPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return Utils.hasFingerprintHardware(mContext) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public CharSequence getSummary() {
        return mContext.getText(isPanicFingerSet(mContext, UserHandle.myUserId())
                ? R.string.panic_fingerprint_summary_on
                : R.string.panic_fingerprint_summary_off);
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            return false;
        }
        mContext.startActivity(new Intent(mContext, PanicFingerprintActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return true;
    }

    /** Whether a panic fingerprint is set and still enrolled. */
    static boolean isPanicFingerSet(Context context, int userId) {
        return findPanicFinger(context, userId) != null;
    }

    static Fingerprint findPanicFinger(Context context, int userId) {
        final FingerprintManager fpm = Utils.getFingerprintManagerOrNull(context);
        if (fpm == null) {
            return null;
        }
        final int id = new LockPatternUtils(context).getPanicFingerprintId(userId);
        if (id == LockPatternUtils.PANIC_FINGERPRINT_NONE) {
            return null;
        }
        for (Fingerprint fp : fpm.getEnrolledFingerprints(userId)) {
            if (fp.getBiometricId() == id) {
                return fp;
            }
        }
        return null;
    }
}
