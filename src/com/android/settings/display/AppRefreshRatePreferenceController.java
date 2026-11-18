/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.display;

import android.content.Context;
import android.view.Display;

import com.android.settings.core.BasePreferenceController;

import java.util.ArrayList;
import java.util.List;

/** Shows the per-app refresh rate screen when the display has more than one refresh rate. */
public class AppRefreshRatePreferenceController extends BasePreferenceController {

    public AppRefreshRatePreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return getSupportedRefreshRates(mContext).size() > 1 ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    /** Returns the distinct rounded refresh rates at the current resolution, ascending. */
    static List<Integer> getSupportedRefreshRates(Context context) {
        final List<Integer> rates = new ArrayList<>();
        final Display display = context.getDisplay();
        if (display == null) {
            return rates;
        }
        final Display.Mode current = display.getMode();
        for (Display.Mode m : display.getSupportedModes()) {
            if (m.getPhysicalWidth() != current.getPhysicalWidth()
                    || m.getPhysicalHeight() != current.getPhysicalHeight()) {
                continue;
            }
            final int rate = Math.round(m.getRefreshRate());
            if (!rates.contains(rate)) {
                rates.add(rate);
            }
        }
        rates.sort(null);
        return rates;
    }
}
