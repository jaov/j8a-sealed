# Java 8 Sealed Classes & Pattern Matching Simulation

This library provides a compile-time simulation of Java 17/21 Sealed Classes and Pattern Matching, fully compatible with Java 8. It utilizes standard Java Annotation Processing to generate type-safe wrappers and exhaustive pattern matching DSLs.

## Core Features

*   **Java 8 Compatible**: Brings modern Java features to legacy codebases.
*   **Non-Invasive**: Your domain model classes (the "leaves") remain pure POJOs. They do not require any annotations.
*   **Exhaustive by Design**: The generated Pattern Matching DSL enforces handling of all permitted subclasses at compile-time.
*   **Type-Safe**: Uses Java's type system to ensure correctness.
*   **Double Dispatch**: Implements the Visitor pattern under the hood for efficient dispatch.

## How It Works

1.  **Blueprint**: You define an interface annotated with `@Sealed` and `@Permits`.
2.  **Generation**: The annotation processor generates a "Root" interface, a Visitor, and Wrapper classes.
3.  **Usage**: You use the generated static factories (`wrap`) and DSL (`match`, `returning`) to interact with your data.

## Next Steps

*   [Installation](installation.md)
*   [Usage Guide](usage.md)
*   [API Reference](api_reference.md)
