grammar ArduinoMl

entry App:
	'app' name=ID 'initial' 'state' initial=[State:ID]
	'{'
		('error_led' ':' errorLed=INT)?

		'bricks'
		bricks+=Brick
		(bricks+=Brick)* 
		
		'states'
		states+=State
		(states+=State)* 		
	'}';

Brick:
	Actuator | Sensor;

Actuator:
	'Actuator' name=ID ':' outputPin=INT;

Sensor:
	'Sensor' name=ID ':' inputPin=INT;

State: 
	NormalState | ErrorState;

ErrorState:
	'error' ',' name=ID ',' 'code' ',' errorCode=INT;

NormalState:
	name=ID '{'
		(actions+=Action)*
		transition=Transition
	'}';

Action:
	actuator=[Actuator:ID] '<=' value=Signal;

Transition:
	condition=Expr '=>' next=[State:ID];

Expr:
	OrExpr;

OrExpr infers Expr:
	AndExpr ({infer OrExpr.left=current} 'OR' right=AndExpr)*;

AndExpr infers Expr:
	Condition ({infer AndExpr.left=current} 'AND' right=Condition)*;

Condition infers Expr:
	{infer Condition} sensor=[Sensor:ID] 'is' value=Signal
	| '(' Expr ')';

Signal:
	value=('HIGH' | 'LOW');

hidden terminal WS: /\s+/;
terminal ID: /[_a-zA-Z][\w_]*/;
terminal INT returns number: /[0-9]+/;
terminal STRING: /"[^"]*"|'[^']*'/;

hidden terminal ML_COMMENT: /\/\*[\s\S]*?\*\//;
hidden terminal SL_COMMENT: /\/\/[^\n\r]*/;
