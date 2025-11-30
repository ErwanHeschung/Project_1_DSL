sensor "b1" pin 8
sensor "b2" pin 9
actuator "buzzer" pin 11

state "idle" means "buzzer" becomes "low"
state "on" means "buzzer" becomes "high"

initial "idle"

from "idle" to "on" when "b1" becomes "high" and "b2" becomes "high"
from "on" to "idle" when "b1" becomes "low" or "b2" becomes "low"

export "DualCheck"