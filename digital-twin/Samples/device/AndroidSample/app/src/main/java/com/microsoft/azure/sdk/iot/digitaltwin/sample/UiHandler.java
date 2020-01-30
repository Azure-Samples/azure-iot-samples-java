package com.microsoft.azure.sdk.iot.digitaltwin.sample;

interface UiHandler {
    void updateName(String name);
    void updateBrightness(double brightness);
    void updateTemperatureAndHumidity(double temperature, double humidity);
    void updateOnoff(boolean on);
    void startBlink(long interval);
}
