package io.github.mosser.arduinoml.kernel.generator;

import io.github.mosser.arduinoml.kernel.App;
import io.github.mosser.arduinoml.kernel.behavioral.Action;
import io.github.mosser.arduinoml.kernel.behavioral.And;
import io.github.mosser.arduinoml.kernel.behavioral.BinaryExpression;
import io.github.mosser.arduinoml.kernel.behavioral.Condition;
import io.github.mosser.arduinoml.kernel.behavioral.ErrorState;
import io.github.mosser.arduinoml.kernel.behavioral.Expression;
import io.github.mosser.arduinoml.kernel.behavioral.Or;
import io.github.mosser.arduinoml.kernel.behavioral.SignalTransition;
import io.github.mosser.arduinoml.kernel.behavioral.State;
import io.github.mosser.arduinoml.kernel.behavioral.TimeTransition;
import io.github.mosser.arduinoml.kernel.behavioral.Transition;
import io.github.mosser.arduinoml.kernel.structural.Actuator;
import io.github.mosser.arduinoml.kernel.structural.Brick;
import io.github.mosser.arduinoml.kernel.structural.Sensor;

/**
 * Quick and dirty visitor to support the generation of Wiring code
 */
public class ToWiring extends Visitor<StringBuffer> {
	enum PASS {
		ONE, TWO
	}

	public ToWiring() {
		this.result = new StringBuffer();
	}

	private void w(String s) {
		result.append(String.format("%s", s));
	}

	@Override
	public void visit(App app) {
		// first pass, create global vars
		context.put("pass", PASS.ONE);
		w("// Wiring code generated from an ArduinoML model\n");
		w(String.format("// Application name: %s\n", app.getName()) + "\n");

		w("long debounce = 200;\n");
		w("\nenum STATE {");
		String sep = "";
		for (State state : app.getStates()) {
			w(sep);
			state.accept(this);
			sep = ", ";
		}
		w("};\n");
		if (app.getInitial() != null) {
			w("STATE currentState = " + app.getInitial().getName() + ";\n");
		}

		for (Brick brick : app.getBricks()) {
			brick.accept(this);
		}

		// second pass, setup and loop
		context.put("pass", PASS.TWO);
		w("\nvoid setup(){\n");
		for (Brick brick : app.getBricks()) {
			brick.accept(this);
		}
		w("}\n");

		w("\nvoid loop() {\n" +
				"\tswitch(currentState){\n");
		for (State state : app.getStates()) {
			state.accept(this);
		}
		w("\t}\n" +
				"}");
	}

	@Override
	public void visit(Actuator actuator) {
		if (context.get("pass") == PASS.ONE) {
			return;
		}
		if (context.get("pass") == PASS.TWO) {
			w(String.format("  pinMode(%d, OUTPUT); // %s [Actuator]\n", actuator.getPin(), actuator.getName()));
			return;
		}
	}

	@Override
	public void visit(Sensor sensor) {
		if (context.get("pass") == PASS.ONE) {
			w(String.format("\nboolean %sBounceGuard = false;\n", sensor.getName()));
			w(String.format("long %sLastDebounceTime = 0;\n", sensor.getName()));
			return;
		}
		if (context.get("pass") == PASS.TWO) {
			w(String.format("  pinMode(%d, INPUT);  // %s [Sensor]\n", sensor.getPin(), sensor.getName()));
			return;
		}
	}

	@Override
	public void visit(State state) {
		if (context.get("pass") == PASS.ONE) {
			w(state.getName());
			return;
		}
		if (context.get("pass") == PASS.TWO) {
			w("\t\tcase " + state.getName() + ":\n");
			for (Action action : state.getActions()) {
				action.accept(this);
			}

			for (Transition t : state.getTransitions()) {
				t.accept(this);
			}
			w("\t\tbreak;\n");

			return;
		}

	}

