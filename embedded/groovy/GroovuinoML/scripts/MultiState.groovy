sensor "button" pin 8
actuator "led" pin 12
actuator "buzzer" pin 11

state "buzz" means "buzzer" becomes "high" and "led" becomes "low"
state "led_on" means "buzzer" becomes "low" and "led" becomes "high"
state "off" means "buzzer" becomes "low" and "led" becomes "low"

initial "buzz"

from "buzz" to "led_on" when "button" becomes "high"
from "led_on" to "off" when "button" becomes "high"
from "off" to "buzz" when "button" becomes "high"

export "MultiState"
