## Installation

mochafloats is published to `https://repo.redlance.org/public` under the group
`org.redlance.mochafloats`, with one artifact per module: `lexer`, `parser`, `runtime`
and `runtime-compiler`. It requires Java 24 or newer.

### Gradle

```kotlin
repositories {
    maven("https://repo.redlance.org/public")
}

dependencies {
    implementation("org.redlance.mochafloats:runtime:<version>")
}
```

### Maven

<!--@formatter:off-->
```xml
<repositories>
    <repository>
        <id>redlance</id>
        <url>https://repo.redlance.org/public</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.redlance.mochafloats</groupId>
        <artifactId>runtime</artifactId>
        <version>VERSION</version>
    </dependency>
</dependencies>
```
<!--@formatter:on-->

Use `runtime-compiler` instead of `runtime` to compile expressions to bytecode.
The `parser` module depends on `netty-buffer`, which Minecraft already provides.
