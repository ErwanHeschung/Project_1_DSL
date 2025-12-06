import fs from "fs";
import { CompositeGeneratorNode, NL, toString } from "langium";
import path from "path";
import {
  Action,
  Actuator,
  App,
  State,
  Sensor,
  NormalState,
  ErrorState,
  Transition,
  isNormalState,
  isErrorState,
} from "../language-server/generated/ast";
import { extractDestinationAndName } from "./cli-util";

export function generateInoFile(
  app: App,
  filePath: string,
  destination: string | undefined,
): string {
  const data = extractDestinationAndName(filePath, destination);
  const generatedFilePath = `${path.join(data.destination, data.name)}.ino`;
  const fileNode = new CompositeGeneratorNode();
  compile(app, fileNode);
  if (!fs.existsSync(data.destination)) {
    fs.mkdirSync(data.destination, { recursive: true });
  }
  fs.writeFileSync(generatedFilePath, toString(fileNode));
  return generatedFilePath;
}

function compile(app: App, fileNode: CompositeGeneratorNode) {
  fileNode.append(`//Wiring code generated from an ArduinoML model`, NL);
  fileNode.append(`// Application name: ${app.name}`, NL, NL);

  fileNode.append(`long debounce = 200;`, NL, NL);

  fileNode.append(`// Pin definitions`, NL);
  if (app.errorLed) {
    fileNode.append(`#define ERROR_LED_PIN ${app.errorLed}`, NL, NL);
  }
  const errorStates: ErrorState[] = app.states.filter(isErrorState);
  if (errorStates.length > 0) {
    fileNode.append(`// Error codes`, NL);
    for (const errorState of errorStates) {
      fileNode.append(
        `#define ERROR_${errorState.name.toUpperCase()} ${errorState.errorCode}`,
        NL,
      );
    }
    fileNode.append(NL);
  }

  fileNode.append(`// State machine`, NL);
  fileNode.append(
    `enum STATE {${app.states.map((s) => s.name).join(", ")}};`,
    NL,
  );
  fileNode.append(`STATE currentState = ${app.initial.ref?.name};`, NL, NL);

  fileNode.append(`// Sensor bounce guards`, NL);
  for (const brick of app.bricks) {
    if ("inputPin" in brick) {
      fileNode.append(`bool ${brick.name}BounceGuard = false;`, NL);
      fileNode.append(`long ${brick.name}LastDebounceTime = 0;`, NL);
    }
  }
  fileNode.append(NL);

  compileSetup(app, fileNode);
  fileNode.append(NL);

  compileLoop(app, fileNode);
  fileNode.append(NL);

  if (errorStates.length > 0) {
    compileBlinkErrorFunction(fileNode);
  }
}

function compileSetup(app: App, fileNode: CompositeGeneratorNode) {
  fileNode.append(`void setup() {`, NL);

  if (app.errorLed) {
    fileNode.append(`\t// Initialize error LED`, NL);
    fileNode.append(`\tpinMode(ERROR_LED_PIN, OUTPUT);`, NL);
    fileNode.append(`\tdigitalWrite(ERROR_LED_PIN, LOW);`, NL);
  }
  fileNode.append(NL);

  fileNode.append(`\t// Initialize sensors and actuators`, NL);
  for (const brick of app.bricks) {
    if ("inputPin" in brick) {
      compileSensor(brick, fileNode);
    } else {
      compileActuator(brick, fileNode);
    }
  }

  fileNode.append(`}`, NL);
}

function compileLoop(app: App, fileNode: CompositeGeneratorNode) {
  fileNode.append(`void loop() {`, NL);
  fileNode.append(`\tswitch(currentState) {`, NL);

  for (const state of app.states) {
    compileState(state, fileNode);
  }

  fileNode.append(`\t}`, NL);
  fileNode.append(`}`, NL);
}

function compileState(state: State, fileNode: CompositeGeneratorNode) {
  if (isNormalState(state)) {
    compileNormalState(state, fileNode);
  } else if (isErrorState(state)) {
    compileErrorState(state, fileNode);
  }
}

function compileActuator(actuator: Actuator, fileNode: CompositeGeneratorNode) {
  fileNode.append(
    `\tpinMode(${actuator.outputPin}, OUTPUT); // ${actuator.name} [Actuator]`,
    NL,
  );
}

function compileSensor(sensor: Sensor, fileNode: CompositeGeneratorNode) {
  fileNode.append(
    `\tpinMode(${sensor.inputPin}, INPUT);  // ${sensor.name} [Sensor]`,
    NL,
  );
}

