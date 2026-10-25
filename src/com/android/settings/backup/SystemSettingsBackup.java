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

import android.content.ContentResolver;
import android.content.Context;
import android.app.UiModeManager;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import lineageos.providers.LineageSettings;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Saves system settings, including the quick settings tiles, to a JSON file and restores them.
 *
 * Only settings that Android itself backs up and a few toggles of these settings are saved, so
 * device specific values like the Android ID, ADB or the screen lock are never touched. LineageOS
 * settings are saved when they have a validator, which every user facing LineageOS setting has.
 */
public final class SystemSettingsBackup {
    private static final String TAG = "SystemSettingsBackup";

    private static final int VERSION = 1;

    private static final String KEY_VERSION = "version";
    private static final String KEY_SDK = "sdk";
    private static final String KEY_CREATED = "created";
    private static final String KEY_SYSTEM = "system";
    private static final String KEY_SECURE = "secure";
    private static final String KEY_GLOBAL = "global";
    private static final String KEY_LINEAGE_SYSTEM = "lineage_system";
    private static final String KEY_LINEAGE_SECURE = "lineage_secure";
    private static final String KEY_LINEAGE_GLOBAL = "lineage_global";
    private static final String KEY_VOLUMES = "volumes";
    private static final String KEY_BRIGHTNESS = "brightness";

    // Same lists and order as frameworks/base/packages/SettingsProvider/src/android/provider/
    // settings/backup, without the wearable settings and the ringtones, whose media URIs don't
    // survive a reinstall.
    private static final String[] SYSTEM_KEYS = {
            Settings.System.STAY_ON_WHILE_PLUGGED_IN,
            Settings.System.WIFI_USE_STATIC_IP,
            Settings.System.WIFI_STATIC_IP,
            Settings.System.WIFI_STATIC_GATEWAY,
            Settings.System.WIFI_STATIC_NETMASK,
            Settings.System.WIFI_STATIC_DNS1,
            Settings.System.WIFI_STATIC_DNS2,
            Settings.System.BLUETOOTH_DISCOVERABILITY,
            Settings.System.BLUETOOTH_DISCOVERABILITY_TIMEOUT,
            Settings.System.FONT_SCALE,
            Settings.System.DIM_SCREEN,
            Settings.System.SCREEN_OFF_TIMEOUT,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.ADAPTIVE_SLEEP,
            Settings.System.APPLY_RAMPING_RINGER,
            Settings.System.VIBRATE_INPUT_DEVICES,
            Settings.System.MODE_RINGER_STREAMS_AFFECTED,
            Settings.System.TEXT_AUTO_REPLACE,
            Settings.System.TEXT_AUTO_CAPS,
            Settings.System.TEXT_AUTO_PUNCTUATE,
            Settings.System.TEXT_SHOW_PASSWORD,
            Settings.System.AUTO_TIME,
            Settings.System.AUTO_TIME_ZONE,
            Settings.System.TIME_12_24,
            Settings.System.DTMF_TONE_WHEN_DIALING,
            Settings.System.DTMF_TONE_TYPE_WHEN_DIALING,
            Settings.System.HEARING_AID_COMPATIBILITY,
            Settings.System.TTY_MODE,
            Settings.System.MASTER_MONO,
            Settings.System.MASTER_BALANCE,
            Settings.System.FOLD_LOCK_BEHAVIOR,
            Settings.System.SOUND_EFFECTS_ENABLED,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            Settings.System.POWER_SOUNDS_ENABLED,
            Settings.System.DOCK_SOUNDS_ENABLED,
            Settings.System.LOCKSCREEN_SOUNDS_ENABLED,
            Settings.System.SHOW_WEB_SUGGESTIONS,
            Settings.System.SIP_CALL_OPTIONS,
            Settings.System.SIP_RECEIVE_CALLS,
            Settings.System.TOUCHPAD_THREE_FINGER_TAP_CUSTOMIZATION,
            Settings.System.POINTER_SPEED,
            Settings.System.POINTER_FILL_STYLE,
            Settings.System.POINTER_STROKE_STYLE,
            Settings.System.POINTER_SCALE,
            Settings.System.VIBRATE_ON,
            Settings.System.VIBRATE_WHEN_RINGING,
            Settings.System.LOCK_TO_APP_ENABLED,
            Settings.System.ACCELEROMETER_ROTATION,
            Settings.System.SHOW_BATTERY_PERCENT,
            Settings.System.ALARM_VIBRATION_INTENSITY,
            Settings.System.MEDIA_VIBRATION_INTENSITY,
            Settings.System.NOTIFICATION_VIBRATION_INTENSITY,
            Settings.System.RING_VIBRATION_INTENSITY,
            Settings.System.HAPTIC_FEEDBACK_INTENSITY,
            Settings.System.HARDWARE_HAPTIC_FEEDBACK_INTENSITY,
            Settings.System.GESTURE_INPUT_VIBRATION_INTENSITY,
            Settings.System.KEYBOARD_VIBRATION_INTENSITY,
            Settings.System.KEYBOARD_VIBRATION_ENABLED,
            Settings.System.DISPLAY_COLOR_MODE_VENDOR_HINT,
            Settings.System.DISPLAY_COLOR_MODE,
            Settings.System.NOTIFICATION_LIGHT_PULSE,
            Settings.System.WEAR_ACCESSIBILITY_GESTURE_ENABLED,
            Settings.System.CLOCKWORK_BLUETOOTH_SETTINGS_PREF,
            Settings.System.ENABLE_NOTIFICATION_WITHOUT_TAP_OR_TILT,
            Settings.System.UNREAD_NOTIFICATION_DOT_INDICATOR,
            Settings.System.AUTO_LAUNCH_MEDIA_CONTROLS,
            Settings.System.LOCALE_PREFERENCES,
            Settings.System.MOUSE_REVERSE_VERTICAL_SCROLLING,
            Settings.System.MOUSE_SCROLLING_ACCELERATION,
            Settings.System.MOUSE_SCROLLING_SPEED,
            Settings.System.MOUSE_SWAP_PRIMARY_BUTTON,
            Settings.System.MOUSE_POINTER_ACCELERATION_ENABLED,
            Settings.System.TOUCHPAD_POINTER_SPEED,
            Settings.System.TOUCHPAD_NATURAL_SCROLLING,
            Settings.System.TOUCHPAD_TAP_TO_CLICK,
            Settings.System.TOUCHPAD_TAP_DRAGGING,
            Settings.System.TOUCHPAD_RIGHT_CLICK_ZONE,
            Settings.System.TOUCHPAD_ACCELERATION_ENABLED,
            Settings.System.CAMERA_FLASH_NOTIFICATION,
            Settings.System.SCREEN_FLASH_NOTIFICATION,
            Settings.System.SCREEN_FLASH_NOTIFICATION_COLOR,
            Settings.System.NOTIFICATION_COOLDOWN_ENABLED,
            Settings.System.NOTIFICATION_COOLDOWN_ALL,
            Settings.System.NOTIFICATION_COOLDOWN_VIBRATE_UNLOCKED,
            Settings.System.PREFERRED_REGION,
            Settings.System.CV_ENABLED,
            Settings.System.CV_DYNAMIC_ENABLED,
            Settings.System.CV_PREFERRED_INTENSITY,
            Settings.System.PEAK_REFRESH_RATE,
            Settings.System.MIN_REFRESH_RATE,
            Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_DISABLE,
    };

