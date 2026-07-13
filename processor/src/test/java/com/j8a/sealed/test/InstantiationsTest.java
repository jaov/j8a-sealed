package com.j8a.sealed.test;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.j8a.sealed.processor.SealedProcessor;
import org.junit.Test;

import javax.tools.JavaFileObject;
import java.util.Arrays;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static java.nio.charset.StandardCharsets.UTF_8;

public class InstantiationsTest {

    @Test
    public void testMatcherBuilderInstantiation() {
        JavaFileObject petDef = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "import com.j8a.sealed.annotations.GenerationMode;\n" +
            "\n" +
            "@Sealed(name=\"Pet\", mode=GenerationMode.BOTH)\n" +
            "@Permits(classes={Dog.class})\n" +
            "public interface PetDef {\n" +
            "}"
        );

        JavaFileObject dog = JavaFileObjects.forSourceString("com.example.Dog",
            "package com.example;\n" +
            "public final class Dog {\n" +
            "}"
        );

        Compilation compilation = javac()
            .withProcessors(new SealedProcessor())
            .compile(petDef, dog);

        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("com.example.Pet")
            .contentsAsString(UTF_8)
            .contains("return new MatcherBuilder<>();");
    }

    @Test
    public void testConsumerMatcherBuilderInstantiation() {
        JavaFileObject petDef = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "import com.j8a.sealed.annotations.GenerationMode;\n" +
            "\n" +
            "@Sealed(name=\"Pet\", mode=GenerationMode.BOTH)\n" +
            "@Permits(classes={Dog.class})\n" +
            "public interface PetDef {\n" +
            "}"
        );

        JavaFileObject dog = JavaFileObjects.forSourceString("com.example.Dog",
            "package com.example;\n" +
            "public final class Dog {\n" +
            "}"
        );

        Compilation compilation = javac()
            .withProcessors(new SealedProcessor())
            .compile(petDef, dog);

        assertThat(compilation).succeeded();
        assertThat(compilation)
            .generatedSourceFile("com.example.Pet")
            .contentsAsString(UTF_8)
            .contains("return new ConsumerMatcherBuilder();");
    }
}
