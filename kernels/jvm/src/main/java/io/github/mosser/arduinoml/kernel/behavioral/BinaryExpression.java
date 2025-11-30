package io.github.mosser.arduinoml.kernel.behavioral;

public abstract class BinaryExpression extends Expression {
    private Expression leftExpression;
    private Expression rightExpression;

    public Expression getLeftExpression() {
        return leftExpression;
    }

    public void setLeftExpression(Expression leftExpression) {
        this.leftExpression = leftExpression;
    }

    public Expression getRightExpression() {
        return rightExpression;
    }

    public void setRightExpression(Expression rightExpression) {
        this.rightExpression = rightExpression;
    }
}