    private static final String[] SECURE_KEYS = {
            Settings.Secure.BUGREPORT_IN_POWER_MENU,
            Settings.Secure.ALLOW_MOCK_LOCATION,
            Settings.Secure.USB_MASS_STORAGE_ENABLED,
            Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED,
            Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER,
            Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED,
            Settings.Secure.ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED,
            Settings.Secure.ACCESSIBILITY_DISPLAY_MAGNIFICATION_NAVBAR_ENABLED,
            Settings.Secure.ADAPTIVE_CHARGING_ENABLED,
            Settings.Secure.ADAPTIVE_SLEEP,
            Settings.Secure.CAMERA_AUTOROTATE,
            Settings.Secure.AUTOFILL_SERVICE,
            Settings.Secure.ACCESSIBILITY_DISPLAY_MAGNIFICATION_SCALE,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            Settings.Secure.ENABLED_ACCESSIBILITY_AUDIO_DESCRIPTION_BY_DEFAULT,
            Settings.Secure.ENABLED_VR_LISTENERS,
            Settings.Secure.TOUCH_EXPLORATION_GRANTED_ACCESSIBILITY_SERVICES,
            Settings.Secure.TOUCH_EXPLORATION_ENABLED,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE,
            Settings.Secure.ACCESSIBILITY_BUTTON_TARGET_COMPONENT,
            Settings.Secure.ACCESSIBILITY_SHORTCUT_DIALOG_SHOWN,
            Settings.Secure.ACCESSIBILITY_SHORTCUT_ON_LOCK_SCREEN,
            Settings.Secure.ACCESSIBILITY_HCT_RECT_PROMPT_STATUS,
            Settings.Secure.ACCESSIBILITY_HIGH_TEXT_CONTRAST_ENABLED,
            Settings.Secure.CONTRAST_LEVEL,
            Settings.Secure.ACCESSIBILITY_CAPTIONING_PRESET,
            Settings.Secure.ACCESSIBILITY_CAPTIONING_ENABLED,
            Settings.Secure.ACCESSIBILITY_CAPTIONING_LOCALE,
            Settings.Secure.ACCESSIBILITY_CAPTIONING_EASY_READER_ENABLED,
            Settings.Secure.ACCESSIBILITY_CAPTIONING_BACKGROUND_COLOR,
            Settings.Secure.ACCESSIBILITY_CAPTIONING_FOREGROUND_COLOR,
            Settings.Secure.ACCESSIBILITY_CAPTIONING_EDGE_TYPE,
            Settings.Secure.ACCESSIBILITY_CAPTIONING_EDGE_COLOR,
            Settings.Secure.ACCESSIBILITY_CAPTIONING_TYPEFACE,
            Settings.Secure.ACCESSIBILITY_CAPTIONING_FONT_SCALE,
            Settings.Secure.ACCESSIBILITY_CAPTIONING_WINDOW_COLOR,
            Settings.Secure.FONT_WEIGHT_ADJUSTMENT,
            Settings.Secure.REDUCE_BRIGHT_COLORS_LEVEL,
            Settings.Secure.REDUCE_BRIGHT_COLORS_PERSIST_ACROSS_REBOOTS,
            Settings.Secure.TTS_DEFAULT_RATE,
            Settings.Secure.TTS_DEFAULT_PITCH,
            Settings.Secure.TTS_DEFAULT_SYNTH,
            Settings.Secure.TTS_ENABLED_PLUGINS,
            Settings.Secure.TTS_DEFAULT_LOCALE,
            Settings.Secure.SHOW_IME_WITH_HARD_KEYBOARD,
            Settings.Secure.ACCESSIBILITY_BOUNCE_KEYS,
            Settings.Secure.ACCESSIBILITY_SLOW_KEYS,
            Settings.Secure.ACCESSIBILITY_STICKY_KEYS,
            Settings.Secure.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
            Settings.Secure.WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY,
            Settings.Secure.WIFI_NUM_OPEN_NETWORKS_KEPT,
            Settings.Secure.MOUNT_PLAY_NOTIFICATION_SND,
            Settings.Secure.MOUNT_UMS_AUTOSTART,
            Settings.Secure.MOUNT_UMS_PROMPT,
            Settings.Secure.MOUNT_UMS_NOTIFY_ENABLED,
            Settings.Secure.DOUBLE_TAP_TO_WAKE,
            Settings.Secure.DOUBLE_TAP_TO_SLEEP,
            Settings.Secure.WAKE_GESTURE_ENABLED,
            Settings.Secure.LONG_PRESS_TIMEOUT,
            Settings.Secure.KEY_REPEAT_ENABLED,
            Settings.Secure.KEY_REPEAT_TIMEOUT_MS,
            Settings.Secure.KEY_REPEAT_DELAY_MS,
            Settings.Secure.ACCESSIBILITY_TEXT_CURSOR_BLINK_INTERVAL_MS,
            Settings.Secure.CAMERA_GESTURE_DISABLED,
            Settings.Secure.ACCESSIBILITY_AUTOCLICK_CURSOR_AREA_SIZE,
            Settings.Secure.ACCESSIBILITY_AUTOCLICK_IGNORE_MINOR_CURSOR_MOVEMENT,
            Settings.Secure.ACCESSIBILITY_AUTOCLICK_ENABLED,
            Settings.Secure.ACCESSIBILITY_AUTOCLICK_DELAY,
            Settings.Secure.ACCESSIBILITY_AUTOCLICK_PANEL_POSITION,
            Settings.Secure.ACCESSIBILITY_AUTOCLICK_REVERT_TO_LEFT_CLICK,
            Settings.Secure.ACCESSIBILITY_LARGE_POINTER_ICON,
            Settings.Secure.PREFERRED_TTY_MODE,
            Settings.Secure.ENHANCED_VOICE_PRIVACY_ENABLED,
            Settings.Secure.TTY_MODE_ENABLED,
            Settings.Secure.RTT_CALLING_MODE,
            Settings.Secure.INCALL_POWER_BUTTON_BEHAVIOR,
            Settings.Secure.MINIMAL_POST_PROCESSING_ALLOWED,
            Settings.Secure.MIRROR_BUILT_IN_DISPLAY,
            Settings.Secure.INCLUDE_DEFAULT_DISPLAY_IN_TOPOLOGY,
            Settings.Secure.MATCH_CONTENT_FRAME_RATE,
            Settings.Secure.NIGHT_DISPLAY_CUSTOM_START_TIME,
            Settings.Secure.NIGHT_DISPLAY_CUSTOM_END_TIME,
            Settings.Secure.NIGHT_DISPLAY_COLOR_TEMPERATURE,
            Settings.Secure.NIGHT_DISPLAY_AUTO_MODE,
            Settings.Secure.DISPLAY_WHITE_BALANCE_ENABLED,
            Settings.Secure.SYNC_PARENT_SOUNDS,
            Settings.Secure.CAMERA_DOUBLE_TWIST_TO_FLIP_ENABLED,
            Settings.Secure.DOUBLE_TAP_POWER_BUTTON_GESTURE_ENABLED,
            Settings.Secure.DOUBLE_TAP_POWER_BUTTON_GESTURE,
            Settings.Secure.CAMERA_DOUBLE_TAP_POWER_GESTURE_DISABLED,
            Settings.Secure.SYSTEM_NAVIGATION_KEYS_ENABLED,
            Settings.Secure.ACCESSIBILITY_QS_TARGETS,
            Settings.Secure.QS_TILES,
            Settings.Secure.QS_AUTO_ADDED_TILES,
            Settings.Secure.CONTROLS_ENABLED,
            Settings.Secure.POWER_MENU_LOCKED_SHOW_CONTENT,
            Settings.Secure.DOZE_ENABLED,
            Settings.Secure.DOZE_ALWAYS_ON,
            Settings.Secure.DOZE_ALWAYS_ON_INACTIVITY_DETECTION,
            Settings.Secure.DOZE_ALWAYS_ON_WALLPAPER_ENABLED,
            Settings.Secure.DOZE_PICK_UP_GESTURE,
            Settings.Secure.DOZE_DOUBLE_TAP_GESTURE,
            Settings.Secure.DOZE_TAP_SCREEN_GESTURE,
            Settings.Secure.NFC_PAYMENT_DEFAULT_COMPONENT,
            Settings.Secure.AUTOMATIC_STORAGE_MANAGER_DAYS_TO_RETAIN,
            Settings.Secure.FACE_UNLOCK_KEYGUARD_ENABLED,
            Settings.Secure.SHOW_MEDIA_WHEN_BYPASSING,
            Settings.Secure.FACE_UNLOCK_DISMISSES_KEYGUARD,
            Settings.Secure.FACE_UNLOCK_APP_ENABLED,
            Settings.Secure.FACE_UNLOCK_ALWAYS_REQUIRE_CONFIRMATION,
            Settings.Secure.FINGERPRINT_SIDE_FPS_KG_POWER_WINDOW,
            Settings.Secure.FINGERPRINT_SIDE_FPS_BP_POWER_WINDOW,
            Settings.Secure.FINGERPRINT_SIDE_FPS_ENROLL_TAP_WINDOW,
            Settings.Secure.FINGERPRINT_SIDE_FPS_AUTH_DOWNTIME,
            Settings.Secure.SFPS_PERFORMANT_AUTH_ENABLED,
            Settings.Secure.SCREEN_OFF_UNLOCK_UDFPS_ENABLED,
            Settings.Secure.ACTIVE_UNLOCK_ON_WAKE,
            Settings.Secure.ACTIVE_UNLOCK_ON_UNLOCK_INTENT,
            Settings.Secure.ACTIVE_UNLOCK_ON_UNLOCK_INTENT_LEGACY,
            Settings.Secure.ACTIVE_UNLOCK_ON_BIOMETRIC_FAIL,
            Settings.Secure.ACTIVE_UNLOCK_ON_FACE_ERRORS,
            Settings.Secure.ACTIVE_UNLOCK_ON_FACE_ACQUIRE_INFO,
            Settings.Secure.ACTIVE_UNLOCK_ON_UNLOCK_INTENT_WHEN_BIOMETRIC_ENROLLED,
            Settings.Secure.ACTIVE_UNLOCK_WAKEUPS_CONSIDERED_UNLOCK_INTENTS,
            Settings.Secure.ACTIVE_UNLOCK_WAKEUPS_TO_FORCE_DISMISS_KEYGUARD,
            Settings.Secure.VR_DISPLAY_MODE,
            Settings.Secure.NOTIFICATION_BADGING,
            Settings.Secure.NOTIFICATION_DISMISS_RTL,
            Settings.Secure.SCREENSAVER_ENABLED,
            Settings.Secure.SCREENSAVER_COMPONENTS,
            Settings.Secure.SCREENSAVER_ACTIVE_COMPONENT,
            Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK,
            Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP,
            Settings.Secure.SCREENSAVER_ACTIVATE_ON_POSTURED,
            Settings.Secure.SCREENSAVER_HOME_CONTROLS_ENABLED,
            Settings.Secure.SCREENSAVER_RESTRICT_TO_WIRELESS_CHARGING,
            Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR,
            Settings.Secure.LOW_LIGHT_DISPLAY_BEHAVIOR_ENABLED,
            Settings.Secure.SHOW_FIRST_CRASH_DIALOG_DEV_OPTION,
            Settings.Secure.VOLUME_DIALOG_DISMISS_TIMEOUT,
            Settings.Secure.VOLUME_HUSH_GESTURE,
            Settings.Secure.MANUAL_RINGER_TOGGLE_COUNT,
            Settings.Secure.LOW_POWER_WARNING_ACKNOWLEDGED,
            Settings.Secure.EXTRA_LOW_POWER_WARNING_ACKNOWLEDGED,
            Settings.Secure.EMERGENCY_THERMAL_ALERT_DISABLED,
            Settings.Secure.HUSH_GESTURE_USED,
            Settings.Secure.IN_CALL_NOTIFICATION_ENABLED,
            Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS,
            Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_FACE,
            Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS,
            Settings.Secure.LOCK_SCREEN_SHOW_SILENT_NOTIFICATIONS,
            Settings.Secure.LOCK_SCREEN_SHOW_ONLY_UNSEEN_NOTIFICATIONS,
            Settings.Secure.LOCK_SCREEN_NOTIFICATION_MINIMALISM,
            Settings.Secure.SHOW_NOTIFICATION_SNOOZE,
            Settings.Secure.NOTIFICATION_HISTORY_ENABLED,
            Settings.Secure.ZEN_DURATION,
            Settings.Secure.CHARGING_SOUNDS_ENABLED,
            Settings.Secure.CHARGING_VIBRATION_ENABLED,
            Settings.Secure.ACCESSIBILITY_NON_INTERACTIVE_UI_TIMEOUT_MS,
            Settings.Secure.ACCESSIBILITY_INTERACTIVE_UI_TIMEOUT_MS,
            Settings.Secure.UI_NIGHT_MODE,
            Settings.Secure.UI_NIGHT_MODE_CUSTOM_TYPE,
            Settings.Secure.DARK_THEME_CUSTOM_START_TIME,
            Settings.Secure.DARK_THEME_CUSTOM_END_TIME,
            Settings.Secure.SKIP_DIRECTION,
            Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
            Settings.Secure.BACK_GESTURE_INSET_SCALE_LEFT,
            Settings.Secure.BACK_GESTURE_INSET_SCALE_RIGHT,
            Settings.Secure.NAVIGATION_MODE,
            Settings.Secure.SKIP_GESTURE_COUNT,
            Settings.Secure.SKIP_TOUCH_COUNT,
            Settings.Secure.SILENCE_ALARMS_GESTURE_COUNT,
            Settings.Secure.SILENCE_CALL_GESTURE_COUNT,
            Settings.Secure.SILENCE_TIMER_GESTURE_COUNT,
            Settings.Secure.SILENCE_ALARMS_TOUCH_COUNT,
            Settings.Secure.SILENCE_CALL_TOUCH_COUNT,
            Settings.Secure.SILENCE_TIMER_TOUCH_COUNT,
            Settings.Secure.DARK_MODE_DIALOG_SEEN,
            Settings.Secure.GLOBAL_ACTIONS_PANEL_ENABLED,
            Settings.Secure.AWARE_LOCK_ENABLED,
            Settings.Secure.AWARE_TAP_PAUSE_GESTURE_COUNT,
            Settings.Secure.AWARE_TAP_PAUSE_TOUCH_COUNT,
            Settings.Secure.PEOPLE_STRIP,
            Settings.Secure.MEDIA_CONTROLS_RESUME,
            Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN,
            Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE,
            Settings.Secure.ACCESSIBILITY_BUTTON_TARGETS,
            Settings.Secure.ACCESSIBILITY_MAGNIFICATION_CAPABILITY,
            Settings.Secure.ACCESSIBILITY_MAGNIFICATION_FOLLOW_TYPING_ENABLED,
            Settings.Secure.ACCESSIBILITY_MAGNIFICATION_FOLLOW_KEYBOARD_ENABLED,
            Settings.Secure.ONE_HANDED_MODE_ACTIVATED,
            Settings.Secure.ONE_HANDED_MODE_ENABLED,
            Settings.Secure.ONE_HANDED_MODE_TIMEOUT,
            Settings.Secure.TAPS_APP_TO_EXIT,
            Settings.Secure.SWIPE_BOTTOM_TO_NOTIFICATION_ENABLED,
            Settings.Secure.EMERGENCY_GESTURE_ENABLED,
            Settings.Secure.EMERGENCY_GESTURE_SOUND_ENABLED,
            Settings.Secure.ADAPTIVE_CONNECTIVITY_ENABLED,
            Settings.Secure.ADAPTIVE_CONNECTIVITY_WIFI_ENABLED,
            Settings.Secure.ADAPTIVE_CONNECTIVITY_MOBILE_NETWORK_ENABLED,
            Settings.Secure.ASSIST_HANDLES_LEARNING_TIME_ELAPSED_MILLIS,
            Settings.Secure.ASSIST_HANDLES_LEARNING_EVENT_COUNT,
            Settings.Secure.ACCESSIBILITY_BUTTON_MODE,
            Settings.Secure.ACCESSIBILITY_FLOATING_MENU_SIZE,
            Settings.Secure.ACCESSIBILITY_FLOATING_MENU_ICON_TYPE,
            Settings.Secure.ACCESSIBILITY_FLOATING_MENU_OPACITY,
            Settings.Secure.ACCESSIBILITY_FLOATING_MENU_FADE_ENABLED,
            Settings.Secure.ACCESSIBILITY_FORCE_INVERT_COLOR_ENABLED,
            Settings.Secure.ACCESSIBILITY_MAGNIFICATION_ALWAYS_ON_ENABLED,
            Settings.Secure.ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE,
            Settings.Secure.ACCESSIBILITY_MAGNIFICATION_JOYSTICK_ENABLED,
            Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MAGNIFY_NAV_AND_IME,
            Settings.Secure.ACCESSIBILITY_MOUSE_KEYS_ENABLED,
            Settings.Secure.ACCESSIBILITY_MOUSE_KEYS_ACCELERATION,
            Settings.Secure.ACCESSIBILITY_MOUSE_KEYS_MAX_SPEED,
            Settings.Secure.ACCESSIBILITY_MOUSE_KEYS_USE_PRIMARY_KEYS,
            Settings.Secure.ACCESSIBILITY_PINCH_TO_ZOOM_ANYWHERE_ENABLED,
            Settings.Secure.ACCESSIBILITY_SINGLE_FINGER_PANNING_ENABLED,
            Settings.Secure.ACTION_CORNER_TOP_LEFT_ACTION,
            Settings.Secure.ACTION_CORNER_TOP_RIGHT_ACTION,
            Settings.Secure.ACTION_CORNER_BOTTOM_LEFT_ACTION,
            Settings.Secure.ACTION_CORNER_BOTTOM_RIGHT_ACTION,
            Settings.Secure.ODI_CAPTIONS_VOLUME_UI_ENABLED,
            Settings.Secure.NOTIFICATION_BUBBLES,
            Settings.Secure.LOCATION_TIME_ZONE_DETECTION_ENABLED,
            Settings.Secure.LOCKSCREEN_SHOW_CONTROLS,
            Settings.Secure.LOCKSCREEN_ALLOW_TRIVIAL_CONTROLS,
            Settings.Secure.LOCKSCREEN_SHOW_WALLET,
            Settings.Secure.LOCK_SCREEN_SHOW_QR_CODE_SCANNER,
            Settings.Secure.LOCKSCREEN_USE_DOUBLE_LINE_CLOCK,
            Settings.Secure.STATUS_BAR_SHOW_VIBRATE_ICON,
            Settings.Secure.WEAR_TALKBACK_ENABLED,
            Settings.Secure.HBM_SETTING_KEY,
            Settings.Secure.ASSIST_TOUCH_GESTURE_ENABLED,
            Settings.Secure.ASSIST_LONG_PRESS_HOME_ENABLED,
            Settings.Secure.BLUETOOTH_LE_BROADCAST_PROGRAM_INFO,
            Settings.Secure.BLUETOOTH_LE_BROADCAST_NAME,
            Settings.Secure.BLUETOOTH_LE_BROADCAST_CODE,
            Settings.Secure.BLUETOOTH_LE_BROADCAST_APP_SOURCE_NAME,
            Settings.Secure.BLUETOOTH_LE_BROADCAST_IMPROVE_COMPATIBILITY,
            Settings.Secure.BLUETOOTH_LE_BROADCAST_FALLBACK_ACTIVE_DEVICE_ADDRESS,
            Settings.Secure.CUSTOM_BUGREPORT_HANDLER_APP,
            Settings.Secure.CUSTOM_BUGREPORT_HANDLER_USER,
            Settings.Secure.CONTEXTUAL_SCREEN_TIMEOUT_ENABLED,
            Settings.Secure.HINGE_ANGLE_LIDEVENT_ENABLED,
            Settings.Secure.LOCK_SCREEN_WEATHER_ENABLED,
            Settings.Secure.HEARING_AID_RINGTONE_ROUTING,
            Settings.Secure.HEARING_AID_CALL_ROUTING,
            Settings.Secure.HEARING_AID_MEDIA_ROUTING,
            Settings.Secure.HEARING_AID_NOTIFICATION_ROUTING,
            Settings.Secure.ACCESSIBILITY_FONT_SCALING_HAS_BEEN_CHANGED,
            Settings.Secure.SEARCH_ALL_ENTRYPOINTS_ENABLED,
            Settings.Secure.HUB_MODE_TUTORIAL_STATE,
            Settings.Secure.GLANCEABLE_HUB_ENABLED,
            Settings.Secure.WHEN_TO_START_GLANCEABLE_HUB,
            Settings.Secure.GLANCEABLE_HUB_RESTRICT_TO_WIRELESS_CHARGING,
            Settings.Secure.STYLUS_BUTTONS_ENABLED,
            Settings.Secure.STYLUS_HANDWRITING_ENABLED,
            Settings.Secure.DEFAULT_NOTE_TASK_PROFILE,
            Settings.Secure.CREDENTIAL_SERVICE,
            Settings.Secure.CREDENTIAL_SERVICE_PRIMARY,
            Settings.Secure.STYLUS_POINTER_ICON_ENABLED,
            Settings.Secure.CAMERA_EXTENSIONS_FALLBACK,
            Settings.Secure.VISUAL_QUERY_ACCESSIBILITY_DETECTION_ENABLED,
            Settings.Secure.IMMERSIVE_MODE_CONFIRMATIONS,
            Settings.Secure.AUDIO_DEVICE_INVENTORY,
            Settings.Secure.SCREEN_RESOLUTION_MODE,
            Settings.Secure.ACCESSIBILITY_GESTURE_TARGETS,
            Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_SATURATION_LEVEL,
            Settings.Secure.CHARGE_OPTIMIZATION_MODE,
            Settings.Secure.ON_DEVICE_INTELLIGENCE_UNBIND_TIMEOUT_MS,
            Settings.Secure.ON_DEVICE_INFERENCE_UNBIND_TIMEOUT_MS,
            Settings.Secure.ON_DEVICE_INTELLIGENCE_IDLE_TIMEOUT_MS,
            Settings.Secure.FIRST_ON_DEVICE_MODELS_DOWNLOADED_TIME,
            Settings.Secure.TETHERING_ALLOW_VPN_UPSTREAMS,
            Settings.Secure.CONTENT_SAFETY_IDLE_TIMEOUT_MS,
            Settings.Secure.CONTENT_SAFETY_UNBIND_TIMEOUT_MS,
            Settings.Secure.CONTENT_SAFETY_SANDBOXED_UNBIND_TIMEOUT_MS,
            Settings.Secure.CONTENT_SAFETY_SANDBOXED_IDLE_TIMEOUT_MS,
            Settings.Secure.CONTENT_SAFETY_SETTINGS_IDLE_TIMEOUT_MS,
            Settings.Secure.CONTENT_SAFETY_SETTINGS_UNBIND_TIMEOUT_MS,
            Settings.Secure.MANDATORY_BIOMETRICS,
            Settings.Secure.MANDATORY_BIOMETRICS_REQUIREMENTS_SATISFIED,
            Settings.Secure.ADVANCED_PROTECTION_MODE,
            Settings.Secure.ACCESSIBILITY_KEY_GESTURE_TARGETS,
            Settings.Secure.EM_VALUE,
            Settings.Secure.FACE_APP_ENABLED,
            Settings.Secure.FACE_KEYGUARD_ENABLED,
            Settings.Secure.FINGERPRINT_APP_ENABLED,
            Settings.Secure.FINGERPRINT_KEYGUARD_ENABLED,
            Settings.Secure.DUAL_SHADE,
            Settings.Secure.BROWSER_CONTENT_FILTERS_ENABLED,
            Settings.Secure.SEARCH_CONTENT_FILTERS_ENABLED,
            Settings.Secure.SPELL_CHECKER_ENABLED,
            Settings.Secure.SELECTED_SPELL_CHECKER,
            Settings.Secure.SELECTED_SPELL_CHECKER_SUBTYPE,
            Settings.Secure.HDR_BRIGHTNESS_ENABLED,
            Settings.Secure.HDR_BRIGHTNESS_BOOST_LEVEL,
            Settings.Secure.IDENTITY_CHECK_PROMO_CARD_SHOWN,
            Settings.Secure.IDENTITY_CHECK_NOTIFICATION_VIEW_DETAILS_CLICKED,
            Settings.Secure.NOTIFICATION_BUNDLES_ALWAYS_EXPAND,
            Settings.Secure.ACCESSIBILITY_TOP_ROW_KEY_TARGETS,
            Settings.Secure.ACCESSIBILITY_QUICK_ACCESS_TARGETS,
            Settings.Secure.NAVIGATIONBAR_KEY_ORDER,
            Settings.Secure.IDENTITY_CHECK_WATCH_PROMO_CARD_SHOWN,
            Settings.Secure.IDENTITY_CHECK_WATCH_NOTIFICATION_VIEW_DETAILS_CLICKED,
            Settings.Secure.TEXT_SHOW_PASSWORD_TOUCH,
            Settings.Secure.TEXT_SHOW_PASSWORD_PHYSICAL,
            Settings.Secure.PERSONAL_CONTEXT_ENABLED,
            Settings.Secure.PERSONAL_CONTEXT_MODE_ENABLED_DEFAULT,
            Settings.Secure.LIGHT_ANIMATION_FAVORITE_CALLS_ENABLED,
            Settings.Secure.IME_SWITCHER_BUTTON_IN_NAVBAR_ENABLED,
            Settings.Secure.IME_NAV_BAR_SPACE_HIDDEN,
            Settings.Secure.APP_LOCK_GRACE_PERIOD,
            Settings.Secure.LOCKSCREEN_POWER_MENU_DISABLED,
            Settings.Secure.LOCKSCREEN_QS_DISABLED,
            Settings.Secure.LOCKSCREEN_CHARGING_DETAILS,
            Settings.Secure.RAISE_TRIGGER_DEFAULT_ASSISTANT,
    };

