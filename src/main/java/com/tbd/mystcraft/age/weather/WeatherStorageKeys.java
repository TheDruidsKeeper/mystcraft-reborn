package com.tbd.mystcraft.age.weather;

/** Keys used inside {@code AgeData.data("weather")} by the weather controllers (original spec §4.3.4). */
public final class WeatherStorageKeys {
    private WeatherStorageKeys() {}

    /** Cycling controllers. */
    public static final String RAINING = "raining";
    public static final String THUNDERING = "thundering";
    public static final String RAIN_COUNTER = "rain_counter";
    public static final String THUNDER_COUNTER = "thunder_counter";

    /** Toggleable controllers. */
    public static final String DISABLED = "disabled";
    public static final String RESET_COUNTER = "reset_counter";

    /** Ticks after a toggle before a toggleable controller re-enables itself. */
    public static final int RESET_COOLDOWN = 12000;
}
