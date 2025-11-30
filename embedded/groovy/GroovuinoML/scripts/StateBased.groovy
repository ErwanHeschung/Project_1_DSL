sensor "button" pin 8
actuator "red_led" pin 12

state "off" means "red_led" becomes "low"
state "on" means "red_led" becomes "high"

initial "off"

from "off" to "on" when "button" becomes "high"
from "on" to "off" when "button" becomes "high"

export "RedButton"