    private static final String[] GLOBAL_KEYS = {
            Settings.Global.CONNECTED_APPS_ALLOWED_PACKAGES,
            Settings.Global.CONNECTED_APPS_DISALLOWED_PACKAGES,
            Settings.Global.APPLY_RAMPING_RINGER,
            Settings.Global.BUGREPORT_IN_POWER_MENU,
            Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
            Settings.Global.APP_AUTO_RESTRICTION_ENABLED,
            Settings.Global.AUTO_TIME,
            Settings.Global.AUTO_TIME_ZONE,
            Settings.Global.TIME_ZONE_NOTIFICATIONS,
            Settings.Global.TIME_ZONE_OFFSET_CHANGE_NOTIFICATIONS,
            Settings.Global.HOME_TIME_ZONE_ID,
            Settings.Global.POWER_SOUNDS_ENABLED,
            Settings.Global.DOCK_SOUNDS_ENABLED,
            Settings.Global.CHARGING_SOUNDS_ENABLED,
            Settings.Global.USB_MASS_STORAGE_ENABLED,
            Settings.Global.NETWORK_RECOMMENDATIONS_ENABLED,
            Settings.Global.NETWORK_AVOID_BAD_WIFI,
            Settings.Global.WIFI_WAKEUP_ENABLED,
            Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
            Settings.Global.USE_OPEN_WIFI_PACKAGE,
            Settings.Global.WIFI_WATCHDOG_POOR_NETWORK_TEST_ENABLED,
            Settings.Global.EMERGENCY_TONE,
            Settings.Global.CALL_AUTO_RETRY,
            Settings.Global.DOCK_AUDIO_MEDIA_ENABLED,
            Settings.Global.ENABLE_AUTOMATIC_SYSTEM_SERVER_HEAP_DUMPS,
            Settings.Global.ENCODED_SURROUND_OUTPUT,
            Settings.Global.ENCODED_SURROUND_OUTPUT_ENABLED_FORMATS,
            Settings.Global.LOW_POWER_MODE_REMINDER_ENABLED,
            Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL,
            Settings.Global.LOW_POWER_MODE_STICKY_AUTO_DISABLE_ENABLED,
            Settings.Global.LOW_POWER_MODE_STICKY_AUTO_DISABLE_LEVEL,
            Settings.Global.BLUETOOTH_ON,
            Settings.Global.PRIVATE_DNS_MODE,
            Settings.Global.PRIVATE_DNS_SPECIFIER,
            Settings.Global.SOFT_AP_TIMEOUT_ENABLED,
            Settings.Global.ZEN_DURATION,
            Settings.Global.MUTE_ALARM_STREAM_WITH_RINGER_MODE_USER_PREFERENCE,
            Settings.Global.REVERSE_CHARGING_AUTO_ON,
            Settings.Global.CHARGING_VIBRATION_ENABLED,
            Settings.Global.AWARE_ALLOWED,
            Settings.Global.CUSTOM_BUGREPORT_HANDLER_APP,
            Settings.Global.CUSTOM_BUGREPORT_HANDLER_USER,
            Settings.Global.USER_DISABLED_HDR_FORMATS,
            Settings.Global.ARE_USER_DISABLED_HDR_FORMATS_ALLOWED,
            Settings.Global.DEVICE_CONFIG_SYNC_DISABLED,
            Settings.Global.POWER_BUTTON_LONG_PRESS,
            Settings.Global.AUTOMATIC_POWER_SAVE_MODE,
            Settings.Global.ADVANCED_BATTERY_USAGE_AMOUNT,
            Settings.Global.ADAPTIVE_BATTERY_MANAGEMENT_ENABLED,
            Settings.Global.POWER_BUTTON_LONG_PRESS_DURATION_MS,
            Settings.Global.USER_PREFERRED_REFRESH_RATE,
            Settings.Global.USER_PREFERRED_RESOLUTION_HEIGHT,
            Settings.Global.USER_PREFERRED_RESOLUTION_WIDTH,
            Settings.Global.RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO_ENABLED,
            Settings.Global.HDR_CONVERSION_MODE,
            Settings.Global.HDR_FORCE_CONVERSION_TYPE,
            Settings.Global.FORCE_ENABLE_PSS_PROFILING,
            Settings.Global.ADD_USERS_WHEN_LOCKED,
            Settings.Global.REMOVE_GUEST_ON_EXIT,
            Settings.Global.USER_SWITCHER_ENABLED,
            Settings.Global.WIFI_OFF_TIMEOUT,
            Settings.Global.BLUETOOTH_OFF_TIMEOUT,
            Settings.Global.GLOBAL_ACTIONS_TIMEOUT_MILLIS,
            Settings.Global.WATCH_RANGING_SUPPORTED_BY_PRIMARY_DEVICE,
            Settings.Global.MINMODE_ACTIVE,
            Settings.Global.WATCH_RANGING_AVAILABLE,
    };

