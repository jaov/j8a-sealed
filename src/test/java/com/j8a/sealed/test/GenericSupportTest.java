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
            "public interface Def {} // Not generic"
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
            "public interface Def<T> {} // Generic"
        );
        JavaFileObject a = JavaFileObjects.forSourceString("com.example.A",
            "package com.example; public final class A {} // Not generic"
        );

        assertAbout(javaSources())
            .that(Arrays.asList(def, a))
            .processedWith(new SealedProcessor())
            .failsToCompile()
            .withErrorContaining("base interface MUST NOT be generic");
    }
}
