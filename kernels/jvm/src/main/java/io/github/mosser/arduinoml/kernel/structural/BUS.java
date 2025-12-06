package io.github.mosser.arduinoml.kernel.structural;

public enum BUS {
    BUS1(new String[]{"2", "3", "4", "5", "6", "7", "8"}),
    BUS2(new String[]{"10", "11", "12", "13", "A0", "A1", "A2"}),
    BUS3(new String[]{"10", "11", "12", "13", "A4", "A5", "1"});

    private final String[] pins;

    BUS(String[] pins) {
        this.pins = pins;
    }

    public String[] getPins() {
        return pins;
    }

    public static BUS fromInt(int num) {
        switch (num) {
            case 1: return BUS1;
            case 2: return BUS2;
            case 3: return BUS3;
            default: throw new IllegalArgumentException("Bus invalide: " + num);
        }
    }

    @Override
    public String toString() {
        return name() + " { pins: " + String.join(", ", pins) + " }";
    }
}