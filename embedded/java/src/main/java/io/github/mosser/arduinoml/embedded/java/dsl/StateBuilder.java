package io.github.mosser.arduinoml.embedded.java.dsl;

import io.github.mosser.arduinoml.kernel.behavioral.ErrorState;
import io.github.mosser.arduinoml.kernel.behavioral.State;

public class StateBuilder {

    AppBuilder parent;
    State local; // ← plus d'instanciation ici

    StateBuilder(AppBuilder parent, String name) {
        this.parent = parent;
        this.local = new State();
        this.local.setName(name);
    }

    // ← AJOUT : constructeur pour ErrorState
    StateBuilder(AppBuilder parent, ErrorState state) {
        this.parent = parent;
        this.local = state;
    }

    public InstructionBuilder setting(String sensorName) {
        return new InstructionBuilder(this, sensorName);
    }

    public StateBuilder initial() {
        parent.theApp.setInitial(this.local);
        return this;
    }

    public AppBuilder endState() {
        parent.theApp.getStates().add(this.local);
        return parent;
    }

}