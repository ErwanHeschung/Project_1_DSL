// Yacc grammar for ArduinoML in C
//
//           Author: Erick Gallesio [eg@unice.fr]
//    Creation date: 16-Nov-2017 17:54 (eg)
// Last file update: 30-Nov-2017 15:27 (eg)

%{
#define  YYERROR_VERBOSE 1      // produce verbose syntax error messages

#include <stdio.h>
#include <stdlib.h>
#include "arduino_syntax.h"
#include "arduino.h"

#define  YYERROR_VERBOSE 1      // produce verbose syntax error messages

//  Prototypes
int  yylex(void);
void yyerror(const char *s);
%}

%union {
    int                        value;
    char                       *name;
    struct arduino_transition  *transition;
    struct arduino_action      *action;
    struct arduino_state       *state;
    struct arduino_brick       *brick;
    struct arduino_expression  *expression;
};

%token KAPPL KSENSOR KACTUATOR KIS LEFT RIGHT INITSTATE KAFTER KERROR_LED KERROR KCODE
%token  <name>          IDENT KHIGH KLOW
%token  <value>         INTEGER

%type   <name>          name
%type   <value>         signal port error_led
%type   <transition>    transition transitions
%type   <action>        action actions
%type   <state>         state states
%type   <brick>         brick bricks
%type   <expression>    expression condition

%left OR
%left AND
%%

start:          KAPPL name '{' error_led bricks  states '}'           { emit_code($2, $4, $5, $6); }
     ;

error_led:      KERROR_LED ':' INTEGER ';'                  { $$ = $3; }
         |      /* empty */                                 { $$ = -1; }
         ;

bricks:         bricks brick ';'                            { $$ = add_brick($2, $1); }
      |         error ';'                                   { yyerrok; }
      |         /* empty */                                 { $$ = NULL; }
      ;

brick:          KACTUATOR name ':' port                     { $$ = make_brick($4, actuator, $2); }
     |          KSENSOR   name ':' port                     { $$ = make_brick($4, sensor, $2); }
     ;

states:         states state                                { $$ = add_state($1, $2); }
      |         /*empty */                                  { $$ = NULL; }
      ;

state:          name '{' actions  transitions '}'            { $$ = make_state($1, $3, $4, 0); }
      |         INITSTATE name '{' actions  transitions '}'  { $$ = make_state($2, $4, $5, 1); }
      |         KERROR name KCODE INTEGER ';'                { $$ = make_error_state($2, $4); }
      ;


actions:        actions action ';'                          { $$ = add_action($1, $2); }
       |        /* empty */                                 { $$ = NULL; }
       ;

action:          name LEFT signal                           { $$ = make_action($1, $3); }
      ;

transitions:    transitions transition ';'                  { $$ = add_transition($1, $2); }
           |    transition ';'                              { $$ = $1; }
           |    error ';'                                   { yyerrok; }
           ;

transition:     expression RIGHT name                       { $$ = make_transition($1, $3); }
           |    KAFTER INTEGER RIGHT name                   { $$ = make_delay_transition($2, $4); }
           ;

signal:         KHIGH                                       { $$ = 1; }
      |         KLOW                                        { $$ = 0; }
      ;

condition:   name KIS signal                                {$$ = make_condition_expression($1, $3);}
         ;

expression:     expression OR expression                    {$$ = make_binary_expression(OR_OP, $1, $3);}
          |     expression AND expression                   {$$ = make_binary_expression(AND_OP, $1, $3);}
          |     condition                                   {$$ = $1;}
          |     '(' expression ')'                          {$$ = $2;}
          ;

name:           IDENT          ;
port:           INTEGER        ;


%%
void yyerror(const char *msg) { extern int yylineno; error_msg(yylineno, msg); }
