package io.github.mosser.arduinoml.kernel.structural;

import io.github.mosser.arduinoml.kernel.generator.Visitable;
import io.github.mosser.arduinoml.kernel.generator.Visitor;

public class LCDDisplay implements Visitable {
    private Brick brick;
    private String prefix;
    private BUS bus;

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

    public void setBus(int num) {
        this.bus = BUS.fromInt(num);
    }

    public BUS getBus(){
        return bus;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
