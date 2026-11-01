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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.hardware.fingerprint.Fingerprint;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintSensorPropertiesInternal;
import android.os.Bundle;
import android.os.UserHandle;
import android.widget.Toast;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.password.ChooseLockSettingsHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers / manages the panic fingerprint. If none is registered, goes straight into the
 * regular fingerprint enrollment and remembers the newly enrolled finger as the panic one.
 * Otherwise asks for the screen lock first and offers to replace or remove it.
 */
public class PanicFingerprintActivity extends Activity {

    private static final int REQUEST_CONFIRM = 1;
    private static final int REQUEST_ENROLL = 2;

    private static final String KEY_IDS_BEFORE = "ids_before";
    private static final String KEY_BUSY = "busy";

    private FingerprintManager mFingerprintManager;
    private LockPatternUtils mLockPatternUtils;
    private int mUserId;
    private ArrayList<Integer> mIdsBeforeEnroll = new ArrayList<>();
    private boolean mBusy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFingerprintManager = Utils.getFingerprintManagerOrNull(this);
        if (mFingerprintManager == null || !mFingerprintManager.isHardwareDetected()) {
            finish();
            return;
        }
        mLockPatternUtils = new LockPatternUtils(this);
        mUserId = UserHandle.myUserId();

        if (savedInstanceState != null) {
            mBusy = savedInstanceState.getBoolean(KEY_BUSY);
            final ArrayList<Integer> ids = savedInstanceState.getIntegerArrayList(KEY_IDS_BEFORE);
            if (ids != null) {
                mIdsBeforeEnroll = ids;
            }
            if (mBusy) {
                return;
            }
        }

        mBusy = true;
        if (PanicFingerprintPreferenceController.isPanicFingerSet(this, mUserId)) {
            final boolean launched = new ChooseLockSettingsHelper.Builder(this)
                    .setRequestCode(REQUEST_CONFIRM)
                    .setTitle(getString(R.string.panic_fingerprint_title))
                    .setUserId(mUserId)
                    .setForegroundOnly(true)
                    .show();
            if (!launched) {
                showManageDialog();
            }
        } else {
            // Drop a stale id (finger removed) before registering a new one.
            mLockPatternUtils.setPanicFingerprintId(LockPatternUtils.PANIC_FINGERPRINT_NONE,
                    mUserId);
            startEnroll();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_BUSY, mBusy);
        outState.putIntegerArrayList(KEY_IDS_BEFORE, mIdsBeforeEnroll);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CONFIRM) {
            if (resultCode == RESULT_OK) {
                showManageDialog();
            } else {
                finish();
            }
        } else if (requestCode == REQUEST_ENROLL) {
            onEnrollFinished();
        }
    }

    private void startEnroll() {
        final List<Fingerprint> enrolled = mFingerprintManager.getEnrolledFingerprints(mUserId);
        final int max = getMaxFingerprints();
        if (max > 0 && enrolled.size() >= max) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.panic_fingerprint_title)
                    .setMessage(getString(R.string.panic_fingerprint_limit, max))
                    .setPositiveButton(android.R.string.ok, null)
                    .setOnDismissListener(d -> finish())
                    .show();
            return;
        }
        mIdsBeforeEnroll = new ArrayList<>();
        for (Fingerprint fp : enrolled) {
            mIdsBeforeEnroll.add(fp.getBiometricId());
        }
        final Intent intent = new Intent(this, FingerprintEnrollIntroduction.class);
        intent.putExtra(Intent.EXTRA_USER_ID, mUserId);
        startActivityForResult(intent, REQUEST_ENROLL);
    }

    private void onEnrollFinished() {
        int newId = LockPatternUtils.PANIC_FINGERPRINT_NONE;
        for (Fingerprint fp : mFingerprintManager.getEnrolledFingerprints(mUserId)) {
            if (!mIdsBeforeEnroll.contains(fp.getBiometricId())) {
                newId = fp.getBiometricId();
                break;
            }
        }
        if (newId != LockPatternUtils.PANIC_FINGERPRINT_NONE) {
            mLockPatternUtils.setPanicFingerprintId(newId, mUserId);
            Toast.makeText(this, R.string.panic_fingerprint_saved, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.panic_fingerprint_not_saved, Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void showManageDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.panic_fingerprint_title)
                .setMessage(R.string.panic_fingerprint_message)
                .setPositiveButton(R.string.panic_fingerprint_done, (d, w) -> finish())
                .setNegativeButton(R.string.panic_fingerprint_remove,
                        (d, w) -> removePanicFinger(false /* thenEnroll */))
                .setNeutralButton(R.string.panic_fingerprint_replace,
                        (d, w) -> removePanicFinger(true /* thenEnroll */))
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void removePanicFinger(boolean thenEnroll) {
        final Fingerprint fp = PanicFingerprintPreferenceController.findPanicFinger(this, mUserId);
        mLockPatternUtils.setPanicFingerprintId(LockPatternUtils.PANIC_FINGERPRINT_NONE, mUserId);
        if (fp == null) {
            afterRemoval(thenEnroll);
            return;
        }
        mFingerprintManager.remove(fp, mUserId, new FingerprintManager.RemovalCallback() {
            @Override
            public void onRemovalSucceeded(Fingerprint removed, int remaining) {
                if (remaining == 0) {
                    afterRemoval(thenEnroll);
                }
            }

            @Override
            public void onRemovalError(Fingerprint removed, int errMsgId,
                    CharSequence errString) {
                Toast.makeText(PanicFingerprintActivity.this, errString,
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void afterRemoval(boolean thenEnroll) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (thenEnroll) {
            startEnroll();
        } else {
            Toast.makeText(this, R.string.panic_fingerprint_removed, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private int getMaxFingerprints() {
        final List<FingerprintSensorPropertiesInternal> props =
                mFingerprintManager.getSensorPropertiesInternal();
        return props.isEmpty() ? 0 : props.get(0).maxEnrollmentsPerUser;
    }
}
