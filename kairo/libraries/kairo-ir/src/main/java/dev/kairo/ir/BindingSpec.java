package dev.kairo.ir;

public record BindingSpec(String targetProperty, String expression, boolean twoWay) {
    public static BindingSpec oneWay(String targetProperty, String expression) {
        return new BindingSpec(targetProperty, expression, false);
    }

    public static BindingSpec twoWay(String targetProperty, String expression) {
        return new BindingSpec(targetProperty, expression, true);
    }
}
