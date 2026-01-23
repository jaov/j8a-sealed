package com.j8a. sealed.test;

import com.google.testing.compile.JavaFileObjects;
import com.j8a.sealed.processor.SealedProcessor;
import org.junit.Test;

import javax.tools.JavaFileObject;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import java.util.Arrays;

public class SealedProcessorTest {

    @Test
    public void testSimpleGeneration() {
        JavaFileObject petDef = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "import com.j8a.sealed.annotations.GenerationMode;\n" +
            "\n" +
            "@Sealed(name=\"Pet\", mode=GenerationMode.BOTH)\n" +
            "@Permits(classes={Dog.class, Cat.class})\n" +
            "public interface PetDef {\n" +
            "    String name();\n" +
            "}"
        );

        JavaFileObject dog = JavaFileObjects.forSourceString("com.example.Dog",
            "package com.example;\n" +
            "public final class Dog {\n" +
            "    private final String name;\n" +
            "    public Dog(String name) { this.name = name; }\n" +
            "    public String name() { return name; }\n" +
            "}"
        );

        JavaFileObject cat = JavaFileObjects.forSourceString("com.example.Cat",
            "package com.example;\n" +
            "public final class Cat {\n" +
            "    private final String name;\n" +
            "    public Cat(String name) { this.name = name; }\n" +
            "    public String name() { return name; }\n" +
            "}"
        );

        assertAbout(javaSources())
            .that(Arrays.asList(petDef, dog, cat))
            .withCompilerOptions("-Xlint:-processing")
            .processedWith(new SealedProcessor())
            .compilesWithoutError();
            // .andGeneratesSources(...) // We can check generated source content if needed, but compilation is a good start.
    }

    @Test
    public void testValidationNonFinalStrictMode() {
        JavaFileObject petDef = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "\n" +
            "@Sealed(name=\"Pet\")\n" +
            "@Permits(classes={Dog.class})\n" +
            "public interface PetDef {}"
        );

        JavaFileObject dog = JavaFileObjects.forSourceString("com.example.Dog",
            "package com.example;\n" +
            "public class Dog {}" // Not final
        );

        assertAbout(javaSources())
            .that(Arrays.asList(petDef, dog))
            .processedWith(new SealedProcessor())
            .failsToCompile()
            .withErrorContaining("Strict mode is enabled: Permitted class 'Dog' must be final");
    }

    @Test
    public void testDSLGeneration() {
        JavaFileObject petDef = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "import com.j8a.sealed.annotations.GenerationMode;\n" +
            "\n" +
            "@Sealed(name=\"Pet\", mode=GenerationMode.BOTH)\n" +
            "@Permits(classes={Dog.class, Cat.class})\n" +
            "public interface PetDef {\n" +
            "    String name();\n" +
            "}"
        );

        JavaFileObject dog = JavaFileObjects.forSourceString("com.example.Dog",
            "package com.example;\n" +
            "public final class Dog {\n" +
            "    public String name() { return \"dog\"; }\n" +
            "}"
        );

        JavaFileObject cat = JavaFileObjects.forSourceString("com.example.Cat",
            "package com.example;\n" +
            "public final class Cat {\n" +
            "    public String name() { return \"cat\"; }\n" +
            "}"
        );

        assertAbout(javaSources())
            .that(Arrays.asList(petDef, dog, cat))
            .withCompilerOptions("-Xlint:-processing")
            .processedWith(new SealedProcessor())
            .compilesWithoutError();
            // We rely on compilation to ensure the generated code is valid Java.
            // Deeper inspection would require inspecting the generated file content or loading the class.
    }
}
