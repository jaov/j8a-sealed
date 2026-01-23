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
2.  **No Generics**: Permitted classes cannot have type parameters.
3.  **No Abstracts**: Permitted classes cannot be abstract.
4.  **Uniqueness**: Duplicate classes in `@Permits` are not allowed.
5.  **Finality**: If `strict=true` (default), all permitted classes must be `final`.
