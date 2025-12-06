/*
 * arduino.c     -- ArduinoML configuration and code generation
 *
 *           Author: Erick Gallesio [eg@unice.fr]
 *    Creation date: 17-Nov-2017 11:13
 * Last file update:  1-Dec-2017 18:47 (eg)
 */

#include "arduino.h"
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

extern int yylineno;           ///< line number (managed by lex)
static int error_detected = 0; ///< The number of errors while compiling a file
char *input_path = NULL;       ///< Name of the input path or NULL if stdin

static void *__must_malloc(size_t sz, const char *func, const char *file,
                           int line);
#define must_malloc(_sz) (__must_malloc((_sz), __func__, __FILE__, __LINE__))

// ======================================================================
//                              B R I C K S
// ======================================================================
struct arduino_brick {
  char *var;
  enum port_assignment kind;
  int port_number;
  struct arduino_brick *next;
};

/// Find name in the list of already declared bricks
static int find_brick(char *name, Brick *list) {
  for (Brick *p = list; p; p = p->next) {
    if (strcmp(name, p->var) == 0)
      return 1;
  }
  return 0;
}

/// declare a new brick on port `number`
Brick *make_brick(int number, enum port_assignment kind, char *name) {
  Brick *p = must_malloc(sizeof(Brick));

  p->var = name;
  p->kind = kind;
  p->port_number = number;
  p->next = NULL;
  return p;
}

/// Add a brick to a list of bricks
Brick *add_brick(Brick *b, Brick *list) {
  // Check that the given variable is not already used
  if (find_brick(b->var, list)) {
    error_msg(yylineno, "name '%s' was already used", b->var);
  }
  // Check that the given port is not already used
  for (Brick *p = list; p; p = p->next) {
    if (p->port_number == b->port_number)
      error_msg(yylineno, "port %d was already used by '%s'", p->port_number,
                p->var);
  }
  b->next = list;
  return b;
}

// ======================================================================
//                            T R A N S I T I O N
// ======================================================================
struct arduino_transition {
  int lineno;
  Expression* expression;
  char *newstate;
};

/// Make a new transition (when `var` is `signal` goto `newstate`
Transition *make_transition(Expression* expression, char *newstate) {
  Transition *p = must_malloc(sizeof(Transition));

  p->lineno = yylineno;
  p->expression = expression;
  p->newstate = newstate;
  return p;
}

// ======================================================================
//                            A C T I O N
// ======================================================================
struct arduino_action {
  int lineno;
  char *var_name;
  int sig_value;
  struct arduino_action *next;
};

// Make a new action (setting `var` to `signal`)
Action *make_action(char *var, int signal) {
  Action *p = must_malloc(sizeof(Action));

  p->lineno = yylineno;
  p->var_name = var;
  p->sig_value = signal;
  p->next = NULL;
  return p;
}

// Add an action to a list of actions
Action *add_action(Action *list, Action *a) {
  if (list) {
    Action *tmp = list;
    while (tmp->next)
      tmp = tmp->next;
    tmp->next = a;
    return list;
  }
  return a;
}

// ======================================================================
//                            S Τ A Τ E
// ======================================================================
struct arduino_state {
  int lineno;
  char *name;
  Action *actions;
  Transition *transition;
  struct arduino_state *next;
};

static State *initial_state = NULL;

static int find_state(char *name, State *list) {
  for (State *p = list; p; p = p->next) {
    if (strcmp(name, p->name) == 0)
      return 1;
  }
  return 0;
}

// Make a new state named `var` with a list of `actions` and a `transition`
// `initial` must be one if the state is the initial one
State *make_state(char *var, Action *actions, Transition *transition,
                  int initial) {
  State *p = must_malloc(sizeof(State));

  p->lineno = yylineno;
  p->name = var;
  p->actions = actions;
  p->transition = transition;
  p->next = NULL;
  if (initial)
    initial_state = p; // Keep a reference on the initial state
  return p;
}

// Add a state to a list of states
State *add_state(State *list, State *s) {
  if (list) {
    State *tmp = list;
    while (tmp->next)
      tmp = tmp->next;
    tmp->next = s;
    return list;
  }
  return s;
}

// ======================================================================
//                        E X P R E S S I O N S
// ======================================================================

struct arduino_expression {
  enum ExpType type;
  union {
    BinaryExpression binary_expression;
    Condition condition;
  } data;
};

Expression *make_binary_expression(enum Operator op, Expression *right,
                                   Expression *left) {
  Expression *expression = malloc(sizeof(Expression));
  expression->type = (op == OR_OP) ? OR_EXP : AND_EXP;
  expression->data.binary_expression.op = op;
  expression->data.binary_expression.left = left;
  expression->data.binary_expression.right = right;
  return expression;
}

Expression *make_condition_expression(char *sensor_name, int value) {
  Expression *expression = malloc(sizeof(Expression));
  expression->type = CONDITION;
  expression->data.condition.sensor_name = sensor_name;
  expression->data.condition.value = value;
  return expression;
}

// ======================================================================
//                     S E M A N T I C   C H E C K S
// ======================================================================

