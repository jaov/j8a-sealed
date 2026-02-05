# API Reference

## `@Sealed`

Applied to the **Blueprint Interface**.

| Attribute | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `name` | `String` | **Required** | The name of the generated Root interface (e.g., "Shape"). |
| `mode` | `GenerationMode` | `BOTH` | Controls generated DSL styles. Options: `FUNCTION`, `CONSUMER`, `BOTH`. |

## `@Permits`

Applied to the **Blueprint Interface**.

| Attribute | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `classes` | `Class<?>[]` | **Required** | Array of allowed implementation classes. |
| `strict` | `boolean` | `true` | If `true`, permitted classes **must** be `final`. If `false`, allows non-final classes (triggers a warning). |

## Validation Rules

The annotation processor enforces the following rules at compile time:

1.  **Accessibility**: All permitted classes must be accessible to the generated code (public, or package-private in the same package).
2.  **Generics Support**:
    *   Up to **one** permitted class can be generic.
    *   If a permitted class is generic, the Blueprint Interface **must** be generic (with matching type parameters).
    *   If no permitted classes are generic, the Blueprint Interface **must not** be generic.
3.  **No Abstracts**: Permitted classes cannot be abstract.
4.  **Uniqueness**: Duplicate classes in `@Permits` are not allowed.
5.  **Finality**: If `strict=true` (default), all permitted classes must be `final`.
6.  **Method Delegation**: Every non-static, non-default method in the Blueprint Interface must have a corresponding **public** implementation in every permitted class.

## Method Delegation Validation

To ensure the rigor of the sealed hierarchy, the processor proactively validates that every permitted class satisfies the contract defined in the Blueprint Interface. If a requirement is not met, the processor provides "gentle guidance" via descriptive error messages:

*   **Missing Method**: If a method is completely missing, you are instructed to implement it.
*   **Access Modifier**: If a matching signature exists but is not `public`, the processor identifies the method and asks you to change its accessibility.
*   **Signature Mismatch (Near-Match)**: If methods with the same name exist but have different parameters, the processor lists the "near-matches" it found to help you identify the discrepancy.
*   **Return Type**: If the method exists but returns an incompatible type, a specific error is triggered.

## Functional Chaining Method Generation

When working with generics, the processor can auto-generate monadic methods on the Root interface:

*   **`map`**: `default <U> Root<U> map(Function<? super T, ? extends U> mapper)`
*   **`flatMap`**: `default <U> Root<U> flatMap(Function<? super T, Root<U>> mapper)`

**Requirements for generation:**
1.  There is exactly one generic permitted class (e.g., `Success<T>`).
2.  That class has a **public constructor** accepting a single argument of type `T`.
3.  That class has a **public accessor method** (e.g., `get()`, `value()`) that returns `T`.

If these conditions are not met, these methods will be skipped (with a compiler warning).

## Internal Architecture

### Boilerplate Reduction
The annotation processor minimizes generated code size by utilizing an internal `abstract static class Wrapper<V>` within the Root interface. This class handles the implementation of:
*   `equals(Object o)`
*   `hashCode()`
*   `toString()`

All generated leaf wrappers extend this base class, ensuring consistent object contracts without repetitive bytecode emission.

### IDE-Friendly Matchers
To ensure perfect auto-completion and type inference in IDEs, the generated Matcher DSL uses **strict typing** for input parameters while maintaining **Producer-Extends** covariance for outputs.

*   **Strict Input**: Handler parameters use exact types (e.g., `Function<Dog, ...>`). This avoids common pitfalls where IDEs fail to infer specific methods (like `dog.bark()`) when using `? super` wildcards.
*   **Covariant Output**: Handler return types use `? extends R`. This allows you to return a `String` where a `CharSequence` is expected, maintaining type safety and flexibility.

This design choice prioritizes the developer experience and IDE support, ensuring that pattern matching feels fluent and intuitive.
