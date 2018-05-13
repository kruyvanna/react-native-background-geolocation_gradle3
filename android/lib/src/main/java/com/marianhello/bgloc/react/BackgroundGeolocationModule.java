
package com.marianhello.bgloc.react;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.intentfilter.androidpermissions.PermissionManager;
import com.marianhello.bgloc.BackgroundGeolocationFacade;
import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.LocationService;
import com.marianhello.bgloc.PluginDelegate;
import com.marianhello.bgloc.PluginException;
import com.marianhello.bgloc.data.BackgroundActivity;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.react.data.LocationMapper;
import com.marianhello.logging.LogEntry;
import com.marianhello.logging.LoggerManager;

import org.json.JSONException;

import java.util.Arrays;
import java.util.Collection;

import static com.marianhello.bgloc.BackgroundGeolocationFacade.PERMISSIONS;

public class BackgroundGeolocationModule extends ReactContextBaseJavaModule implements LifecycleEventListener, PluginDelegate {

    public static final String LOCATION_EVENT = "location";
    public static final String STATIONARY_EVENT = "stationary";
    public static final String ACTIVITY_EVENT = "activity";

    public static final String FOREGROUND_EVENT = "foreground";
    public static final String BACKGROUND_EVENT = "background";
    public static final String AUTHORIZATION_EVENT = "authorization";

    public static final String START_EVENT = "start";
    public static final String STOP_EVENT = "stop";
    public static final String ERROR_EVENT = "error";

    private static final int PERMISSIONS_REQUEST_CODE = 1;

    private BackgroundGeolocationFacade facade;
    private org.slf4j.Logger logger;

    public static class ErrorMap {
        public static ReadableMap from(String message, int code) {
            WritableMap out = Arguments.createMap();
            out.putInt("code", code);
            out.putString("message", message);
            return out;
        }

        public static ReadableMap from(String message, Throwable cause, int code) {
            WritableMap out = Arguments.createMap();
            out.putInt("code", code);
            out.putString("message", message);
            out.putMap("cause", from(cause));
            return out;
        }

        public static ReadableMap from(PluginException e) {
            WritableMap out = Arguments.createMap();
            out.putInt("code", e.getCode());
            out.putString("message", e.getMessage());
            if (e.getCause() != null) {
                out.putMap("cause", from(e.getCause()));
            }

            return out;
        }

        private static WritableMap from(Throwable e) {
            WritableMap out = Arguments.createMap();
            out.putString("message", e.getMessage());
            return out;
        }
    }

    public BackgroundGeolocationModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addLifecycleEventListener(this);

