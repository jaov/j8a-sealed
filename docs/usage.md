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
