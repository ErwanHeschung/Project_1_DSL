package io.github.mosser.arduinoml.kernel.generator;

import java.util.HashMap;
import java.util.Map;

import io.github.mosser.arduinoml.kernel.App;
import io.github.mosser.arduinoml.kernel.behavioral.Action;
import io.github.mosser.arduinoml.kernel.behavioral.And;
import io.github.mosser.arduinoml.kernel.behavioral.Condition;
import io.github.mosser.arduinoml.kernel.behavioral.ErrorState;
import io.github.mosser.arduinoml.kernel.behavioral.Or;
import io.github.mosser.arduinoml.kernel.behavioral.SignalTransition;
import io.github.mosser.arduinoml.kernel.behavioral.State;
import io.github.mosser.arduinoml.kernel.behavioral.TimeTransition;
import io.github.mosser.arduinoml.kernel.structural.Actuator;
import io.github.mosser.arduinoml.kernel.structural.Sensor;

public abstract class Visitor<T> {

	public abstract void visit(App app);

	public abstract void visit(State state);

	public abstract void visit(SignalTransition transition);

	public abstract void visit(TimeTransition transition);

	public abstract void visit(Action action);

	public abstract void visit(Actuator actuator);

	public abstract void visit(Sensor sensor);

	public abstract void visit(Condition condition);

	public abstract void visit(And and);

	public abstract void visit(Or or);

	public abstract void visit(ErrorState errorState);

	/***********************
	 ** Helper mechanisms **
	 ***********************/

	protected Map<String, Object> context = new HashMap<>();

	protected T result;

	public T getResult() {
		return result;
	}

}