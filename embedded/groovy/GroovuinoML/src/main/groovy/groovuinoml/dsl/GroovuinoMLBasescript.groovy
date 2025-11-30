package main.groovy.groovuinoml.dsl

import io.github.mosser.arduinoml.kernel.behavioral.TimeUnit
import io.github.mosser.arduinoml.kernel.behavioral.Action
import io.github.mosser.arduinoml.kernel.behavioral.State
import io.github.mosser.arduinoml.kernel.behavioral.Condition
import io.github.mosser.arduinoml.kernel.behavioral.And
import io.github.mosser.arduinoml.kernel.behavioral.Or
import io.github.mosser.arduinoml.kernel.structural.Actuator
import io.github.mosser.arduinoml.kernel.structural.Sensor
import io.github.mosser.arduinoml.kernel.structural.SIGNAL

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
	
	// from state1 to state2 when sensor becomes signal
	def from(state1) {
		[to: { state2 ->
			// recursive closure to allow multiple and/or chains
			def closure
			closure = { sensor ->
				[becomes: { signal ->
					Sensor sensorObj = sensor instanceof String ? (Sensor)((GroovuinoMLBinding)this.getBinding()).getVariable(sensor) : (Sensor)sensor
					SIGNAL signalObj = signal instanceof String ? (SIGNAL)((GroovuinoMLBinding)this.getBinding()).getVariable(signal) : (SIGNAL)signal
					
					Condition condition = new Condition()
					condition.setSensor(sensorObj)
					condition.setValue(signalObj)
		
					State fromState = state1 instanceof String ? (State)((GroovuinoMLBinding)this.getBinding()).getVariable(state1) : (State)state1
					State toState = state2 instanceof String ? (State)((GroovuinoMLBinding)this.getBinding()).getVariable(state2) : (State)state2
					def model = ((GroovuinoMLBinding) this.getBinding()).getGroovuinoMLModel()
	
					if(fromState.getTransition() == null) {
						model.createTransition(fromState, toState, condition)
					} else {
						def existing = fromState.getTransition().getExpression()
						And a = new And()
						a.setLeftExpression(existing)
						a.setRightExpression(condition)
						fromState.getTransition().setExpression(a)
					}
					[and: closure, or: { s2 ->
						Sensor sensorObj2 = s2 instanceof String ? (Sensor)((GroovuinoMLBinding)this.getBinding()).getVariable(s2) : (Sensor)s2
						[becomes: { signal2 ->
							SIGNAL signalObj2 = signal2 instanceof String ? (SIGNAL)((GroovuinoMLBinding)this.getBinding()).getVariable(signal2) : (SIGNAL)signal2
							Condition condition2 = new Condition()
							condition2.setSensor(sensorObj2)
							condition2.setValue(signalObj2)
							def existing = fromState.getTransition().getExpression()
							Or o = new Or()
							o.setLeftExpression(existing)
							o.setRightExpression(condition2)
							fromState.getTransition().setExpression(o)
							[and: closure, or: closure]
						}]
					}]
				}]
			} 

			[when: closure,
			 after: { delay ->
				 ((GroovuinoMLBinding) this.getBinding()).getGroovuinoMLModel().createTransition(
					 state1 instanceof String ? (State)((GroovuinoMLBinding)this.getBinding()).getVariable(state1) : (State)state1,
					 state2 instanceof String ? (State)((GroovuinoMLBinding)this.getBinding()).getVariable(state2) : (State)state2,
					 delay)
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