    // User facing settings that Android doesn't back up
    private static final String[] EXTRA_SYSTEM_KEYS = {
            Settings.System.THREE_FINGER_GESTURE,
    };

    private static final String[] EXTRA_SECURE_KEYS = {
            Settings.Secure.NIGHT_DISPLAY_ACTIVATED,
            Settings.Secure.ASSIST_STRUCTURE_ENABLED,
            Settings.Secure.ASSIST_SCREENSHOT_ENABLED,
            Settings.Secure.SILENCE_GESTURE,
            Settings.Secure.SKIP_GESTURE,
            // SystemUI status bar clock seconds and hidden status bar icons
            "clock_seconds",
            "icon_blacklist",
            Settings.Secure.WINDOW_IGNORE_SECURE,
            "tethering_allow_vpn_upstreams",
            "lockscreen_charging_details",
            "lockscreen_power_menu_disabled",
            "lockscreen_qs_disabled",
    };

    private static final String[] EXTRA_GLOBAL_KEYS = {
            Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED,
            Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE,
    };

    // LineageOS settings without a validator
    private static final String[] EXTRA_LINEAGE_SECURE_KEYS = {
            LineageSettings.Secure.QS_SHOW_BRIGHTNESS_SLIDER,
            LineageSettings.Secure.LOCKSCREEN_VISUALIZER_ENABLED,
    };

