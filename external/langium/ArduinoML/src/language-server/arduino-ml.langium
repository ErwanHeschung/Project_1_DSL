grammar ArduinoMl

entry App:
	'app' name=ID 'initial' 'state' initial=[State:ID]
	'{'
		'bricks'
		bricks+=Brick
		(bricks+=Brick)* 
		
		'states'
		states+=State
		( states+=State)* 		
	'}';

Brick :
	(Actuator | Sensor);

Actuator:
	{infer Actuator}
	'Actuator' name=ID ':' outputPin=INT;

Sensor :
	{infer Sensor}
	'Sensor'  name=ID ':' inputPin=INT;
	
State :
	name=ID '{'
		actions+=Action
		(actions+=Action)* 
		transition=Transition
	'}';


Action :
	actuator=[Actuator:ID] '<=' value=Signal;

Transition:
    condition=Expr '=>' next=[State:ID];

Expr:
    OrExpr;

OrExpr:
    {infer OrExpr}
    left=AndExpr 'OR' right=OrExpr
  | {infer OrExpr} AndExpr;

AndExpr:
    {infer AndExpr}
    left=Condition 'AND' right=AndExpr
  | {infer AndExpr} Condition;

Condition:
    {infer Condition}
    sensor=[Sensor:ID] 'is' value=Signal
  | '(' expr=Expr ')';


Signal:
    value=(HIGH | LOW);
terminal HIGH : 'HIGH';
terminal LOW : 'LOW';

hidden terminal WS: /\s+/;
terminal ID: /[_a-zA-Z][\w_]*/;
terminal INT returns number: /[0-9]+/;
terminal STRING: /"[^"]*"|'[^']*'/;

hidden terminal ML_COMMENT: /\/\*[\s\S]*?\*\//;
hidden terminal SL_COMMENT: /\/\/[^\n\r]*/;
