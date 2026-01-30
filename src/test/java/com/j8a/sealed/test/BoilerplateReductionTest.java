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

public class BoilerplateReductionTest {

    @Test
    public void testWrapperGeneration() throws IOException {
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
        
        JavaFileObject petFile = compilation.generatedSourceFile("com.example.Pet").get();
        String content = petFile.getCharContent(true).toString();
        
        assertTrue("Content should contain Wrapper class definition", content.contains("abstract class Wrapper"));
        assertTrue("Content should contain value field", content.contains("protected final V value"));
        assertTrue("Content should contain equals method", content.contains("public boolean equals(Object o)"));
        assertTrue("Content should contain hashCode method", content.contains("public int hashCode()"));
        assertTrue("Content should contain toString method", content.contains("public String toString()"));
        assertTrue("DogWrapper should extend Wrapper", content.contains("final class DogWrapper extends Wrapper<Dog>"));
    }

    @Test
    public void testGenericWrapperGeneration() throws IOException {
        JavaFileObject resultDef = JavaFileObjects.forSourceString("com.example.ResultDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "\n" +
            "@Sealed(name=\"Result\")\n" +
            "@Permits(classes={Success.class})\n" +
            "public interface ResultDef<T> {}"
        );

        JavaFileObject success = JavaFileObjects.forSourceString("com.example.Success",
            "package com.example;\n" +
            "public final class Success<T> {}"
        );

        Compilation compilation = javac()
            .withProcessors(new SealedProcessor())
            .compile(resultDef, success);

        assertThat(compilation).succeeded();

        JavaFileObject resultFile = compilation.generatedSourceFile("com.example.Result").get();
        String content = resultFile.getCharContent(true).toString();

        assertTrue("Content should contain Wrapper class definition", content.contains("abstract class Wrapper<T, V> implements Result<T>"));
        assertTrue("SuccessWrapper should extend Wrapper", content.contains("final class SuccessWrapper<T> extends Wrapper<T, Success<T>>"));
    }
}