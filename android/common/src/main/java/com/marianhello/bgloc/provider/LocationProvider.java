/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

This is a new class
*/

package com.marianhello.bgloc.provider;

import com.marianhello.bgloc.Config;

/**
 * LocationProvider interface
 */
public interface LocationProvider {

    void onCreate();
    void onDestroy();
    void onStart();
    void onStop();
    void onCommand(int mode);
    void onConfigure(Config config);
    void setDelegate(ProviderDelegate delegate);
    boolean isStarted();
}
