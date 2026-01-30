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
*   They must implement all methods defined in the Blueprint Interface with **public** visibility.
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

**Note on Flexibility:** Matchers use PECS (Producer-Extends, Consumer-Super). This means you can use general-purpose handlers:
```java
Function<Object, String> toStringHandler = Object::toString;

Shape.returning(String.class)
    .onCircle(toStringHandler)   // Valid: Circle IS-AN Object
    .onRectangle(toStringHandler)
    .asFunction();
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
public interface ResultDef<T> {}

public final class Success<T> {
    private final T value;
    public Success(T value) { this.value = value; }
    public T get() { return value; }
}

public final class Failure {
    private final String message;
    public Failure(String message) { this.message = message; }
    public String message() { return message; }
}
```

### Using Generics

The generated `Result<T>` interface will handle type propagation and supports functional chaining.

```java
Result<String> success = Result.wrap(new Success<>("Hello"));

// Monadic chaining (Auto-generated)
Result<Integer> length = success
    .map(String::trim)
    .flatMap(s -> s.isEmpty() ? Result.wrap(new Failure("Empty")) : Result.wrap(new Success<>(s.length())));
```

### Linear Pipelines (Railroad Oriented Programming)

With `flatMap`, complex business logic remains linear and purely declarative, automatically short-circuiting on failure states:

```java
// Assuming these methods exist
Result<String> callApi();
Result<User> validate(String rawApiBody);
Result<Integer> saveToDb(User userToSave); // returns id of created user.

public Result<Integer> process() {
    return callApi()                  // returns Result<String>
        .flatMap(this::validate)      // returns Result<User>
        .flatMap(this::saveToDb);     // returns Result<Integer>
}
```

**Note on `map`:** The `map` method is auto-generated only if the generic permitted class has a compatible constructor (1 arg) and accessor method.

So 

```java
// Not valid
final class InvalidConstructor<T> {
    private final T value();
    public T getValue(); // we do have an accessor
    public InvalidConstructor(T oneValue, Integer Another); // But the only constructor takes two parameters
}
```

or

```java
final class Success<T> {
    private final T value;

    // Valid constructor
    public Success<T> (T value);

} // class finished with no public access method for value
```

Will __not__ generate (flat)map
