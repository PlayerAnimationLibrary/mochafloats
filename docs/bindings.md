## Bindings

### Functions

The simplest binding is a `MutableObjectBinding` holding functions; the interpreter calls
them directly.

<!--@formatter:off-->
```java
MolangInterpreter<?> molang = MolangInterpreter.standard();

MutableObjectBinding query = new MutableObjectBinding();
query.set("get_age", (Function<?>) (ctx, args) -> NumberValue.of(18));
molang.scope().set("query", query);

molang.eval("query.get_age()"); // evaluates to 18
```
<!--@formatter:on-->

### Java methods

Annotate a class and its static members with `@Binding` and bind the class. Compiled code
calls these methods directly; the interpreter calls them through a method handle.

<!--@formatter:off-->
```java
@Binding("random")
public class RandomBinding {
    // random.select(a, b)
    @Binding("select")
    public static float select(float a, float b) {
        return Math.random() < 0.5 ? a : b;
    }
}

// ...
MochaEngine<?> engine = MochaEngine.createStandard();

engine.interpreter().bind(RandomBinding.class);

engine.compiler().compile("random.select(1, 2)").evaluate(); // evaluates to either 1 or 2
// generates the following code:
//     return RandomBinding.select(1, 2);
```
<!--@formatter:on-->

`bindInstance(type, instance, name)` binds the non-static members of an object instead.
