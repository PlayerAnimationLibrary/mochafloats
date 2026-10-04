## Getting Started

`mochafloats` is a Molang lexer, parser, interpreter and JVM bytecode compiler for Java 24+,
forked from [Mocha](https://github.com/unnamed/mocha). Molang is the expression language
Minecraft Bedrock uses for data-driven values; for the language itself, see
[learn.microsoft.com](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/molangreference/examples/molangconcepts/molangintroduction?view=minecraft-bedrock-stable).

The full documentation is at [docs.zigythebird.com/mochafloats](https://docs.zigythebird.com/mochafloats/intro).

### Modules

| Module             | Contains                                                                  |
|--------------------|---------------------------------------------------------------------------|
| `lexer`            | The tokenizer.                                                            |
| `parser`           | The parser, the syntax tree and its network format (Netty `ByteBuf`).     |
| `runtime`          | `MolangInterpreter`, scopes, values, bindings and the `math` library.     |
| `runtime-compiler` | `MolangCompiler`, which compiles expressions to bytecode, and `MochaEngine`. |

Each module depends on the one above it, so declare only the one you need. Most projects only need `runtime`.

### Benchmark

`./gradlew jmh` compares mochafloats with Moonflower's molang-compiler and bedrockk's MoLang.
The benchmark source is in [`src/jmh`](../src/jmh/java/team/unnamed/mocha/CompareBenchmark.java).
