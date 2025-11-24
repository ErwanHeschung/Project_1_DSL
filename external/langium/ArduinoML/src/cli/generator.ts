import fs from 'fs';
import { CompositeGeneratorNode, NL, toString } from 'langium';
import path from 'path';
import { Action, Actuator, App, Sensor, State, Transition } from '../language-server/generated/ast';
import { extractDestinationAndName } from './cli-util';

export function generateInoFile(app: App, filePath: string, destination: string | undefined): string {
    const data = extractDestinationAndName(filePath, destination);
    const generatedFilePath = `${path.join(data.destination, data.name)}.ino`;

    const fileNode = new CompositeGeneratorNode();
    compile(app,fileNode)
    
    
    if (!fs.existsSync(data.destination)) {
        fs.mkdirSync(data.destination, { recursive: true });
    }
    fs.writeFileSync(generatedFilePath, toString(fileNode));
    return generatedFilePath;
}


function compile(app:App, fileNode:CompositeGeneratorNode){
    fileNode.append(
	`
//Wiring code generated from an ArduinoML model
// Application name: `+app.name+`

long debounce = 200;
enum STATE {`+app.states.map(s => s.name).join(', ')+`};

STATE currentState = `+app.initial.ref?.name+`;`
    ,NL);
	
    for(const brick of app.bricks){
        if ("inputPin" in brick){
            fileNode.append(`
bool `+brick.name+`BounceGuard = false;
long `+brick.name+`LastDebounceTime = 0;

            `,NL);
        }
    }
    fileNode.append(`
	void setup(){`);
    for(const brick of app.bricks){
        if ("inputPin" in brick){
       		compileSensor(brick,fileNode);
		}else{
            compileActuator(brick,fileNode);
        }
	}


    fileNode.append(`
	}
	void loop() {
			switch(currentState){`,NL)
			for(const state of app.states){
				compileState(state, fileNode)
            }
	fileNode.append(`
		}
	}
	`,NL);




    }

	function compileActuator(actuator: Actuator, fileNode: CompositeGeneratorNode) {
        fileNode.append(`
		pinMode(`+actuator.outputPin+`, OUTPUT); // `+actuator.name+` [Actuator]`)
    }

	function compileSensor(sensor:Sensor, fileNode: CompositeGeneratorNode) {
    	fileNode.append(`
		pinMode(`+sensor.inputPin+`, INPUT); // `+sensor.name+` [Sensor]`)
	}

    function compileState(state: State, fileNode: CompositeGeneratorNode) {
        fileNode.append(`
				case `+state.name+`:`)
		for(const action of state.actions){
			compileAction(action, fileNode)
		}
		if (state.transition !== null){
			compileTransition(state.transition, fileNode)
		}
		fileNode.append(`
				break;`)
    }
	

	function compileAction(action: Action, fileNode:CompositeGeneratorNode) {
		fileNode.append(`
					digitalWrite(`+action.actuator.ref?.outputPin+`,`+action.value.value+`);`)
	}

	function compileTransition(transition: Transition, fileNode: CompositeGeneratorNode) {
		const sensors = collectSensors(transition.condition);

		sensors.forEach(sensor => {
			fileNode.append(`${sensor.name}BounceGuard = millis() - ${sensor.name}LastDebounceTime > debounce;\n`);
		});

		const conditionCode = compileExpr(transition.condition);

		fileNode.append(`
			if (${conditionCode}) {
				${sensors.map(s => `${s.name}LastDebounceTime = millis();`).join('\n')}
				currentState = ${transition.next.ref?.name};
			}
		`);
	}

	function compileExpr(expr: any): string {
		if ('left' in expr && 'right' in expr) {
			const left = compileExpr(expr.left);
			const right = compileExpr(expr.right);
			if (expr.$type === 'OrExpr') return `(${left} || ${right})`;
			if (expr.$type === 'AndExpr') return `(${left} && ${right})`;
		} else if ('sensor' in expr && 'value' in expr) {
			const sensorName = expr.sensor.ref.name;
			const value = expr.value.value;
			return `(digitalRead(${expr.sensor.ref.inputPin}) == ${value} && ${sensorName}BounceGuard)`;
		} else if ('condition' in expr) {
			return `(${compileExpr(expr.condition)})`;
		}
		throw new Error("Unknown Expr type");
	}

	function collectSensors(expr: any): { name: string }[] {
		if ('left' in expr && 'right' in expr) {
			return [...collectSensors(expr.left), ...collectSensors(expr.right)];
		} else if ('sensor' in expr) {
			return [{ name: expr.sensor.ref.name }];
		} else if ('condition' in expr) {
			return collectSensors(expr.condition);
		}
		return [];
	}
