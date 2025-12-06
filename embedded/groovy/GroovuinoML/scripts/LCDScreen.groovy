sensor "button" pin 10
actuator "red_led" pin 9

display "red_led" on_bus 1 prefixed "RED LED :"

state "off" means "red_led" becomes "low"
state "on" means "red_led" becomes "high"

initial "off"

from "off" to "on" when "button" becomes "high"
from "on" to "off" when "button" becomes "high"

export "LCDScreen"
