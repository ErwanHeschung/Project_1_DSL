package io.github.mosser.arduinoml.embedded.java.dsl;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.mosser.arduinoml.kernel.App;
import io.github.mosser.arduinoml.kernel.behavioral.ErrorState;
import io.github.mosser.arduinoml.kernel.behavioral.State;
import io.github.mosser.arduinoml.kernel.structural.Actuator;
import io.github.mosser.arduinoml.kernel.structural.Brick;
import io.github.mosser.arduinoml.kernel.structural.Sensor;

public class AppBuilder {

    App theApp = null;

    /*********************
     ** Creating an App **
     *********************/

    public static AppBuilder application(String name) {
        AppBuilder inst = new AppBuilder();
        inst.theApp = new App();
        inst.theApp.setName(name);
        return inst;
    }

    public App build() {
        return theApp;
    }

    private AppBuilder() {
    }

    /**********************
     ** Declaring Bricks **
     **********************/

    public AppBuilder uses(Brick b) {
        this.theApp.getBricks().add(b);
        return this;
    }

    public static Brick sensor(String name, int port) {
        return createBrick(Sensor.class, name, port);
    }

    public static Brick actuator(String name, int port) {
        return createBrick(Actuator.class, name, port);
    }

    private static Brick createBrick(Class<? extends Brick> kind, String name, int port) {
        try {
            Brick b = kind.newInstance();
            if (name.isEmpty() || !Character.isLowerCase(name.charAt(0)))
                throw new IllegalArgumentException("Illegal brick name: [" + name + "]");
            b.setName(name);
            if (port < 1 || port > 12)
                throw new IllegalArgumentException("Illegal brick port: [" + port + "]");
            b.setPin(port);
            return b;
        } catch (InstantiationException | IllegalAccessException iae) {
            throw new IllegalArgumentException("Unable to instantiate " + kind.getCanonicalName());
        }
    }

    /**********************
     ** Declaring States **
     **********************/

    public StateBuilder hasForState(String name) {
        return new StateBuilder(this, name);
    }

    public StateBuilder hasForErrorState(String name, String actuatorName, int errorCode) {
        ErrorState s = new ErrorState();
        s.setName(name);
        s.setErrorCode(errorCode);

        Actuator led = findActuator(actuatorName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown actuator: " + actuatorName));
        s.setActuator(led);

        return new StateBuilder(this, s);
    }

    /*******************************
     ** Declaring TransitionTable **
     *******************************/

    public TransitionTableBuilder beginTransitionTable() {
        // Symbol table pour les états
        Map<String, State> statesByName = theApp.getStates().stream()
                .collect(Collectors.toMap(State::getName, Function.identity()));

        // Symbol table pour les capteurs
        Map<String, Sensor> sensorsByName = theApp.getBricks().stream()
                .filter(b -> b instanceof Sensor)
                .map(b -> (Sensor) b)
                .collect(Collectors.toMap(Sensor::getName, Function.identity()));

        return new TransitionTableBuilder(this, statesByName, sensorsByName);
    }

    /***********************************************************************************
     ** Helpers to avoid a symbol table for Bricks (using the App under construction)
     * **
     ***********************************************************************************/

    Optional<Actuator> findActuator(String name) {
        return theApp.getBricks()
                .stream()
                .filter(brick -> brick instanceof Actuator)
                .map(brick -> (Actuator) brick)
                .filter(actuator -> actuator.getName().equals(name))
                .findFirst();
    }

}
