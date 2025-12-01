package io.github.mosser.arduinoml.embedded.java.dsl;

import java.util.Map;

import io.github.mosser.arduinoml.kernel.behavioral.State;
import io.github.mosser.arduinoml.kernel.structural.Sensor;

public class TransitionTableBuilder {

    final AppBuilder parent;
    final Map<String, State> states;
    final Map<String, Sensor> sensors;

    TransitionTableBuilder(AppBuilder parent,
            Map<String, State> states,
            Map<String, Sensor> sensors) {
        this.parent = parent;
        this.states = states;
        this.sensors = sensors;
    }

    // Point d’entrée du DSL : from("idle") ...
    public TransitionBuilder from(String stateName) {
        return new TransitionBuilder(this, stateName);
    }

    // On remonte vers l’AppBuilder quand on a fini la table
    public AppBuilder endTransitionTable() {
        return parent;
    }

    // Helpers utilisés par TransitionBuilder
    State findState(String stateName) {
        State s = states.get(stateName);
        if (s == null) {
            throw new IllegalArgumentException("Unknown state: [" + stateName + "]");
        }
        return s;
    }

    Sensor findSensor(String sensorName) {
        Sensor s = sensors.get(sensorName);
        if (s == null) {
            throw new IllegalArgumentException("Unknown sensor: [" + sensorName + "]");
        }
        return s;
    }
}