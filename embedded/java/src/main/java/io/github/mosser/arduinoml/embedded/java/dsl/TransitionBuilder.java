package io.github.mosser.arduinoml.embedded.java.dsl;

import io.github.mosser.arduinoml.kernel.behavioral.And;
import io.github.mosser.arduinoml.kernel.behavioral.BinaryExpression;
import io.github.mosser.arduinoml.kernel.behavioral.Condition;
import io.github.mosser.arduinoml.kernel.behavioral.Expression;
import io.github.mosser.arduinoml.kernel.behavioral.Or;
import io.github.mosser.arduinoml.kernel.behavioral.SignalTransition;
import io.github.mosser.arduinoml.kernel.behavioral.State;
import io.github.mosser.arduinoml.kernel.structural.SIGNAL;
import io.github.mosser.arduinoml.kernel.structural.Sensor;

public class TransitionBuilder {

    private final TransitionTableBuilder parent;
    private final State fromState;

    // Expression globale de la transition (peut être Condition, And, Or, etc.)
    private Expression expression;

    // Capteur en cours de description (pour isHigh / isLow)
    private Sensor currentSensor;

    // Opérateur logique en attente (AND / OR) pour combiner avec l’expression
    // existante
    private LogicalOp pendingOp = null;

    // Enum interne au DSL, pas dans le kernel
    private enum LogicalOp {
        AND, OR
    }

    TransitionBuilder(TransitionTableBuilder parent, String fromStateName) {
        this.parent = parent;
        this.fromState = parent.findState(fromStateName);
    }

    // ------------------ Construction du DSL ------------------ //

    // Premier capteur / capteur suivant
    public TransitionBuilder when(String sensorName) {
        this.currentSensor = parent.findSensor(sensorName);
        return this;
    }

    public TransitionBuilder and(String sensorName) {
        this.pendingOp = LogicalOp.AND;
        this.currentSensor = parent.findSensor(sensorName);
        return this;
    }

    public TransitionBuilder or(String sensorName) {
        this.pendingOp = LogicalOp.OR;
        this.currentSensor = parent.findSensor(sensorName);
        return this;
    }

    public TransitionBuilder isHigh() {
        return withValue(SIGNAL.HIGH);
    }

    public TransitionBuilder isLow() {
        return withValue(SIGNAL.LOW);
    }

    // ------------------ Construction de l'arbre d'expressions ------------------
    // //

    private TransitionBuilder withValue(SIGNAL value) {
        if (currentSensor == null) {
            throw new IllegalStateException("Sensor must be set before setting value (use when/and/or)");
        }

        // On crée une Condition pour le capteur courant
        Condition condition = new Condition();
        condition.setSensor(currentSensor);
        condition.setValue(value);

        if (expression == null) {
            // Première condition : l'expression globale est juste cette condition
            expression = condition;
        } else {
            // Il y avait déjà une expression : on la combine avec ET / OU
            if (pendingOp == null) {
                // Cas bizarre: on a déjà une expression mais pas d'opérateur → on écrase ou on
                // lève une erreur.
                // Pour rester simple, on considère que c'est une erreur d'utilisation du DSL.
                throw new IllegalStateException("Logical operator (and/or) missing before new condition");
            }
            BinaryExpression binary;
            if (pendingOp == LogicalOp.AND) {
                binary = new And();
            } else {
                binary = new Or();
            }
            binary.setLeftExpression(expression);
            binary.setRightExpression(condition);
            expression = binary;
        }

        // On a consommé l'opérateur logique
        pendingOp = null;
        // On "oublie" le capteur courant après avoir créé sa condition
        currentSensor = null;

        return this;
    }

    // ------------------ Fin de la transition ------------------ //

    public TransitionTableBuilder goTo(String targetStateName) {
        if (expression == null) {
            throw new IllegalStateException("Transition without condition (expression is null)");
        }

        State target = parent.findState(targetStateName);

        SignalTransition transition = new SignalTransition();
        transition.setExpression(expression);
        transition.setNext(target);

        fromState.addTransition(transition);

        return parent;
    }
}
