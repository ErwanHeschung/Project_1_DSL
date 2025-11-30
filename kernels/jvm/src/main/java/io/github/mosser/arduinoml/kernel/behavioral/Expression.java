package io.github.mosser.arduinoml.kernel.behavioral;

import io.github.mosser.arduinoml.kernel.generator.Visitor;

public abstract class Expression {
    public abstract void accept(Visitor visitor);
}
