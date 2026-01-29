# Usage Guide

## 1. Define the Blueprint

Create an interface that represents the root of your sealed hierarchy. Annotate it with `@Sealed` and `@Permits`.

```java
package com.example.shapes;

import com.j8a.sealed.annotations.Sealed;
import com.j8a.sealed.annotations.Permits;

@Sealed(name = "Shape") // The name of the generated Root interface
@Permits(classes = {Circle.class, Rectangle.class})
public interface ShapeDef {
    // Shared methods can be defined here
    double area();
}
```

## 2. Define Permitted Classes (Leaves)

Define the implementation classes. These are standard Java classes.

**Requirements:**
*   They must be **accessible** to the Blueprint (same package if package-private, or public).
*   By default (`strict=true`), they must be `final`.
*   They do not need any specific inheritance or annotations.

```java
package com.example.shapes;

public final class Circle {
    private final double radius;

    public Circle(double radius) {
        this.radius = radius;
    }

    public double area() {
        return Math.PI * radius * radius;
    }
}

public final class Rectangle {
    private final double width;
    private final double height;

    public Rectangle(double width, double height) {
        this.width = width;
        this.height = height;
    }

    public double area() {
        return width * height;
    }
}
```

## 3. Compile

Run `mvn compile`. The processor will generate a `Shape` interface in the same package as `ShapeDef`.

## 4. Use the Generated API

### Wrapping Objects

To participate in the sealed hierarchy, wrap your instances:

```java
Circle c = new Circle(5.0);
Shape shape = Shape.wrap(c);
```

### Pattern Matching (Functional Style)

Use `returning(Class<R>)` to map the shape to a value. The compiler enforces that you handle both `Circle` and `Rectangle`.

```java
String description = Shape.returning(String.class)
    .onCircle(circle -> "A circle with area: " + circle.area())
    .onRectangle(rect -> "A rectangle with area: " + rect.area())
    .asFunction()
    .apply(shape);

System.out.println(description);
```

### Pattern Matching (Consumer Style)

Use `match()` to perform side-effects.

```java
Shape.match()
    .onCircle(circle -> System.out.println("Processing circle..."))
    .onRectangle(rect -> System.out.println("Processing rectangle..."))
    .asConsumer()
    .accept(shape);
```

## 5. Accessing Blueprint Methods

The generated wrappers delegate methods defined in the Blueprint (`ShapeDef`) to the underlying object.

```java
// Since 'area()' was defined in ShapeDef and implemented in Circle/Rectangle
double a = shape.area(); 
```

## 6. Generic Support (e.g., Result<T>)

The library supports sealed hierarchies with generics, useful for types like `Result<T>` or `Option<T>`.

**Rules:**
*   Only **one** permitted class can be generic (e.g., `Success<T>`).
*   The base interface must be generic (e.g., `ResultDef<T>`).

### Example: Result<T>

```java
@Sealed(name = "Result")
@Permits(classes = {Success.class, Failure.class})
public interface ResultDef<T> {
    // Optional common methods
}

// Generic permitted class
public final class Success<T> {
    private final T value;
    public Success(T value) { this.value = value; }
    public T get() { return value; }
}

// Non-generic permitted class
public final class Failure {
    private final String message;
    public Failure(String message) { this.message = message; }
    public String message() { return message; }
}
```

### Using Generics

The generated `Result<T>` interface will handle type propagation.

```java
Result<String> success = Result.wrap(new Success<>("Hello"));
Result<String> failure = Result.wrap(new Failure("Error"));

// Monadic Map (Auto-generated)
// Transform Result<String> -> Result<Integer>
Result<Integer> length = success.map(String::length); 

// Pattern Matching
Result.returning(String.class)
    .onFailure(f -> "Failed: " + f.message())
    .onSuccess(s -> "Succeeded: " + s.get())
    .asFunction()
    .apply(success);
```

**Note on `map`:** The `map` method is auto-generated only if the generic permitted class has a compatible constructor (1 arg) and accessor method.