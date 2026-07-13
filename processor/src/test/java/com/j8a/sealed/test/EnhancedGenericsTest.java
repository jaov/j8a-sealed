package com.j8a.sealed.test;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.j8a.sealed.processor.SealedProcessor;
import org.junit.Test;
import java.io.IOException;
import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.Assert.assertTrue;

public class EnhancedGenericsTest {

    @Test
    public void testPECSInMatchers() throws IOException {
        JavaFileObject petDef = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "import com.j8a.sealed.annotations.GenerationMode;\n" +
            "\n" +
            "@Sealed(name=\"Pet\", mode=GenerationMode.BOTH)\n" +
            "@Permits(classes={Dog.class})\n" +
            "public interface PetDef {}"
        );

        JavaFileObject dog = JavaFileObjects.forSourceString("com.example.Dog",
            "package com.example;\n" +
            "public final class Dog {}"
        );

        Compilation compilation = javac()
            .withProcessors(new SealedProcessor())
            .compile(petDef, dog);

        assertThat(compilation).succeeded();
        
        JavaFileObject resultFile = compilation.generatedSourceFile("com.example.Pet").get();
        String content = resultFile.getCharContent(true).toString();
        
        assertTrue("Should use exact type for Function input", content.contains("onDog(Function<Dog, "));
        assertTrue("Should use ? extends for Function output", content.contains(", ? extends R> func)"));
        assertTrue("Should use exact type for Consumer input", content.contains("onDog(Consumer<Dog> cons)"));
    }

    @Test
    public void testMatcherCompatibility() {
        JavaFileObject petDef = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "import com.j8a.sealed.annotations.GenerationMode;\n" +
            "\n" +
            "@Sealed(name=\"Pet\", mode=GenerationMode.BOTH)\n" +
            "@Permits(classes={Dog.class})\n" +
            "public interface PetDef {}"
        );

        JavaFileObject dog = JavaFileObjects.forSourceString("com.example.Dog",
            "package com.example;\n" +
            "public final class Dog {}"
        );

        JavaFileObject usage = JavaFileObjects.forSourceString("com.example.Usage",
            "package com.example;\n" +
            "import java.util.function.Function;\n" +
            "public class Usage {\n" +
            "    public void test() {\n" +
            "        Function<Dog, String> func = o -> o.toString();\n" +
            "        // This should compile with exact match\n" +
            "        Pet.returning(String.class).onDog(func).asFunction();\n" +
            "    }\n" +
            "}"
        );

        Compilation compilation = javac()
            .withProcessors(new SealedProcessor())
            .compile(petDef, dog, usage);

        assertThat(compilation).succeeded();
    }
}