function compileNormalState(
  state: NormalState,
  fileNode: CompositeGeneratorNode,
) {
  fileNode.append(`\t\tcase ${state.name}:`, NL);

  for (const action of state.actions) {
    compileAction(action, fileNode);
  }

  for (const transition of state.transitions) {
    compileTransition(transition, fileNode);
  }

  fileNode.append(`\t\t\tbreak;`, NL);
}

function compileAction(action: Action, fileNode: CompositeGeneratorNode) {
  fileNode.append(
    `\t\t\tdigitalWrite(${action.actuator.ref?.outputPin}, ${action.value.value});`,
    NL,
  );
}

function compileTransition(
  transition: Transition,
  fileNode: CompositeGeneratorNode,
) {
  if ("delay" in transition) {
    fileNode.append(`\t\t\tdelay(${transition.delay});`, NL);
    fileNode.append(`\t\t\tcurrentState = ${transition.next.ref?.name};`, NL);
  } else if ("condition" in transition) {
    const sensors = collectSensors(transition.condition);

    fileNode.append(`\t\t\t// Update bounce guards`, NL);
    sensors.forEach((sensor) => {
      fileNode.append(
        `\t\t\t${sensor.name}BounceGuard = millis() - ${sensor.name}LastDebounceTime > debounce;`,
        NL,
      );
    });
    fileNode.append(NL);

    const conditionCode = compileExpr(transition.condition);
    fileNode.append(`\t\t\t// Check transition condition`, NL);
    fileNode.append(`\t\t\tif (${conditionCode}) {`, NL);

    sensors.forEach((sensor) => {
      fileNode.append(`\t\t\t\t${sensor.name}LastDebounceTime = millis();`, NL);
    });

    fileNode.append(`\t\t\t\tcurrentState = ${transition.next.ref?.name};`, NL);
    fileNode.append(`\t\t\t}`, NL);
  }
}

function compileExpr(expr: any): string {
  if ("left" in expr && "right" in expr) {
    const left = compileExpr(expr.left);
    const right = compileExpr(expr.right);
    if (expr.$type === "OrExpr") return `(${left} || ${right})`;
    if (expr.$type === "AndExpr") return `(${left} && ${right})`;
  } else if ("sensor" in expr && "value" in expr) {
    const sensorName = expr.sensor.ref.name;
    const value = expr.value.value;
    return `(digitalRead(${expr.sensor.ref.inputPin}) == ${value} && ${sensorName}BounceGuard)`;
  } else if ("expr" in expr) {
    return `(${compileExpr(expr.expr)})`;
  }
  throw new Error("Unknown Expr type");
}

function compileErrorState(
  state: ErrorState,
  fileNode: CompositeGeneratorNode,
): void {
  fileNode.append(`\t\tcase ${state.name}:`, NL);
  fileNode.append(`\t\t\tblinkError(ERROR_${state.name.toUpperCase()});`, NL);
  fileNode.append(`\t\t\t// Error state - no transitions (sink state)`, NL);
  fileNode.append(`\t\t\tbreak;`, NL);
}

function compileBlinkErrorFunction(
  fileNode: CompositeGeneratorNode,
  blinkDuration: number = 300,
  pauseDuration: number = 1500,
) {
  fileNode.append(`void blinkError(int errorCode) {`, NL);
  fileNode.append(`\t// Blink the error LED 'errorCode' times`, NL);
  fileNode.append(`\tfor (int i = 0; i < errorCode; i++) {`, NL);
  fileNode.append(`\t\tdigitalWrite(ERROR_LED_PIN, HIGH);`, NL);
  fileNode.append(`\t\tdelay(${blinkDuration});`, NL);
  fileNode.append(`\t\tdigitalWrite(ERROR_LED_PIN, LOW);`, NL);
  fileNode.append(`\t\tdelay(${blinkDuration});`, NL);
  fileNode.append(`\t}`, NL);
  fileNode.append(`\t// Pause between repetitions`, NL);
  fileNode.append(`\tdelay(${pauseDuration});`, NL);
  fileNode.append(`}`, NL);
}

function collectSensors(expr: any): { name: string }[] {
  if ("left" in expr && "right" in expr) {
    return [...collectSensors(expr.left), ...collectSensors(expr.right)];
  } else if ("sensor" in expr) {
    return [{ name: expr.sensor.ref.name }];
  } else if ("expr" in expr) {
    return collectSensors(expr.expr);
  }
  return [];
}
