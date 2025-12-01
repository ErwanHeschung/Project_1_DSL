import static io.github.mosser.arduinoml.embedded.java.dsl.AppBuilder.actuator;
import static io.github.mosser.arduinoml.embedded.java.dsl.AppBuilder.application;
import static io.github.mosser.arduinoml.embedded.java.dsl.AppBuilder.sensor;
import io.github.mosser.arduinoml.kernel.App;
import io.github.mosser.arduinoml.kernel.generator.ToWiring;
import io.github.mosser.arduinoml.kernel.generator.Visitor;

public class Main {

    public static void main(String[] args) {

        App app = alarmWithError();

        Visitor codeGenerator = new ToWiring();
        app.accept(codeGenerator);
        System.out.println(codeGenerator.getResult());
    }

    private static App verySimpleAlarm() {
        return application("very_simple_alarm")
                .uses(sensor("button", 9))
                .uses(actuator("led", 10))
                .uses(actuator("buzzer", 11))

                .hasForState("idle").initial()
                .setting("led").toLow()
                .setting("buzzer").toLow()
                .endState()

                .hasForState("alarming")
                .setting("led").toHigh()
                .setting("buzzer").toHigh()
                .endState()

                .beginTransitionTable()
                .from("idle").when("button").isHigh().goTo("alarming")
                .from("alarming").when("button").isLow().goTo("idle")
                .endTransitionTable()
                .build();
    }

    private static App dualCheckAlarm() {
        return application("dual_check_alarm")
                .uses(sensor("b1", 8))
                .uses(sensor("b2", 9))
                .uses(actuator("buzzer", 11))

                .hasForState("idle").initial()
                .setting("buzzer").toLow()
                .endState()

                .hasForState("on")
                .setting("buzzer").toHigh()
                .endState()

                .beginTransitionTable()
                .from("idle")
                .when("b1").isHigh()
                .and("b2").isHigh()
                .goTo("on")

                .from("on")
                .when("b1").isLow()
                .or("b2").isLow()
                .goTo("idle")
                .endTransitionTable()
                .build();
    }

    private static App stateBasedAlarm() {
        return application("state_based_alarm")
                .uses(sensor("button", 9))
                .uses(actuator("led", 12))

                .hasForState("on")
                .setting("led").toHigh()
                .endState()

                .hasForState("off").initial()
                .setting("led").toLow()
                .endState()

                .beginTransitionTable()
                .from("off").when("button").isHigh().goTo("on")
                .from("on").when("button").isHigh().goTo("off")
                .endTransitionTable()
                .build();
    }

    private static App multiStateAlarm() {
        return application("multi_state_alarm")
                .uses(sensor("button", 9))
                .uses(actuator("led", 12))
                .uses(actuator("buzzer", 11))

                .hasForState("ready").initial()
                .setting("led").toLow()
                .setting("buzzer").toLow()
                .endState()

                .hasForState("buzzing")
                .setting("led").toLow()
                .setting("buzzer").toHigh()
                .endState()

                .hasForState("lighting")
                .setting("led").toHigh()
                .setting("buzzer").toLow()
                .endState()

                .beginTransitionTable()
                .from("ready").when("button").isHigh().goTo("buzzing")
                .from("buzzing").when("button").isHigh().goTo("lighting")
                .from("lighting").when("button").isHigh().goTo("ready")
                .endTransitionTable()
                .build();
    }

    private static App alarmWithError() {
        return application("alarm_with_error")
                .uses(sensor("button", 9))
                .uses(sensor("panic", 8))
                .uses(actuator("led", 12))
                .uses(actuator("error_led", 11))

                .hasForState("off").initial()
                .setting("led").toLow()
                .endState()

                .hasForState("on")
                .setting("led").toHigh()
                .endState()

                .hasForErrorState("errButtons", "error_led", 4)
                .endState()

                .beginTransitionTable()
                .from("off").when("button").isHigh().goTo("on")
                .from("on").when("button").isLow().goTo("off")

                .from("off").when("panic").isHigh().goTo("errButtons")
                .from("on").when("panic").isHigh().goTo("errButtons")
                .endTransitionTable()
                .build();
    }

}
