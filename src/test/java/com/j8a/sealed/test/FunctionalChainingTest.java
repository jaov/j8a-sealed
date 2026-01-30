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

public class FunctionalChainingTest {

    @Test
    public void testFlatMapGeneration() throws IOException {
        JavaFileObject resultDef = JavaFileObjects.forSourceString("com.example.ResultDef",
            "package com.example;\n" +
            "import com.j8a.sealed.annotations.Sealed;\n" +
            "import com.j8a.sealed.annotations.Permits;\n" +
            "\n" +
            "@Sealed(name=\"Result\")\n" +
            "@Permits(classes={Success.class, Failure.class})\n" +
            "public interface ResultDef<T> {}"
        );

        JavaFileObject success = JavaFileObjects.forSourceString("com.example.Success",
            "package com.example;\n" +
            "public final class Success<T> {\n" +
            "    private final T value;\n" +
            "    public Success(T value) { this.value = value; }\n" +
            "    public T get() { return value; }\n" +
            "}"
        );

        JavaFileObject failure = JavaFileObjects.forSourceString("com.example.Failure",
            "package com.example;\n" +
            "public final class Failure {}"
        );

        Compilation compilation = javac()
            .withProcessors(new SealedProcessor())
            .compile(resultDef, success, failure);

        assertThat(compilation).succeeded();
        
        JavaFileObject resultFile = compilation.generatedSourceFile("com.example.Result").get();
        String content = resultFile.getCharContent(true).toString();
        
        assertTrue("Should contain flatMap method", content.contains("default <U> Result<U> flatMap(Function<? super T, Result<U>> mapper)"));
        assertTrue("Should contain map method using flatMap", content.contains("default <U> Result<U> map(Function<? super T, ? extends U> mapper)"));
        assertTrue("map should call flatMap", content.contains("return this.flatMap(val -> Result.wrap(new Success<>(mapper.apply(val))))"));
    }
}