    private static final String[] EXTRA_LINEAGE_GLOBAL_KEYS = {
            LineageSettings.Global.WAKE_WHEN_PLUGGED_OR_UNPLUGGED,
    };

    // Volumes and brightness live in AudioService and DisplayManager, which don't pick up
    // changes of their settings, so they are saved and applied through them
    private static final int[] VOLUME_STREAMS = {
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_VOICE_CALL,
    };

    // Settings that services only pick up from a restore broadcast, as sent by SettingsHelper
    private static final Set<String> BROADCAST_TO_SYSTEM = Set.of(
            Settings.Secure.ENABLED_NOTIFICATION_LISTENERS,
            Settings.Secure.ENABLED_VR_LISTENERS,
            Settings.Global.BLUETOOTH_ON,
            Settings.Secure.DARK_THEME_CUSTOM_START_TIME,
            Settings.Secure.DARK_THEME_CUSTOM_END_TIME,
            Settings.Secure.SCREEN_RESOLUTION_MODE,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            Settings.Secure.ACCESSIBILITY_DISPLAY_MAGNIFICATION_NAVBAR_ENABLED,
            Settings.Secure.ACCESSIBILITY_BUTTON_TARGETS,
            Settings.Secure.ACCESSIBILITY_QS_TARGETS,
            Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE);

