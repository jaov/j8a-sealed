package com.j8a.sealed.test;

import com.google.testing.compile.JavaFileObjects;
import com.j8a.sealed.processor.SealedProcessor;
import org.junit.Test;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

public class ValidationTest {

    @Test
    public void testSealedWithoutPermits() {
        JavaFileObject def = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "@Sealed(name=\"Pet\")\n" +
            "public interface PetDef {}"
        );

        assertAbout(javaSources())
            .that(Arrays.asList(def))
            .processedWith(new SealedProcessor())
            .failsToCompile()
            .withErrorContaining("A @Sealed interface must also be annotated with @Permits.");
    }

    @Test
    public void testPermitsWithoutSealed() {
        JavaFileObject def = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "@Permits(classes={Dog.class})\n" +
            "public interface PetDef {}"
        );
        JavaFileObject dog = JavaFileObjects.forSourceString("com.example.Dog",
            "package com.example; public final class Dog {}"
        );

        assertAbout(javaSources())
            .that(Arrays.asList(def, dog))
            .processedWith(new SealedProcessor())
            .failsToCompile()
            .withErrorContaining("A @Permits annotation can only be used on an interface also annotated with @Sealed.");
    }

    @Test
    public void testDuplicatePermittedClasses() {
        JavaFileObject def = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "@Sealed(name=\"Pet\")\n" +
            "@Permits(classes={Dog.class, Dog.class})\n" +
            "public interface PetDef {}"
        );
        JavaFileObject dog = JavaFileObjects.forSourceString("com.example.Dog",
            "package com.example; public final class Dog {}"
        );

        assertAbout(javaSources())
            .that(Arrays.asList(def, dog))
            .processedWith(new SealedProcessor())
            .failsToCompile()
            .withErrorContaining("Duplicate class detected in @Permits: Dog");
    }
}