	@Override
	public void visit(SignalTransition transition) {
		if (context.get("pass") == PASS.ONE) {
			return;
		}
		if (context.get("pass") == PASS.TWO) {
			Expression expression = transition.getExpression();
			// Update bounce times for all sensors in the expression
			updateBounceGuards(expression);
			w("\t\t\tif( ");
			expression.accept(this);
			w(" ) {\n");
			// Assign debounce time for all sensors when transition is triggered
			assignDebounceTime(expression);
			w("\t\t\t\tcurrentState = " + transition.getNext().getName() + ";\n");
			w("\t\t\t}\n");
		}
	}

	private void updateBounceGuards(Expression expression) {
		if (expression instanceof Condition) {
			Condition condition = (Condition) expression;
			String sensorName = condition.getSensor().getName();
			w(String.format("\t\t\t%sBounceGuard = millis() - %sLastDebounceTime > debounce;\n",
					sensorName, sensorName));
		} else if (expression instanceof BinaryExpression) {
			BinaryExpression binary = (BinaryExpression) expression;
			updateBounceGuards(binary.getLeftExpression());
			updateBounceGuards(binary.getRightExpression());
		}
	}

	private void assignDebounceTime(Expression expression) {
		if (expression instanceof Condition) {
			Condition condition = (Condition) expression;
			String sensorName = condition.getSensor().getName();
			w(String.format("\t\t\t\t%sLastDebounceTime = millis();\n", sensorName));
		} else if (expression instanceof BinaryExpression) {
			BinaryExpression binary = (BinaryExpression) expression;
			assignDebounceTime(binary.getLeftExpression());
			assignDebounceTime(binary.getRightExpression());
		}
	}

	@Override
	public void visit(Condition condition) {
		String sensorName = condition.getSensor().getName();
		w(String.format("digitalRead(%d) == %s && %sBounceGuard",
				condition.getSensor().getPin(), condition.getValue(), sensorName));
	}

	@Override
	public void visit(And and) {
		w("(");
		and.getLeftExpression().accept(this);
		w(") && (");
		and.getRightExpression().accept(this);
		w(")");
	}

	@Override
	public void visit(Or or) {
		w("(");
		or.getLeftExpression().accept(this);
		w(") || (");
		or.getRightExpression().accept(this);
		w(")");
	}

	@Override
	public void visit(TimeTransition transition) {
		if (context.get("pass") == PASS.ONE) {
			return;
		}
		if (context.get("pass") == PASS.TWO) {
			int delayInMS = transition.getDelay();
			w(String.format("\t\t\tdelay(%d);\n", delayInMS));
			w("\t\t\t\tcurrentState = " + transition.getNext().getName() + ";\n");
			w("\t\t\t}\n");
			return;
		}
	}

	@Override
	public void visit(Action action) {
		if (context.get("pass") == PASS.ONE) {
			return;
		}
		if (context.get("pass") == PASS.TWO) {
			w(String.format("\t\t\tdigitalWrite(%d,%s);\n", action.getActuator().getPin(), action.getValue()));
			return;
		}
	}

	@Override
	public void visit(ErrorState state) {
		if (context.get("pass") == PASS.ONE) {
			// 1er passage : on veut juste le nom dans l'enum STATE
			w(state.getName());
			return;
		}

		if (context.get("pass") == PASS.TWO) {
			int pin = state.getActuator().getPin();
			int code = state.getErrorCode();

			w("\t\tcase " + state.getName() + ":\n");

			// (optionnel) exécuter aussi les actions normales de l'état
			for (Action action : state.getActions()) {
				action.accept(this);
			}

			w("\t\t\t// Error state: blink code " + code + " on pin " + pin + "\n");
			w("\t\t\tfor (int i = 0; i < " + code + "; i++) {\n");
			w("\t\t\t\tdigitalWrite(" + pin + ", HIGH);\n");
			w("\t\t\t\tdelay(200);\n");
			w("\t\t\t\tdigitalWrite(" + pin + ", LOW);\n");
			w("\t\t\t\tdelay(200);\n");
			w("\t\t\t}\n");
			w("\t\t\tdelay(800);\n");
			w("\t\t\tbreak;\n");
		}
	}

}
