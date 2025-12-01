"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.generateInoFile = void 0;
const fs_1 = __importDefault(require("fs"));
const langium_1 = require("langium");
const path_1 = __importDefault(require("path"));
const ast_1 = require("../language-server/generated/ast");
const cli_util_1 = require("./cli-util");
function generateInoFile(app, filePath, destination) {
    const data = (0, cli_util_1.extractDestinationAndName)(filePath, destination);
    const generatedFilePath = `${path_1.default.join(data.destination, data.name)}.ino`;
    const fileNode = new langium_1.CompositeGeneratorNode();
    compile(app, fileNode);
    if (!fs_1.default.existsSync(data.destination)) {
        fs_1.default.mkdirSync(data.destination, { recursive: true });
    }
    fs_1.default.writeFileSync(generatedFilePath, (0, langium_1.toString)(fileNode));
    return generatedFilePath;
}
exports.generateInoFile = generateInoFile;
function compile(app, fileNode) {
    var _a;
    fileNode.append(`//Wiring code generated from an ArduinoML model`, langium_1.NL);
    fileNode.append(`// Application name: ${app.name}`, langium_1.NL, langium_1.NL);
    fileNode.append(`long debounce = 200;`, langium_1.NL, langium_1.NL);
    fileNode.append(`// Pin definitions`, langium_1.NL);
    fileNode.append(`#define ERROR_LED_PIN ${app.errorLed}`, langium_1.NL, langium_1.NL);
    const errorStates = app.states.filter(ast_1.isErrorState);
    if (errorStates.length > 0) {
        fileNode.append(`// Error codes`, langium_1.NL);
        for (const errorState of errorStates) {
            fileNode.append(`#define ERROR_${errorState.name.toUpperCase()} ${errorState.errorCode}`, langium_1.NL);
        }
        fileNode.append(langium_1.NL);
    }
    fileNode.append(`// State machine`, langium_1.NL);
    fileNode.append(`enum STATE {${app.states.map((s) => s.name).join(", ")}};`, langium_1.NL);
    fileNode.append(`STATE currentState = ${(_a = app.initial.ref) === null || _a === void 0 ? void 0 : _a.name};`, langium_1.NL, langium_1.NL);
    fileNode.append(`// Sensor bounce guards`, langium_1.NL);
    for (const brick of app.bricks) {
        if ("inputPin" in brick) {
            fileNode.append(`bool ${brick.name}BounceGuard = false;`, langium_1.NL);
            fileNode.append(`long ${brick.name}LastDebounceTime = 0;`, langium_1.NL);
        }
    }
    fileNode.append(langium_1.NL);
    compileSetup(app, fileNode);
    fileNode.append(langium_1.NL);
    compileLoop(app, fileNode);
    fileNode.append(langium_1.NL);
    if (errorStates.length > 0) {
        compileBlinkErrorFunction(fileNode);
    }
}
function compileSetup(app, fileNode) {
    fileNode.append(`void setup() {`, langium_1.NL);
    fileNode.append(`\t// Initialize error LED`, langium_1.NL);
    fileNode.append(`\tpinMode(ERROR_LED_PIN, OUTPUT);`, langium_1.NL);
    fileNode.append(`\tdigitalWrite(ERROR_LED_PIN, LOW);`, langium_1.NL);
    fileNode.append(langium_1.NL);
    fileNode.append(`\t// Initialize sensors and actuators`, langium_1.NL);
    for (const brick of app.bricks) {
        if ("inputPin" in brick) {
            compileSensor(brick, fileNode);
        }
        else {
            compileActuator(brick, fileNode);
        }
    }
    fileNode.append(`}`, langium_1.NL);
}
function compileLoop(app, fileNode) {
    fileNode.append(`void loop() {`, langium_1.NL);
    fileNode.append(`\tswitch(currentState) {`, langium_1.NL);
    for (const state of app.states) {
        compileState(state, fileNode);
    }
    fileNode.append(`\t}`, langium_1.NL);
    fileNode.append(`}`, langium_1.NL);
}
function compileState(state, fileNode) {
    if ((0, ast_1.isNormalState)(state)) {
        compileNormalState(state, fileNode);
    }
    else if ((0, ast_1.isErrorState)(state)) {
        compileErrorState(state, fileNode);
    }
}
function compileActuator(actuator, fileNode) {
    fileNode.append(`\tpinMode(${actuator.outputPin}, OUTPUT); // ${actuator.name} [Actuator]`, langium_1.NL);
}
function compileSensor(sensor, fileNode) {
    fileNode.append(`\tpinMode(${sensor.inputPin}, INPUT);  // ${sensor.name} [Sensor]`, langium_1.NL);
}
function compileNormalState(state, fileNode) {
    fileNode.append(`\t\tcase ${state.name}:`, langium_1.NL);
    for (const action of state.actions) {
        compileAction(action, fileNode);
    }
    if (state.transition !== null) {
        compileTransition(state.transition, fileNode);
    }
    fileNode.append(`\t\t\tbreak;`, langium_1.NL);
}
function compileAction(action, fileNode) {
    var _a;
    fileNode.append(`\t\t\tdigitalWrite(${(_a = action.actuator.ref) === null || _a === void 0 ? void 0 : _a.outputPin}, ${action.value.value});`, langium_1.NL);
}
function compileTransition(transition, fileNode) {
    var _a;
    const sensors = collectSensors(transition.condition);
    fileNode.append(`\t\t\t// Update bounce guards`, langium_1.NL);
    sensors.forEach((sensor) => {
        fileNode.append(`\t\t\t${sensor.name}BounceGuard = millis() - ${sensor.name}LastDebounceTime > debounce;`, langium_1.NL);
    });
    fileNode.append(langium_1.NL);
    const conditionCode = compileExpr(transition.condition);
    fileNode.append(`\t\t\t// Check transition condition`, langium_1.NL);
    fileNode.append(`\t\t\tif (${conditionCode}) {`, langium_1.NL);
    sensors.forEach((sensor) => {
        fileNode.append(`\t\t\t\t${sensor.name}LastDebounceTime = millis();`, langium_1.NL);
    });
    fileNode.append(`\t\t\t\tcurrentState = ${(_a = transition.next.ref) === null || _a === void 0 ? void 0 : _a.name};`, langium_1.NL);
    fileNode.append(`\t\t\t}`, langium_1.NL);
}
function compileExpr(expr) {
    if ("left" in expr && "right" in expr) {
        const left = compileExpr(expr.left);
        const right = compileExpr(expr.right);
        if (expr.$type === "OrExpr")
            return `(${left} || ${right})`;
        if (expr.$type === "AndExpr")
            return `(${left} && ${right})`;
    }
    else if ("sensor" in expr && "value" in expr) {
        const sensorName = expr.sensor.ref.name;
        const value = expr.value.value;
        return `(digitalRead(${expr.sensor.ref.inputPin}) == ${value} && ${sensorName}BounceGuard)`;
    }
    else if ("expr" in expr) {
        return `(${compileExpr(expr.expr)})`;
    }
    throw new Error("Unknown Expr type");
}
function compileErrorState(state, fileNode) {
    fileNode.append(`\t\tcase ${state.name}:`, langium_1.NL);
    fileNode.append(`\t\t\tblinkError(ERROR_${state.name.toUpperCase()});`, langium_1.NL);
    fileNode.append(`\t\t\t// Error state - no transitions (sink state)`, langium_1.NL);
    fileNode.append(`\t\t\tbreak;`, langium_1.NL);
}
function compileBlinkErrorFunction(fileNode, blinkDuration = 300, pauseDuration = 1500) {
    fileNode.append(`void blinkError(int errorCode) {`, langium_1.NL);
    fileNode.append(`\t// Blink the error LED 'errorCode' times`, langium_1.NL);
    fileNode.append(`\tfor (int i = 0; i < errorCode; i++) {`, langium_1.NL);
    fileNode.append(`\t\tdigitalWrite(ERROR_LED_PIN, HIGH);`, langium_1.NL);
    fileNode.append(`\t\tdelay(${blinkDuration});`, langium_1.NL);
    fileNode.append(`\t\tdigitalWrite(ERROR_LED_PIN, LOW);`, langium_1.NL);
    fileNode.append(`\t\tdelay(${blinkDuration});`, langium_1.NL);
    fileNode.append(`\t}`, langium_1.NL);
    fileNode.append(`\t// Pause between repetitions`, langium_1.NL);
    fileNode.append(`\tdelay(${pauseDuration});`, langium_1.NL);
    fileNode.append(`}`, langium_1.NL);
}
function collectSensors(expr) {
    if ("left" in expr && "right" in expr) {
        return [...collectSensors(expr.left), ...collectSensors(expr.right)];
    }
    else if ("sensor" in expr) {
        return [{ name: expr.sensor.ref.name }];
    }
    else if ("expr" in expr) {
        return collectSensors(expr.expr);
    }
    return [];
}
//# sourceMappingURL=generator.js.map