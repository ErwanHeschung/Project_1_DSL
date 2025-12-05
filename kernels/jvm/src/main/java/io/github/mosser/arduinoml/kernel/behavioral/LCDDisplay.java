package io.github.mosser.arduinoml.kernel.behavioral;

import io.github.mosser.arduinoml.kernel.generator.Visitable;
import io.github.mosser.arduinoml.kernel.generator.Visitor;
import io.github.mosser.arduinoml.kernel.structural.Brick;

public class LCDDisplay implements Visitable {
    private Brick brick;
    private String prefix;

    public void setBrick(Brick brick) {
        this.brick = brick;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Brick getBrick() {
        return brick;
    }

    public String  getPrefix() {
        return prefix;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