    private interface Getter {
        String get(ContentResolver cr, String key);
    }

    private interface Putter {
        boolean put(ContentResolver cr, String key, String value);
    }

    private SystemSettingsBackup() {}

    /** Writes the current settings to {@code uri}. Returns the number of saved settings. */
    public static int backup(Context context, Uri uri) throws IOException, JSONException {
        final ContentResolver cr = context.getContentResolver();
        final JSONObject system = save(cr, Settings.System::getString, SYSTEM_KEYS,
                EXTRA_SYSTEM_KEYS);
        final JSONObject secure = save(cr, Settings.Secure::getString, SECURE_KEYS,
                EXTRA_SECURE_KEYS);
        final JSONObject global = save(cr, Settings.Global::getString, GLOBAL_KEYS,
                EXTRA_GLOBAL_KEYS);
        final JSONObject lineageSystem = save(cr, LineageSettings.System::getString,
                LineageSettings.System.VALIDATORS.keySet().toArray(new String[0]));
        final JSONObject lineageSecure = save(cr, LineageSettings.Secure::getString,
                LineageSettings.Secure.VALIDATORS.keySet().toArray(new String[0]),
                EXTRA_LINEAGE_SECURE_KEYS);
        final JSONObject lineageGlobal = save(cr, LineageSettings.Global::getString,
                LineageSettings.Global.VALIDATORS.keySet().toArray(new String[0]),
                EXTRA_LINEAGE_GLOBAL_KEYS);

        final AudioManager audioManager = context.getSystemService(AudioManager.class);
        final JSONObject volumes = new JSONObject();
        for (int stream : VOLUME_STREAMS) {
            volumes.put(String.valueOf(stream), audioManager.getStreamVolume(stream));
        }
        final float brightness = context.getSystemService(DisplayManager.class)
                .getBrightness(Display.DEFAULT_DISPLAY);

        final JSONObject root = new JSONObject()
                .put(KEY_VERSION, VERSION)
                .put(KEY_SDK, Build.VERSION.SDK_INT)
                .put(KEY_CREATED, System.currentTimeMillis())
                .put(KEY_SYSTEM, system)
                .put(KEY_SECURE, secure)
                .put(KEY_GLOBAL, global)
                .put(KEY_LINEAGE_SYSTEM, lineageSystem)
                .put(KEY_LINEAGE_SECURE, lineageSecure)
                .put(KEY_LINEAGE_GLOBAL, lineageGlobal)
                .put(KEY_VOLUMES, volumes)
                .put(KEY_BRIGHTNESS, brightness);

        try (OutputStream out = cr.openOutputStream(uri, "wt")) {
            if (out == null) {
                throw new IOException("Can't open " + uri);
            }
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return system.length() + secure.length() + global.length() + lineageSystem.length()
                + lineageSecure.length() + lineageGlobal.length() + volumes.length() + 1;
    }

    /**
     * Restores the settings saved in {@code uri}. Settings that aren't in the current lists are
     * skipped, so a file can't change anything that isn't backed up. Returns the number of
     * changed settings.
     */
    public static int restore(Context context, Uri uri) throws IOException, JSONException {
        final ContentResolver cr = context.getContentResolver();
        final JSONObject root;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Can't open " + uri);
            }
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            in.transferTo(bytes);
            root = new JSONObject(bytes.toString(StandardCharsets.UTF_8));
        }
        if (!root.has(KEY_VERSION)) {
            throw new JSONException("Not a settings backup");
        }

