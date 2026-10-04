# mochafloats

[![Maven](https://img.shields.io/maven-metadata/v?metadataUrl=https://repo.redlance.org/public/org/redlance/mochafloats/runtime/maven-metadata.xml&label=maven)](https://repo.redlance.org/#/public/org/redlance/mochafloats)
[![MIT License](https://img.shields.io/badge/license-MIT-blue)](license.txt)
[![Discord](https://img.shields.io/badge/discord-PlayerAnimationLibrary-5865F2)](https://discord.com/invite/PSW2t4Ujm6)

`mochafloats` is a Molang lexer, parser, interpreter and JVM bytecode compiler for Java 24+.
Molang is the small expression language Minecraft Bedrock uses for data-driven values; pretty much
everything in it evaluates to a number.

It is a fork of [Mocha](https://github.com/unnamed/mocha) by Unnamed Team, maintained by the
PlayerAnimationLibrary team. It computes with `float` instead of `double`, is split into modules,
uses the JDK's ClassFile API instead of Javassist, can send parsed expressions over the network,
and fixes many of Mocha's bugs.

```java
MolangInterpreter<?> molang = MolangInterpreter.standard();
float result = molang.eval("math.sqrt(3 * 3 + 4 * 4)"); // 5.0
```

The documentation, with installation, usage and the differences from Mocha, is at
[docs.zigythebird.com/mochafloats](https://docs.zigythebird.com/mochafloats/intro).
The [`docs`](docs) folder has a short version.