static void check_expression(Brick *brick_list, Expression *expr, int lineno) {
  if (!expr) return;

  switch (expr->type) {
    case CONDITION:
      // Verify that the sensor is declared
      if (!find_brick(expr->data.condition.sensor_name, brick_list))
        error_msg(lineno, "undeclared '%s'", expr->data.condition.sensor_name);
      break;
    case OR_EXP:
    case AND_EXP:
      // Recursively check both sides of the binary expression
      check_expression(brick_list, expr->data.binary_expression.left, lineno);
      check_expression(brick_list, expr->data.binary_expression.right, lineno);
      break;
  }
}

static void check_actions(Brick *brick_list, Action *list) {
  for (Action *current = list; current; current = current->next) {
    // Verify that the variable used in this action is declared
    if (!find_brick(current->var_name, brick_list))
      error_msg(list->lineno, "undeclared '%s'", current->var_name);
  }
}

static void check_transition(Brick *brick_list, State *state_list,
                             Transition *trans) {
  // Verify that sensors in the expression are declared
  check_expression(brick_list, trans->expression, trans->lineno);
  // Verify that the next state exists
  if (!find_state(trans->newstate, state_list))
    error_msg(trans->lineno, "undeclared state '%s'", trans->newstate);
}

static void check_states(Brick *brick_list, State *state_list) {
  for (State *current = state_list; current; current = current->next) {
    check_actions(brick_list, current->actions);
    check_transition(brick_list, state_list, current->transition);
    if (find_state(current->name, current->next))
      error_msg(current->lineno, "duplicate state name: '%s'", current->name);
  }
}

// ======================================================================
//                      C O D E   P R O D U C T I O N
// ======================================================================

static void emit_expression(Expression *expr);

static void emit_condition(Condition *cond) {
  printf("digitalRead(%s) == %s", cond->sensor_name,
         cond->value ? "HIGH" : "LOW");
}

static void emit_expression(Expression *expr) {
  if (!expr) return;

  switch (expr->type) {
    case CONDITION:
      emit_condition(&expr->data.condition);
      break;
    case OR_EXP:
      printf("(");
      emit_expression(expr->data.binary_expression.left);
      printf(" || ");
      emit_expression(expr->data.binary_expression.right);
      printf(")");
      break;
    case AND_EXP:
      printf("(");
      emit_expression(expr->data.binary_expression.left);
      printf(" && ");
      emit_expression(expr->data.binary_expression.right);
      printf(")");
      break;
  }
}

static void emit_header(char *appname) {
  printf("// File generated by ArduinoML (C/yacc/lex) for %s\n"
         "long time = 0;\n"
         "long debounce = 200;\n\n",
         appname);
}

static void emit_bricks(Brick *lst) {
  // Produce variables
  for (Brick *p = lst; p; p = p->next) {
    printf("int %s = %d;\n", p->var, p->port_number);
  }

  // Produce setup() function
  printf("\nvoid setup() {\n");
  for (Brick *p = lst; p; p = p->next) {
    printf("  pinMode(%s, %s);\n", p->var,
           (p->kind == sensor) ? "INPUT" : "OUTPUT");
  }
  printf("}\n\n");
}

static void emit_actions(Action *list) {
  for (Action *p = list; p; p = p->next)
    printf("  digitalWrite(%s, %s);\n", p->var_name,
           p->sig_value ? "HIGH" : "LOW");
}

static void emit_transition(char *current_state, Transition *transition) {
  printf("  boolean guard = millis() - time > debounce;\n");
  printf("  if (");
  emit_expression(transition->expression);
  printf(" && guard) {\n");
  printf("    time = millis();\n");
  printf("    state_%s();\n", transition->newstate);
  printf("  } else {\n");
  printf("    state_%s();\n", current_state);
  printf("  }\n");
}

static void emit_states(State *list) {
  for (State *p = list; p; p = p->next) {
    printf("void state_%s() {\n", p->name);
    emit_actions(p->actions);
    emit_transition(p->name, p->transition);
    printf("}\n\n");
  }
}

static void emit_loop(void) {
  printf("void loop() {\n  state_%s();\n}\n", initial_state->name);
}

/// emit the code for the parsed configuration
void emit_code(char *appname, Brick *brick_list, State *state_list) {
  check_states(brick_list, state_list);
  if (!initial_state)
    error_msg(yylineno, "no initial state declared");

  if (error_detected) {
    fprintf(stderr, "**** %d error%s\n", error_detected,
            (error_detected > 1) ? "s" : "");
    return;
  }

  // No error ⇒ produce code
  emit_header(appname);
  emit_bricks(brick_list);
  emit_states(state_list);
  emit_loop();
}

// ======================================================================
//                               U T I L S
// ======================================================================

/// Display error message using the GNU conventions
void error_msg(int lineno, const char *format, ...) {
  va_list ap;

  if (input_path)
    fprintf(stderr, "%s:", input_path);
  fprintf(stderr, "%d: ", lineno);
  va_start(ap, format);
  vfprintf(stderr, format, ap);
  va_end(ap);
  fprintf(stderr, "\n");

  error_detected += 1;
}

// Allocate memory and die if not possible
static void *__must_malloc(size_t sz, const char *func, const char *file,
                           int line) {
  void *res = malloc(sz);
  if (!res) {
    fprintf(stderr,
            "**** function '%s' cannot allocate memory (in file '%s':%d)\n",
            func, file, line);
    fprintf(stderr, "Abort.\n");
    exit(1);
  }
  return res;
}
