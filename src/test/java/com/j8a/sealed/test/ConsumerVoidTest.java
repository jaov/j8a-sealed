package com.j8a.sealed.test;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.j8a.sealed.processor.SealedProcessor;
import org.junit.Test;
import java.io.IOException;
import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConsumerVoidTest {

    @Test
    public void testConsumerUsesVoid() throws IOException {
        JavaFileObject petDef = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "import com.j8a.sealed.annotations.GenerationMode;\n" +
            "\n" +
            "@Sealed(name=\"Pet\", mode=GenerationMode.CONSUMER)\n" +
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
        
        // Check for Visitor<Void> and return null
        assertTrue("Generated code should use Visitor<Void>", content.contains("root.accept(new Visitor<Void>()"));
        assertTrue("Generated onDog should return Void", content.contains("public Void onDog(Dog val)"));
        assertTrue("Generated onDog should return null", content.contains("return null;"));
        
        // Ensure it doesn't use Object
        assertFalse("Generated code should not use Visitor<Object> for consumers", content.contains("Visitor<Object>"));
    }

    @Test
    public void testGenericConsumerUsesVoid() throws IOException {
        JavaFileObject resultDef = JavaFileObjects.forSourceString("com.example.ResultDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "import com.j8a.sealed.annotations.GenerationMode;\n" +
            "\n" +
            "@Sealed(name=\"Result\", mode=GenerationMode.CONSUMER)\n" +
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

        // Check for Visitor<T, Void> and return null
        assertTrue("Generated code should use Visitor<T, Void>", content.contains("root.accept(new Visitor<T, Void>()"));
        assertTrue("Generated onSuccess should return Void", content.contains("public Void onSuccess(Success<T> val)"));
        assertTrue("Generated onSuccess should return null", content.contains("return null;"));
        
        // Ensure it doesn't use Object
        assertFalse("Generated code should not use Visitor<T, Object> for consumers", content.contains("Visitor<T, Object>"));
    }
}
