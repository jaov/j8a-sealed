package com.j8a.sealed.test;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.j8a.sealed.processor.SealedProcessor;
import org.junit.Test;
import java.io.IOException;
import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

public class DelegationTest {

    @Test
    public void testMethodDelegationSuccess() throws IOException {
        JavaFileObject petDef = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "\n" +
            "@Sealed(name=\"Pet\")\n" +
            "@Permits(classes={Dog.class})\n" +
            "public interface PetDef {\n" +
            "    String sound();\n" +
            "}"
        );

        JavaFileObject dog = JavaFileObjects.forSourceString("com.example.Dog",
            "package com.example;\n" +
            "public final class Dog {\n" +
            "    public String sound() { return \"woof\"; }\n" +
            "}"
        );

        Compilation compilation = javac()
            .withProcessors(new SealedProcessor())
            .compile(petDef, dog);

        assertThat(compilation).succeeded();
    }

    @Test
    public void testMethodDelegationFailure() throws IOException {
        JavaFileObject petDef = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "\n" +
            "@Sealed(name=\"Pet\")\n" +
            "@Permits(classes={Dog.class})\n" +
            "public interface PetDef {\n" +
            "    String sound();\n" +
            "}"
        );

        JavaFileObject dog = JavaFileObjects.forSourceString("com.example.Dog",
            "package com.example;\n" +
            "public final class Dog {\n" +
            "    // Missing sound() method\n" +
            "}"
        );

        Compilation compilation = javac()
            .withProcessors(new SealedProcessor())
            .compile(petDef, dog);

        // This should fail with our helpful proactive error message
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Permitted class 'Dog' is missing method 'sound()' defined in @Sealed interface 'PetDef'");
        assertThat(compilation).hadErrorContaining("Please implement it with a matching signature.");
    }

    @Test
    public void testMethodDelegationNonPublicFailure() throws IOException {
        JavaFileObject petDef = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "\n" +
            "@Sealed(name=\"Pet\")\n" +
            "@Permits(classes={Dog.class})\n" +
            "public interface PetDef {\n" +
            "    String sound();\n" +
            "}"
        );

        JavaFileObject dog = JavaFileObjects.forSourceString("com.example.Dog",
            "package com.example;\n" +
            "public final class Dog {\n" +
            "    String sound() { return \"woof\"; } // Not public\n" +
            "}"
        );

        Compilation compilation = javac()
            .withProcessors(new SealedProcessor())
            .compile(petDef, dog);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("has a method 'sound()' matching @Sealed interface 'PetDef', but it must be PUBLIC");
    }

    @Test
    public void testMethodDelegationReturnTypeFailure() throws IOException {
        JavaFileObject petDef = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "\n" +
            "@Sealed(name=\"Pet\")\n" +
            "@Permits(classes={Dog.class})\n" +
            "public interface PetDef {\n" +
            "    String sound();\n" +
            "}"
        );

        JavaFileObject dog = JavaFileObjects.forSourceString("com.example.Dog",
            "package com.example;\n" +
            "public final class Dog {\n" +
            "    public int sound() { return 1; } // Incorrect return type\n" +
            "}"
        );

        Compilation compilation = javac()
            .withProcessors(new SealedProcessor())
            .compile(petDef, dog);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("implements 'sound()', but its return type is incompatible with @Sealed interface 'PetDef'");
    }

    @Test
    public void testMethodDelegationNearMatchFailure() throws IOException {
        JavaFileObject petDef = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "\n" +
            "@Sealed(name=\"Pet\")\n" +
            "@Permits(classes={Dog.class})\n" +
            "public interface PetDef {\n" +
            "    String sound(String volume);\n" +
            "}"
        );

        JavaFileObject dog = JavaFileObjects.forSourceString("com.example.Dog",
            "package com.example;\n" +
            "public final class Dog {\n" +
            "    public String sound() { return \"woof\"; } // Wrong parameters\n" +
            "    public String sound(int times) { return \"woof\"; } // Also wrong\n" +
            "}"
        );

        Compilation compilation = javac()
            .withProcessors(new SealedProcessor())
            .compile(petDef, dog);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("has methods with the same name as 'sound(java.lang.String)' in @Sealed interface 'PetDef', but the signatures do not match.");
        assertThat(compilation).hadErrorContaining("Found near matches: 'sound()', 'sound(int)'");
    }
}