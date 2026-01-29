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

## Map Method Generation

When working with generics, the processor can auto-generate a monadic `map` method (`default <U> Root<U> map(Function<? super T, ? extends U> mapper)`) on the Root interface.

**Requirements for generation:**
1.  There is exactly one generic permitted class (e.g., `Success<T>`).
2.  That class has a **public constructor** accepting a single argument of type `T`.
3.  That class has a **public accessor method** (e.g., `get()`, `value()`) that returns `T`.

If these conditions are not met, the `map` method will be skipped (with a compiler warning).
