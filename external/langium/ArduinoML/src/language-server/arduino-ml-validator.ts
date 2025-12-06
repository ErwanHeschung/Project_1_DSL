import { ValidationAcceptor, ValidationChecks } from "langium";
import {
  ArduinoMlAstType,
  App,
  isErrorState,
  isSensor,
  isActuator,
} from "./generated/ast";
import type { ArduinoMlServices } from "./arduino-ml-module";

/**
 * Register custom validation checks.
 */
export function registerValidationChecks(services: ArduinoMlServices) {
  const registry = services.validation.ValidationRegistry;
  const validator = services.validation.ArduinoMlValidator;
  const checks: ValidationChecks<ArduinoMlAstType> = {
    App: [
      validator.checkNothing,
      validator.checkErrorLedDefined,
      validator.checkForDuplicatePins,
    ],
  };
  registry.register(checks, validator);
}

/**
 * Implementation of custom validations.
 */
export class ArduinoMlValidator {
  checkNothing(app: App, accept: ValidationAcceptor): void {
    if (app.name) {
      const firstChar = app.name.substring(0, 1);
      if (firstChar.toUpperCase() !== firstChar) {
        accept("warning", "App name should start with a capital.", {
          node: app,
          property: "name",
        });
      }
    }
  }
  checkErrorLedDefined(app: App, accept: ValidationAcceptor) {
    let error_states = app.states.filter(isErrorState);
    if (error_states.length > 0 && !app.errorLed) {
      accept(
        "error",
        "error_led definition is required when error states are defined",
        {
          node: app,
          property: "errorLed",
        },
      );
    }
  }
  checkForDuplicatePins(app: App, accept: ValidationAcceptor): void {
    let pins: Set<number> = new Set();
    if (app.errorLed) {
      pins.add(app.errorLed);
    }
    app.bricks.forEach((brick) => {
      if (isSensor(brick)) {
        if (pins.has(brick.inputPin)) {
          accept("error", "Pin already used", {
            node: brick,
            property: "inputPin",
          });
        } else {
          pins.add(brick.inputPin);
        }
      } else if (isActuator(brick)) {
        if (pins.has(brick.outputPin)) {
          accept("error", "Pin already used", {
            node: brick,
            property: "outputPin",
          });
        } else {
          pins.add(brick.outputPin);
        }
      }
    });
  }
}
