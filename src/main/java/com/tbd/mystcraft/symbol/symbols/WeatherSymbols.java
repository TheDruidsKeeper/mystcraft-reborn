package com.tbd.mystcraft.symbol.symbols;

import com.tbd.mystcraft.age.weather.CyclingWeather;
import com.tbd.mystcraft.age.weather.ToggleableWeather;
import com.tbd.mystcraft.api.symbol.AgeDirector;
import com.tbd.mystcraft.api.symbol.logic.WeatherController;

import java.util.function.Supplier;

import static com.tbd.mystcraft.api.symbol.WordData.*;

/** Weather symbols (original spec §4.3.4). */
public final class WeatherSymbols {
    private WeatherSymbols() {}

    /** Generic weather symbol: registers a fresh controller from the factory. */
    public static final class WeatherSymbol extends SimpleSymbol {
        private final Supplier<? extends WeatherController> factory;

        public WeatherSymbol(String path, int rank, Supplier<? extends WeatherController> factory, String... words) {
            super(path, rank, words);
            this.factory = factory;
        }

        @Override
        public void registerLogic(AgeDirector director, long seed) {
            director.registerInterface(factory.get());
        }
    }

    public static WeatherSymbol normal() {
        return new WeatherSymbol("weather_normal", 2, CyclingWeather::normal, SUSTAIN, DYNAMIC, TRADITION, BALANCE);
    }

    public static WeatherSymbol fast() {
        return new WeatherSymbol("weather_fast", 3, CyclingWeather::fast, SUSTAIN, DYNAMIC, TRADITION, SPUR);
    }

    public static WeatherSymbol slow() {
        return new WeatherSymbol("weather_slow", 3, CyclingWeather::slow, SUSTAIN, DYNAMIC, TRADITION, INHIBIT);
    }

    public static WeatherSymbol off() {
        return new WeatherSymbol("weather_off", 3, ToggleableWeather::off, SUSTAIN, STATIC, STIMULATE, ENERGY);
    }

    public static WeatherSymbol on() {
        return new WeatherSymbol("weather_on", 3, ToggleableWeather::on, SUSTAIN, STATIC, TRADITION, STIMULATE);
    }

    public static WeatherSymbol cloudy() {
        return new WeatherSymbol("weather_cloudy", 3, ToggleableWeather::cloudy, SUSTAIN, STATIC, BELIEVE, MOTION);
    }

    public static WeatherSymbol rain() {
        return new WeatherSymbol("weather_rain", 3, ToggleableWeather::rain, SUSTAIN, STATIC, REBIRTH, GROWTH);
    }

    public static WeatherSymbol snow() {
        return new WeatherSymbol("weather_snow", 3, ToggleableWeather::snow, SUSTAIN, STATIC, INHIBIT, ENERGY);
    }

    public static WeatherSymbol storm() {
        return new WeatherSymbol("weather_storm", 3, ToggleableWeather::storm, SUSTAIN, STATIC, NATURE, POWER);
    }
}
