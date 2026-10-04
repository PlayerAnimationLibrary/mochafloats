## Usage

### Evaluate

`MolangInterpreter.standard()` creates an interpreter with the standard `math`, `variable`
and `v` bindings. `eval` parses and evaluates Molang code and returns a `float`.

<!--@formatter:off-->
```java
MolangInterpreter<?> molang = MolangInterpreter.standard();

float result = molang.eval("math.sqrt(3 * 3 + 4 * 4)");
// evaluates to 5.0

float result2 = molang.eval("math.abs(-5) + 5");
// evaluates to 10.0
```
<!--@formatter:on-->

To evaluate the same code many times, parse it once and keep the expressions.

<!--@formatter:off-->
```java
List<Expression> expressions = MolangParser.parseAll("math.sqrt(3 * 3 + 4 * 4)");

molang.eval(expressions);
// evaluates to 5.0
```
<!--@formatter:on-->

`prepareEval(code)` does the same and returns a `Supplier<Float>`.

### Compile

`MochaEngine` (in `runtime-compiler`) holds an interpreter and a compiler that share one
scope. A compiled function runs without any interpretation overhead; constant parts are
evaluated while compiling.

<!--@formatter:off-->
```java
MochaEngine<?> engine = MochaEngine.createStandard();

MochaFunction function = engine.compiler().compile("math.sqrt(3 * 3 + 4 * 4)");
// compiles a class that implements MochaFunction and returns 5.0

function.evaluate();
// evaluates to 5.0
```
<!--@formatter:on-->

The compiled class can implement your own interface with a single method; its parameters
are available by name.

<!--@formatter:off-->
```java
interface CompareFunction extends MochaCompiledFunction {
    boolean compare(@Named("a") float a, @Named("b") float b);
}

// ...
CompareFunction gt = engine.compiler().compile("a > b", CompareFunction.class);

gt.compare(5, 4);
// true

gt.compare(4, 5);
// false
```
<!--@formatter:on-->

The compiler supports a subset of Molang: arithmetic, comparisons, logic, conditionals,
temp variables, interface parameters and Java methods and static fields bound with
`@Binding`. Evaluate anything else, such as `variable`, loops or lambda bindings, with the
interpreter.
