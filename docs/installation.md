# Installation

## Maven Configuration

Add the project as a dependency. Since this is a compile-time annotation processor, you usually don't need special runtime dependencies other than the library itself.

```xml
<dependencies>
    <dependency>
        <groupId>com.j8a</groupId>
        <artifactId>j8a-sealed</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

## Compiler Configuration

Ensure your `maven-compiler-plugin` is configured to use Java 8 (source and target). The annotation processor runs automatically with the standard `javac` process.

```xml
<build>
    <plugins>
        <plugin>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.8.1</version>
            <configuration>
                <source>1.8</source>
                <target>1.8</target>
            </configuration>
        </plugin>
    </plugins>
</build>
```
