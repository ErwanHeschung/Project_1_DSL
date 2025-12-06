package io.github.mosser.arduinoml.kernel.utils;

import io.github.mosser.arduinoml.kernel.App;
import io.github.mosser.arduinoml.kernel.structural.BUS;
import io.github.mosser.arduinoml.kernel.structural.Brick;
import io.github.mosser.arduinoml.kernel.structural.LCDDisplay;

import java.util.HashSet;
import java.util.Set;

public class Validator {
    private Validator() {}

    public static void validatePinUsage(App app) {
        Set<Integer> usedPins = new HashSet<>();

        for (Brick brick : app.getBricks()) {
            int pin = brick.getPin();
            checkPinUsed(pin, usedPins, "Brick " + brick.getName());
        }

        if (app.getLCDDisplay() != null) {
            LCDDisplay lcd = app.getLCDDisplay();
            BUS bus = lcd.getBus();
            for (String pinStr : bus.getPins()) {
                int pin = convertPin(pinStr);
                checkPinUsed(pin, usedPins, "LCD [" + lcd.getBrick().getName() + "] sur bus " + bus.name() + " pin " + pinStr + "\nBus pin : " + app.getLCDDisplay().getBus());
            }
        }
    }

    private static int convertPin(String pinStr) {
        if (pinStr.startsWith("A")) {
            return 14 + Integer.parseInt(pinStr.substring(1));
        }
        return Integer.parseInt(pinStr);
    }

    private static void checkPinUsed(int pin, Set<Integer> usedPins, String context) {
        if (usedPins.contains(pin)) {
            throw new IllegalArgumentException("Pin deja utilisee: " + pin + " (" + context + ")");
        }
        usedPins.add(pin);
    }
}
