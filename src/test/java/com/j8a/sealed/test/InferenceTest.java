package com.j8a.sealed.test;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.j8a.sealed.processor.SealedProcessor;
import org.junit.Test;
import java.io.IOException;
import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

public class InferenceTest {

    @Test
    public void testLambdaInferenceWithPECS() throws IOException {
        JavaFileObject petDef = JavaFileObjects.forSourceString("com.example.PetDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "\n" +
            "@Sealed(name=\"Pet\")\n" +
            "@Permits(classes={com.example.Dog.class, com.example.Cat.class})\n" +
            "public interface PetDef {}"
        );

        JavaFileObject dog = JavaFileObjects.forSourceString("com.example.Dog",
            "package com.example;\n" +
            "public final class Dog {\n" +
            "    public String bark() { return \"woof\"; }\n" +
            "}"
        );

        JavaFileObject cat = JavaFileObjects.forSourceString("com.example.Cat",
            "package com.example;\n" +
            "public final class Cat {\n" +
            "    public String meow() { return \"meow\"; }\n" +
            "}"
        );

        JavaFileObject usage = JavaFileObjects.forSourceString("com.example.Usage",
            "package com.example;\n" +
            "public class Usage {\n" +
            "    public void test() {\n" +
            "        Pet.returning(String.class)\n" +
            "           .onCat(c -> c.meow())\n" +
            "           .onDog(d -> d.bark())\n" +
            "           .asFunction();\n" +
            "           \n" +
            "        Pet.match()\n" +
            "           .onCat(c -> c.meow())\n" +
            "           .onDog(d -> d.bark())\n" +
            "           .asConsumer();\n" +
            "    }\n" +
            "}"
        );

        Compilation compilation = javac()
            .withProcessors(new SealedProcessor())
            .compile(petDef, dog, cat, usage);

        assertThat(compilation).succeeded();
    }
}