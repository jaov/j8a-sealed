package com.j8a.sealed.test;

import com.google.testing.compile.JavaFileObjects;
import com.j8a.sealed.processor.SealedProcessor;
import org.junit.Test;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

public class GenericSupportTest {
    
    @Test
    public void testMapGeneration() {
        JavaFileObject resultDef = JavaFileObjects.forSourceString("com.example.ResultDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "\n" +
            "@Sealed(name=\"Result\")\n" +
            "@Permits(classes={Success.class, Failure.class})\n" +
            "public interface ResultDef<T> {\n" +
            "    String message();\n" +
            "}"
        );

        JavaFileObject success = JavaFileObjects.forSourceString("com.example.Success",
            "package com.example;\n" +
            "public final class Success<T> {\n" +
            "    private final T value;\n" +
            "    public Success(T value) { this.value = value; }\n" +
            "    public T get() { return value; }\n" +
            "    public String message() { return null; }\n" +
            "}"
        );

        JavaFileObject failure = JavaFileObjects.forSourceString("com.example.Failure",
            "package com.example;\n" +
            "public final class Failure {\n" +
            "    private final String message;\n" +
            "    public Failure(String message) { this.message = message; }\n" +
            "    public String message() { return message; }\n" +
            "}"
        );

        assertAbout(javaSources())
            .that(Arrays.asList(resultDef, success, failure))
            .processedWith(new SealedProcessor())
            .compilesWithoutError();
    }

    @Test
    public void testMapGenerationSkippedWithWarning() {
        JavaFileObject resultDef = JavaFileObjects.forSourceString("com.example.SkippedDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "\n" +
            "@Sealed(name=\"SkippedResult\")\n" +
            "@Permits(classes={WeirdSuccess.class})\n" +
            "public interface SkippedDef<T> {\n" +
            "    T get();\n" +
            "}"
        );

        JavaFileObject weirdSuccess = JavaFileObjects.forSourceString("com.example.WeirdSuccess",
            "package com.example;\n" +
            "public final class WeirdSuccess<T> {\n" +
            "    // No public constructor with T\n" +
            "    public WeirdSuccess() {}\n" +
            "    public T get() { return null; }\n" +
            "}"
        );

        assertAbout(javaSources())
            .that(Arrays.asList(resultDef, weirdSuccess))
            .processedWith(new SealedProcessor())
            .compilesWithoutError()
            .withWarningContaining("Could not generate 'map'/'flatMap' methods");
    }

    @Test
    public void testSingleGenericPermittedClass_Valid() {
         JavaFileObject resultDef = JavaFileObjects.forSourceString("com.example.ResultDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "\n" +
            "@Sealed(name=\"Result\")\n" +
            "@Permits(classes={Success.class, Failure.class})\n" +
            "public interface ResultDef<T> {\n" +
            "    void perform();\n" +
            "}"
        );

        JavaFileObject success = JavaFileObjects.forSourceString("com.example.Success",
            "package com.example;\n" +
            "public final class Success<T> {\n" +
            "    private final T value;\n" +
            "    public Success(T value) { this.value = value; }\n" +
            "    public void perform() { }\n" +
            "}"
        );

        JavaFileObject failure = JavaFileObjects.forSourceString("com.example.Failure",
            "package com.example;\n" +
            "public final class Failure {\n" +
            "    public void perform() { }\n" +
            "}"
        );

        assertAbout(javaSources())
            .that(Arrays.asList(resultDef, success, failure))
            .processedWith(new SealedProcessor())
            .compilesWithoutError();
    }

    @Test
    public void testMultipleGenericPermittedClasses_Invalid() {
        JavaFileObject def = JavaFileObjects.forSourceString("com.example.Def",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "@Sealed(name=\"Res\") @Permits(classes={A.class, B.class})\n" +
            "public interface Def<T> {}"
        );
        JavaFileObject a = JavaFileObjects.forSourceString("com.example.A",
            "package com.example; public final class A<T> {}"
        );
        JavaFileObject b = JavaFileObjects.forSourceString("com.example.B",
            "package com.example; public final class B<T> {}"
        );

        assertAbout(javaSources())
            .that(Arrays.asList(def, a, b))
            .processedWith(new SealedProcessor())
            .failsToCompile()
            .withErrorContaining("Up to one permitted class can be generic");
    }

    @Test
    public void testGenericPermittedButNonGenericBase_Invalid() {
         JavaFileObject def = JavaFileObjects.forSourceString("com.example.Def",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "@Sealed(name=\"Res\") @Permits(classes={A.class})\n" +
            "public interface Def {}"
        );
        JavaFileObject a = JavaFileObjects.forSourceString("com.example.A",
            "package com.example; public final class A<T> {}"
        );

        assertAbout(javaSources())
            .that(Arrays.asList(def, a))
            .processedWith(new SealedProcessor())
            .failsToCompile()
            .withErrorContaining("base interface MUST be generic");
    }

    @Test
    public void testNonGenericPermittedButGenericBase_Invalid() {
         JavaFileObject def = JavaFileObjects.forSourceString("com.example.Def",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "@Sealed(name=\"Res\") @Permits(classes={A.class})\n" +
            "public interface Def<T> {}"
        );
        JavaFileObject a = JavaFileObjects.forSourceString("com.example.A",
            "package com.example; public final class A {}"
        );

        assertAbout(javaSources())
            .that(Arrays.asList(def, a))
            .processedWith(new SealedProcessor())
            .failsToCompile()
            .withErrorContaining("base interface MUST NOT be generic");
    }
}