        facade = new BackgroundGeolocationFacade(reactContext, this);
        logger = LoggerManager.getLogger(BackgroundGeolocationModule.class);
    }

    @Override
    public String getName() {
        return "BackgroundGeolocation";
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     */
    @Override
    public void onHostResume() {
        logger.info("App will be resumed");
        facade.switchMode(BackgroundGeolocationFacade.FOREGROUND_MODE);
        sendEvent(FOREGROUND_EVENT, null);
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     */
    @Override
    public void onHostPause() {
        logger.info("App will be paused");
        facade.switchMode(BackgroundGeolocationFacade.BACKGROUND_MODE);
        sendEvent(BACKGROUND_EVENT, null);
    }

    /**
     * The final call you receive before your activity is destroyed.
     * Checks to see if it should turn off
     */
    @Override
    public void onHostDestroy() {
        logger.info("Destroying plugin");
        facade.onAppDestroy();
    }

    private void runOnWebViewThread(Runnable runnable) {
        // currently react-native has no other thread we can run on
        new Thread(runnable).start();
    }


    @ReactMethod
    public void start() {
        runOnWebViewThread(new Runnable() {
            public void run() {
                if (hasPermissions(PERMISSIONS)) {
                    facade.start();
                } else {
                    logger.debug("Permissions not granted");
                    requestPermissions(PERMISSIONS_REQUEST_CODE, BackgroundGeolocationFacade.PERMISSIONS);
                }
            }
        });
    }

    @ReactMethod
    public void stop() {
        runOnWebViewThread(new Runnable() {
            public void run() {
                facade.stop();
            }
        });
    }

    @ReactMethod
    public void switchMode(Integer mode, Callback success, Callback error) {
        facade.switchMode(mode);
    }

    @ReactMethod
    public void configure(final ReadableMap options, final Callback success, final Callback error) {
        runOnWebViewThread(new Runnable() {
            public void run() {
                try {
                    Config config = ConfigMapper.fromMap(options);
                    facade.configure(config);
                    success.invoke(true);
                } catch (JSONException e) {
                    logger.error("Configuration error: {}", e.getMessage());
                    error.invoke(ErrorMap.from("Configuration error", e, PluginException.CONFIGURE_ERROR));
                } catch (PluginException e) {
                    logger.error("Configuration error: {}", e.getMessage());
                    error.invoke(ErrorMap.from(e));
                }
            }
        });
    }

    @Deprecated // use checkStatus as replacement
    @ReactMethod
    public void isLocationEnabled(Callback success, Callback error) {
        logger.debug("Location services enabled check");
        try {
            success.invoke(facade.locationServicesEnabled());
        } catch (Settings.SettingNotFoundException e) {
            logger.error("Location service checked failed: {}", e.getMessage());
            error.invoke(ErrorMap.from(e));
        }
    }

    @ReactMethod
    public void showLocationSettings() {
        BackgroundGeolocationFacade.showLocationSettings(getContext());
    }


    @ReactMethod
    public void showAppSettings() {
        BackgroundGeolocationFacade.showAppSettings(getContext());
    }

    @ReactMethod
    public void getStationaryLocation(Callback success, Callback error) {
        BackgroundLocation stationaryLocation = facade.getStationaryLocation();
        if (stationaryLocation != null) {
            success.invoke(LocationMapper.toWriteableMap(stationaryLocation));
        } else {
            success.invoke();
        }
    }

    @ReactMethod
    public void getLocations(final Callback success, final Callback error) {
        runOnWebViewThread(new Runnable() {
            public void run() {
                WritableArray locationsArray = Arguments.createArray();
                Collection<BackgroundLocation> locations = facade.getLocations();
                for (BackgroundLocation location : locations) {
                    locationsArray.pushMap(LocationMapper.toWriteableMapWithId(location));
                }
                success.invoke(locationsArray);
            }
        });
    }

    @ReactMethod
    public void getValidLocations(final Callback success, Callback error) {
        runOnWebViewThread(new Runnable() {
            public void run() {
                WritableArray locationsArray = Arguments.createArray();
                Collection<BackgroundLocation> locations = facade.getValidLocations();
                for (BackgroundLocation location : locations) {
                    locationsArray.pushMap(LocationMapper.toWriteableMapWithId(location));
                }
                success.invoke(locationsArray);
            }
        });
    }

    @ReactMethod
    public void deleteLocation(final Integer locationId, final Callback success, Callback error) {
        runOnWebViewThread(new Runnable() {
            public void run() {
                facade.deleteLocation(locationId.longValue());
                success.invoke(true);
            }
        });
    }

    @ReactMethod
    public void deleteAllLocations(final Callback success, Callback error) {
        runOnWebViewThread(new Runnable() {
            public void run() {
                facade.deleteAllLocations();
                success.invoke(true);
            }
        });
    }

    @ReactMethod
    public void getConfig(final Callback success, final Callback error) {
        runOnWebViewThread(new Runnable() {
            public void run() {
                try {
                    Config config = facade.getConfig();
                    ReadableMap out = ConfigMapper.toMap(config);
                    success.invoke(out);
                } catch (JSONException e) {
                    logger.error("Error getting config: {}", e.getMessage());
                    error.invoke(ErrorMap.from(e));
                }
            }
        });
    }

    @ReactMethod
    public void getLogEntries(final Integer limit, final Integer offset, final String minLevel, final Callback success, final Callback error) {
        runOnWebViewThread(new Runnable() {
            public void run() {
                WritableArray logEntriesArray = Arguments.createArray();
                Collection<LogEntry> logEntries = facade.getLogEntries(limit, offset, minLevel);
                for (LogEntry logEntry : logEntries) {
                    WritableMap out = Arguments.createMap();
                    out.putInt("id", logEntry.getId());
                    out.putInt("context", logEntry.getContext());
                    out.putString("level", logEntry.getLevel());
                    out.putString("message", logEntry.getMessage());
                    out.putString("timestamp", new Long(logEntry.getTimestamp()).toString());
                    out.putString("logger", logEntry.getLoggerName());
                    if (logEntry.hasStackTrace()) {
                        out.putString("stackTrace", logEntry.getStackTrace());
                    }

                    logEntriesArray.pushMap(out);
                }
                success.invoke(logEntriesArray);
            }
        });
    }

    @ReactMethod
    public void checkStatus(final Callback success, final Callback error) {
        runOnWebViewThread(new Runnable() {
            public void run() {
                try {
                    WritableMap out = Arguments.createMap();
                    out.putBoolean("isRunning", LocationService.isRunning());
                    out.putBoolean("hasPermissions", hasPermissions(PERMISSIONS)); //@Deprecated
                    out.putBoolean("locationServicesEnabled", facade.locationServicesEnabled());
                    out.putInt("authorization", getAuthorizationStatus());
                    success.invoke(out);
                }  catch (Settings.SettingNotFoundException e) {
                    logger.error("Location service checked failed: {}", e.getMessage());
                    error.invoke(ErrorMap.from(e));
                }
            }
        });

    }

    @ReactMethod
    public void headlessTask(String jsFunction, Callback success, Callback error) {
        logger.debug("Registering headless task");
        facade.registerHeadlessTask(jsFunction);
        success.invoke();
    }

    @ReactMethod
    public void forceSync(Callback success, Callback error) {
        facade.forceSync();
        success.invoke();
    }

    private void sendEvent(String eventName, Object params) {
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private void sendError(PluginException error) {
        WritableMap out = Arguments.createMap();
        out.putInt("code", error.getCode());
        out.putString("message", error.getMessage());

        sendEvent(ERROR_EVENT, out);
    }

    private void sendError(int code, String message) {
        WritableMap out = Arguments.createMap();
        out.putInt("code", code);
        out.putString("message", message);

        sendEvent(ERROR_EVENT, out);
    }

    private void requestPermissions(int requestCode, String[] permissions) {
        PermissionManager permissionManager = PermissionManager.getInstance(getContext());
        permissionManager.checkPermissions(Arrays.asList(permissions), new PermissionManager.PermissionRequestListener() {
            @Override
            public void onPermissionGranted() {
                // permission was granted
                // start service
                logger.info("User granted requested permissions");
                facade.start();
            }

            @Override
            public void onPermissionDenied() {
                logger.info("User denied requested permissions");
                sendEvent(AUTHORIZATION_EVENT, BackgroundGeolocationFacade.AUTHORIZATION_DENIED);
            }
        });

    }

    public boolean hasPermissions(String[] permissions) {
        for (String perm: permissions) {
            if (ContextCompat.checkSelfPermission(getReactApplicationContext(), perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public int getAuthorizationStatus() {
        try {
            return facade.getAuthorizationStatus();
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        return -1; // TODO:MAY BE NOT THE RIGHT VALUE
    }

    public Context getContext() {
        return getReactApplicationContext().getBaseContext();
    }

    @Override
    public void onAuthorizationChanged(int authStatus) {
        sendEvent(AUTHORIZATION_EVENT, authStatus);
    }

    @Override
    public void onLocationChanged(BackgroundLocation location) {
        sendEvent(LOCATION_EVENT, LocationMapper.toWriteableMapWithId(location));
    }

    @Override
    public void onStationaryChanged(BackgroundLocation location) {
        sendEvent(STATIONARY_EVENT, LocationMapper.toWriteableMapWithId(location));
    }

    @Override
    public void onActitivyChanged(BackgroundActivity activity) {
        WritableMap out = Arguments.createMap();
        out.putInt("confidence", activity.getConfidence());
        out.putString("type", BackgroundActivity.getActivityString(activity.getType()));
        sendEvent(ACTIVITY_EVENT, out);
    }

    @Override
    public void onLocationPause() {
        sendEvent(STOP_EVENT, null);
    }

    @Override
    public void onLocationResume() {
        sendEvent(START_EVENT, null);
    }

    @Override
    public void onError(PluginException error) {
        sendError(error);
    }
}