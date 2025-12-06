package main.groovy.groovuinoml.dsl

import io.github.mosser.arduinoml.kernel.behavioral.TimeUnit
import io.github.mosser.arduinoml.kernel.behavioral.Action
import io.github.mosser.arduinoml.kernel.behavioral.State
import io.github.mosser.arduinoml.kernel.behavioral.Condition
import io.github.mosser.arduinoml.kernel.behavioral.And
import io.github.mosser.arduinoml.kernel.behavioral.Or
import io.github.mosser.arduinoml.kernel.structural.LCDDisplay
import io.github.mosser.arduinoml.kernel.structural.Actuator
import io.github.mosser.arduinoml.kernel.structural.Sensor
import io.github.mosser.arduinoml.kernel.structural.SIGNAL
import io.github.mosser.arduinoml.kernel.structural.Brick

abstract class GroovuinoMLBasescript extends Script {
//	public static Number getDuration(Number number, TimeUnit unit) throws IOException {
//		return number * unit.inMillis;
//	}

	// sensor "name" pin n
	def sensor(String name) {
		[pin: { n -> ((GroovuinoMLBinding)this.getBinding()).getGroovuinoMLModel().createSensor(name, n) },
		onPin: { n -> ((GroovuinoMLBinding)this.getBinding()).getGroovuinoMLModel().createSensor(name, n)}]
	}
	
	// actuator "name" pin n
	def actuator(String name) {
		[pin: { n -> ((GroovuinoMLBinding)this.getBinding()).getGroovuinoMLModel().createActuator(name, n) }]
	}
	
	// state "name" means actuator becomes signal [and actuator becomes signal]*n
	def state(String name) {
		List<Action> actions = new ArrayList<Action>()
		((GroovuinoMLBinding) this.getBinding()).getGroovuinoMLModel().createState(name, actions)
		// recursive closure to allow multiple and statements
		def closure
		closure = { actuator -> 
			[becomes: { signal ->
				Action action = new Action()
				action.setActuator(actuator instanceof String ? (Actuator)((GroovuinoMLBinding)this.getBinding()).getVariable(actuator) : (Actuator)actuator)
				action.setValue(signal instanceof String ? (SIGNAL)((GroovuinoMLBinding)this.getBinding()).getVariable(signal) : (SIGNAL)signal)
				actions.add(action)
				[and: closure]
			}]
		}
		[means: closure]
	}
	
	// initial state
	def initial(state) {
		((GroovuinoMLBinding) this.getBinding()).getGroovuinoMLModel().setInitialState(state instanceof String ? (State)((GroovuinoMLBinding)this.getBinding()).getVariable(state) : (State)state)
	}
	
	def resolve(obj) {
		(obj instanceof String) ? ((GroovuinoMLBinding)this.getBinding()).getVariable(obj) : obj
	}

	def from(state1) {
		[to: { state2 ->

			def closure
			closure = { sensor ->
				[becomes: { signal ->

					Sensor sensorObj = resolve(sensor) as Sensor
					SIGNAL signalObj = resolve(signal) as SIGNAL
					Condition condition = new Condition(sensor: sensorObj, value: signalObj)

					State fromState = resolve(state1) as State
					State toState = resolve(state2) as State
					def model = ((GroovuinoMLBinding)this.getBinding()).groovuinoMLModel

					if (fromState.transitions.isEmpty()) {
						model.createTransition(fromState, toState, condition)
					}

					//only support one transition for now (taking the last one)
					def lastTransition = fromState.transitions[-1]

					def andClosure, orClosure
					andClosure = { s2 ->
						Sensor sObj = resolve(s2) as Sensor
						[becomes: { sig2 ->
							SIGNAL sigObj = resolve(sig2) as SIGNAL
							Condition cond2 = new Condition(sensor: sObj, value: sigObj)

							def existing = lastTransition.expression

							if (existing instanceof Or) {
								existing.rightExpression = new And(
									leftExpression: existing.rightExpression,
									rightExpression: cond2
								)
							} else {
								existing = new And(leftExpression: existing, rightExpression: cond2)
							}

							lastTransition.expression = existing

							[and: andClosure, or: orClosure]
						}]
					}

					orClosure = { s2 ->
						Sensor sObj = resolve(s2) as Sensor
						[becomes: { sig2 ->
							SIGNAL sigObj = resolve(sig2) as SIGNAL
							Condition cond2 = new Condition(sensor: sObj, value: sigObj)

							def existing = lastTransition.expression

							def newOr = new Or(leftExpression: existing, rightExpression: cond2)
							lastTransition.expression = newOr

							[and: andClosure, or: orClosure]
						}]
					}

					[and: andClosure, or: orClosure]
				}]
			}

			// Optionnel : after delay
			[when: closure,
			after: { delay ->
				State fState = resolve(state1) as State
				State tState = resolve(state2) as State
				((GroovuinoMLBinding)this.getBinding()).groovuinoMLModel.createTransition(fState, tState, delay)
			}]
		}]
	}

	def display(String name) {
		[on_bus: { bus ->
			Brick brick = resolve(name) as Brick
			def model = ((GroovuinoMLBinding)this.getBinding()).groovuinoMLModel

			model.createLCD(brick, "",bus)
			[prefixed: { String newPrefix ->
				model.createLCD(brick, newPrefix,bus)
			}]
		}]
	}

	// export name
	def export(String name) {
		println(((GroovuinoMLBinding) this.getBinding()).getGroovuinoMLModel().generateCode(name).toString())
	}
	
	// disable run method while running
	int count = 0
	abstract void scriptBody()
	def run() {
		if(count == 0) {
			count++
			scriptBody()
		} else {
			println "Run method is disabled"
		}
	}
}
