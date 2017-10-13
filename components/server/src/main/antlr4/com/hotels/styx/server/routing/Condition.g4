grammar Condition;

expression:
    NOT expression                  # NotExpression
  | expression AND expression       # AndExpression
  | expression OR expression        # OrExpression
  | '(' expression ')'              # SubExpression
  | stringComparison                # StringCompareExpression
  ;

stringComparison:
    stringExpression '=~' string              # StringMatchesRegexp
  | stringExpression '==' stringExpression    # StringEqualsString
  | stringExpression                          # StringIsPresent
  ;

stringExpression:
    function
  | string
  ;

function:
    ID '(' arglist ')'
  | ID '(' arglist         { notifyErrorListeners("Missing closing parenthesis"); }
  | ID '(' arglist ')' ')' { notifyErrorListeners("Too many closing parentheses"); }
  ;

arglist:
  ( string (',' string)* )?
  ;

string: QUOTED_STRING | DQUOTED_STRING;

DQUOTED_STRING :   '"' ( ESCAPED_DQUOTE | ~('\n'|'\r') )*? '"';
fragment ESCAPED_DQUOTE : '\\"';

QUOTED_STRING :   '\'' ( ESCAPED_QUOTE | ~('\n'|'\r') )*? '\'';
fragment ESCAPED_QUOTE : '\\\'';

AND : 'AND' ;

OR : 'OR' ;

NOT : 'NOT' ;

SQUOTE : ['] ;

DQUOTE : ["] ;

ID : [a-zA-Z0-9_]+ ;

// TODO: should also accept any visible non-delimiting US-ASCII character
// REF: RFC7230, chapter 3.2.6
// TOKEN : [!#$%&'*+-.^_`|~a-zA-Z0-9]+ ;

WS : [ \t\r\n]+ -> skip ;