        int changed = 0;
        changed += restore(context, root.optJSONObject(KEY_SYSTEM), Settings.System::getString,
                Settings.System::putString, null, SYSTEM_KEYS, EXTRA_SYSTEM_KEYS);
        changed += restore(context, root.optJSONObject(KEY_SECURE), Settings.Secure::getString,
                Settings.Secure::putString, null, SECURE_KEYS, EXTRA_SECURE_KEYS);
        changed += restore(context, root.optJSONObject(KEY_GLOBAL), Settings.Global::getString,
                Settings.Global::putString, null, GLOBAL_KEYS, EXTRA_GLOBAL_KEYS);
        changed += restore(context, root.optJSONObject(KEY_LINEAGE_SYSTEM),
                LineageSettings.System::getString, LineageSettings.System::putString,
                LineageSettings.System.VALIDATORS,
                LineageSettings.System.VALIDATORS.keySet().toArray(new String[0]));
        changed += restore(context, root.optJSONObject(KEY_LINEAGE_SECURE),
                LineageSettings.Secure::getString, LineageSettings.Secure::putString,
                LineageSettings.Secure.VALIDATORS,
                LineageSettings.Secure.VALIDATORS.keySet().toArray(new String[0]),
                EXTRA_LINEAGE_SECURE_KEYS);
        changed += restore(context, root.optJSONObject(KEY_LINEAGE_GLOBAL),
                LineageSettings.Global::getString, LineageSettings.Global::putString,
                LineageSettings.Global.VALIDATORS,
                LineageSettings.Global.VALIDATORS.keySet().toArray(new String[0]),
                EXTRA_LINEAGE_GLOBAL_KEYS);
        final JSONObject secure = root.optJSONObject(KEY_SECURE);
        if (secure != null && restoreQsTiles(context, secure)) {
            changed++;
        }
        if (secure != null && restoreNightMode(context, secure)) {
            changed++;
        }
        changed += restoreVolumes(context, root.optJSONObject(KEY_VOLUMES));
        if (restoreBrightness(context, root)) {
            changed++;
        }
        return changed;
    }

    private static JSONObject save(ContentResolver cr, Getter getter, String[]... keyLists)
            throws JSONException {
        final JSONObject table = new JSONObject();
        for (String[] keys : keyLists) {
            for (String key : keys) {
                // Unset settings are saved too, so restoring brings them back to the default
                final String value = getter.get(cr, key);
                table.put(key, value != null ? value : JSONObject.NULL);
            }
        }
        return table;
    }

    private static int restore(Context context, JSONObject table, Getter getter, Putter putter,
            Map<String, LineageSettings.Validator> validators, String[]... keyLists) {
        if (table == null) {
            return 0;
        }
        final ContentResolver cr = context.getContentResolver();
        int changed = 0;
        for (String[] keys : keyLists) {
            for (String key : keys) {
                // Restored through their services, see restoreQsTiles() and restoreNightMode()
                if (!table.has(key) || Settings.Secure.QS_TILES.equals(key)
                        || Settings.Secure.QS_AUTO_ADDED_TILES.equals(key)
                        || Settings.Secure.UI_NIGHT_MODE.equals(key)) {
                    continue;
                }
                final String value = table.isNull(key) ? null : table.optString(key);
                final String oldValue = getter.get(cr, key);
                if (Objects.equals(value, oldValue)) {
                    continue;
                }
                final LineageSettings.Validator validator =
                        validators != null ? validators.get(key) : null;
                if (value != null && validator != null && !validator.validate(value)) {
                    continue;
                }
                try {
                    if (!putter.put(cr, key, value)) {
                        continue;
                    }
                } catch (RuntimeException e) {
                    // A setting the platform rejects shouldn't stop the rest from being restored
                    Log.w(TAG, "Can't restore " + key, e);
                    continue;
                }
                if (BROADCAST_TO_SYSTEM.contains(key)) {
                    sendSettingRestored(context, "android", key, oldValue, value);
                }
                changed++;
            }
        }
        return changed;
    }

    /**
     * SystemUI keeps the quick settings tiles in memory and writes them back over any outside
     * change, so they are handed to it the same way a restore from the backup transport does:
     * one broadcast for the tiles and one for the auto added tiles.
     */
    private static boolean restoreQsTiles(Context context, JSONObject table) {
        final ContentResolver cr = context.getContentResolver();
        final String tiles = table.isNull(Settings.Secure.QS_TILES)
                ? null : table.optString(Settings.Secure.QS_TILES);
        final String currentTiles = Settings.Secure.getString(cr, Settings.Secure.QS_TILES);
        if (tiles == null || tiles.equals(currentTiles)) {
            return false;
        }
        final String currentAutoAdded =
                Settings.Secure.getString(cr, Settings.Secure.QS_AUTO_ADDED_TILES);
        final String autoAdded = table.isNull(Settings.Secure.QS_AUTO_ADDED_TILES)
                ? currentAutoAdded : table.optString(Settings.Secure.QS_AUTO_ADDED_TILES);
        final String systemUi = context.getString(com.android.internal.R.string.config_systemUi);
        sendSettingRestored(context, systemUi, Settings.Secure.QS_AUTO_ADDED_TILES,
                currentAutoAdded, autoAdded != null ? autoAdded : "");
        sendSettingRestored(context, systemUi, Settings.Secure.QS_TILES, currentTiles, tiles);
        return true;
    }

    /**
     * UiModeManagerService only applies a dark theme change made through its API, while a
     * restore broadcast just reloads its state, which leaves the screen in the old theme.
     */
    private static boolean restoreNightMode(Context context, JSONObject table) {
        if (!table.has(Settings.Secure.UI_NIGHT_MODE)) {
            return false;
        }
        final UiModeManager uiModeManager = context.getSystemService(UiModeManager.class);
        // An unset setting means the default, which is light
        final int mode = table.isNull(Settings.Secure.UI_NIGHT_MODE)
                ? UiModeManager.MODE_NIGHT_NO
                : table.optInt(Settings.Secure.UI_NIGHT_MODE, UiModeManager.MODE_NIGHT_NO);
        if (mode == uiModeManager.getNightMode()) {
            return false;
        }
        try {
            uiModeManager.setNightMode(mode);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Can't restore the dark theme", e);
            return false;
        }
    }

    private static int restoreVolumes(Context context, JSONObject volumes) {
        if (volumes == null) {
            return 0;
        }
        final AudioManager audioManager = context.getSystemService(AudioManager.class);
        int changed = 0;
        for (int stream : VOLUME_STREAMS) {
            final int volume = volumes.optInt(String.valueOf(stream), -1);
            if (volume < 0 || volume == audioManager.getStreamVolume(stream)) {
                continue;
            }
            try {
                audioManager.setStreamVolume(stream, volume, 0);
                changed++;
            } catch (RuntimeException e) {
                // Muting the ring stream isn't allowed in some Do Not Disturb modes
                Log.w(TAG, "Can't restore volume of stream " + stream, e);
            }
        }
        return changed;
    }

    private static boolean restoreBrightness(Context context, JSONObject root) {
        final double brightness = root.optDouble(KEY_BRIGHTNESS, Double.NaN);
        if (Double.isNaN(brightness) || brightness < 0 || brightness > 1) {
            return false;
        }
        final DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        if (displayManager.getBrightness(Display.DEFAULT_DISPLAY) == (float) brightness) {
            return false;
        }
        displayManager.setBrightness(Display.DEFAULT_DISPLAY, (float) brightness);
        return true;
    }

    private static void sendSettingRestored(Context context, String packageName, String name,
            String previousValue, String newValue) {
        final Intent intent = new Intent(Intent.ACTION_SETTING_RESTORED)
                .setPackage(packageName)
                .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
                .putExtra(Intent.EXTRA_SETTING_NAME, name)
                .putExtra(Intent.EXTRA_SETTING_PREVIOUS_VALUE, previousValue)
                .putExtra(Intent.EXTRA_SETTING_NEW_VALUE, newValue)
                .putExtra(Intent.EXTRA_SETTING_RESTORED_FROM_SDK_INT, Build.VERSION.SDK_INT);
        context.sendBroadcastAsUser(intent, context.getUser());
    }
}
