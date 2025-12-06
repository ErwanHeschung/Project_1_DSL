%{
   #include "arduino_syntax.h"

%}
%option noyywrap
%option yylineno

%%
  /* ==== Keywords ==== */
"application"           return KAPPL;
"sensor"                return KSENSOR;
"actuator"              return KACTUATOR;
"is"                    return KIS;
"HIGH"                  return KHIGH;
"LOW"                   return KLOW;
"=>"                    return RIGHT;
"<="                    return LEFT;
"->"                    return INITSTATE;
"after"                 return KAFTER;
"and"                   return AND;
"or"                    return OR;

  /* ==== port numbers (as strings) and identifiers */
[0-9]+                  { yylval.value = atoi(yytext); return INTEGER; }
[a-zA-Z][a-zA-Z0-9_]+   { yylval.name  = strdup(yytext); return IDENT; }

  /* ==== Spaces and comments ==== */
[ \t\n]                 { }
"//".*                  { }

 /* ==== Default ==== */
.                       return *yytext;

