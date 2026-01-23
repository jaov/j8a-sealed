package com.j8a.sealed.processor;

import com.google.auto.service.AutoService;
import com.j8a.sealed.annotations.GenerationMode;
import com.j8a.sealed.annotations.Permits;
import com.j8a.sealed.annotations.Sealed;
import com.squareup.javapoet.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@AutoService(Processor.class)
public class SealedProcessor extends AbstractProcessor {

    private Types typeUtils;
    private Elements elementUtils;
    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        typeUtils = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new LinkedHashSet<>();
        annotations.add(Sealed.class.getCanonicalName());
        annotations.add(Permits.class.getCanonicalName());
        return annotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Sealed.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                error(element, "@Sealed can only be applied to interfaces.");
                continue;
            }

            TypeElement blueprintInterface = (TypeElement) element;
            try {
                processSealedInterface(blueprintInterface);
            } catch (Exception e) {
                error(blueprintInterface, "Error processing @Sealed interface: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return true;
    }

    private void processSealedInterface(TypeElement blueprintInterface) throws IOException {
        Sealed sealedAnnotation = blueprintInterface.getAnnotation(Sealed.class);
        Permits permitsAnnotation = blueprintInterface.getAnnotation(Permits.class);

        if (permitsAnnotation == null) {
            error(blueprintInterface, "A @Sealed interface must also be annotated with @Permits.");
            return;
        }

        String rootInterfaceName = sealedAnnotation.name();
        GenerationMode mode = sealedAnnotation.mode();
        boolean strict = permitsAnnotation.strict();

        List<TypeMirror> permittedTypes = getPermittedTypes(permitsAnnotation);
        List<TypeElement> permittedClasses = new ArrayList<>();

        for (TypeMirror typeMirror : permittedTypes) {
            Element typeElement = typeUtils.asElement(typeMirror);
            if (!(typeElement instanceof TypeElement)) {
                error(blueprintInterface, "Permitted type is not a class/interface: " + typeMirror);
                return;
            }
            permittedClasses.add((TypeElement) typeElement);
        }

        // Validation
        if (!validatePermittedClasses(blueprintInterface, permittedClasses, strict)) {
            return;
        }

        generateRootInterface(blueprintInterface, rootInterfaceName, permittedClasses, mode);
    }

    private List<TypeMirror> getPermittedTypes(Permits permits) {
        try {
            permits.classes(); // This will throw MirroredTypesException
        } catch (MirroredTypesException mte) {
            return (List<TypeMirror>) mte.getTypeMirrors();
        }
        return Collections.emptyList();
    }

    private boolean validatePermittedClasses(TypeElement blueprint, List<TypeElement> permittedClasses, boolean strict) {
        boolean valid = true;
        Set<String> simpleNames = new HashSet<>();

        for (TypeElement permitted : permittedClasses) {
            // Accessibility check
            Set<Modifier> modifiers = permitted.getModifiers();
            if (modifiers.contains(Modifier.PRIVATE)) {
                 error(permitted, "Permitted class '" + permitted.getSimpleName() + "' must be accessible (cannot be private).");
                 valid = false;
            } else {
                 boolean isPublic = modifiers.contains(Modifier.PUBLIC);
                 boolean samePackage = elementUtils.getPackageOf(permitted).equals(elementUtils.getPackageOf(blueprint));
                 
                 if (!isPublic && !samePackage) {
                     error(permitted, "Permitted class '" + permitted.getSimpleName() + "' is not public and must be in the same package as the blueprint to be accessible.");
                     valid = false;
                 }
            }

            // Uniqueness check (simple name collision)
            if (!simpleNames.add(permitted.getSimpleName().toString())) {
                error(blueprint, "Duplicate simple name detected in permitted classes: " + permitted.getSimpleName() + ". Rename one or use aliases (not supported yet).");
                valid = false;
            }
            
            // Generics check
            if (!permitted.getTypeParameters().isEmpty()) {
                error(permitted, "Permitted classes must not have type parameters (generics).");
                valid = false;
            }

            // Abstract check
            if (permitted.getModifiers().contains(Modifier.ABSTRACT)) {
                 error(permitted, "Permitted classes cannot be abstract.");
                 valid = false;
            }

            // Finality check (Strict mode)
            if (strict && !permitted.getModifiers().contains(Modifier.FINAL)) {
                error(permitted, "Strict mode is enabled: Permitted class '" + permitted.getSimpleName() + "' must be final.");
                valid = false;
            } else if (!strict && !permitted.getModifiers().contains(Modifier.FINAL)) {
                warning(permitted, "Strict mode is disabled, but it is recommended to make '" + permitted.getSimpleName() + "' final.");
            }
        }
        return valid;
    }

    private void generateRootInterface(TypeElement blueprint, String rootName, List<TypeElement> permittedClasses, GenerationMode mode) throws IOException {
        String packageName = elementUtils.getPackageOf(blueprint).getQualifiedName().toString();
        ClassName rootClassName = ClassName.get(packageName, rootName);
        ClassName blueprintClassName = ClassName.get(blueprint);

        // Sort permitted classes alphabetically
        permittedClasses.sort(Comparator.comparing(e -> e.getSimpleName().toString()));

        TypeSpec.Builder rootBuilder = TypeSpec.interfaceBuilder(rootName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(blueprintClassName);

        // 1. Generate Visitor Interface
        ClassName visitorClassName = rootClassName.nestedClass("Visitor");
        TypeSpec visitorInterface = generateVisitorInterface(visitorClassName, permittedClasses);
        rootBuilder.addType(visitorInterface);

        // 2. Generate accept(Visitor) method in Root
        TypeVariableName rType = TypeVariableName.get("R");
        MethodSpec acceptMethod = MethodSpec.methodBuilder("accept")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addTypeVariable(rType)
                .returns(rType)
                .addParameter(ParameterizedTypeName.get(visitorClassName, rType), "visitor")
                .build();
        rootBuilder.addMethod(acceptMethod);

        // 3. Generate Wrapper classes
        for (TypeElement permitted : permittedClasses) {
            rootBuilder.addType(generateWrapperClass(permitted, rootClassName, visitorClassName, blueprint));
        }

        // 4. Generate wrap() methods (Factories)
        for (TypeElement permitted : permittedClasses) {
            rootBuilder.addMethod(generateWrapMethod(permitted, rootClassName));
        }

        // 5. Generate DSL Entry Points and Interfaces
        if (mode == GenerationMode.FUNCTION || mode == GenerationMode.BOTH) {
            generateFunctionDSL(rootBuilder, rootClassName, permittedClasses);
        }
        if (mode == GenerationMode.CONSUMER || mode == GenerationMode.BOTH) {
            generateConsumerDSL(rootBuilder, rootClassName, permittedClasses);
        }

        JavaFile.builder(packageName, rootBuilder.build())
                .skipJavaLangImports(true)
                .build()
                .writeTo(filer);
    }

    private TypeSpec generateVisitorInterface(ClassName visitorClassName, List<TypeElement> permittedClasses) {
        TypeVariableName rType = TypeVariableName.get("R");
        TypeSpec.Builder visitorBuilder = TypeSpec.interfaceBuilder(visitorClassName.simpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addTypeVariable(rType);

        for (TypeElement permitted : permittedClasses) {
            visitorBuilder.addMethod(MethodSpec.methodBuilder("on" + permitted.getSimpleName())
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(rType)
                    .addParameter(TypeName.get(permitted.asType()), String.valueOf(Character.toLowerCase(permitted.getSimpleName().charAt(0))) + permitted.getSimpleName().toString().substring(1))
                    .build());
        }
        return visitorBuilder.build();
    }

    private TypeSpec generateWrapperClass(TypeElement permitted, ClassName rootClassName, ClassName visitorClassName, TypeElement blueprint) {
        String wrapperName = permitted.getSimpleName() + "Wrapper";
        TypeName permittedType = TypeName.get(permitted.asType());

        TypeSpec.Builder wrapperBuilder = TypeSpec.classBuilder(wrapperName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addSuperinterface(rootClassName);

        // Field
        wrapperBuilder.addField(permittedType, "value", Modifier.PRIVATE, Modifier.FINAL);

        // Constructor
        wrapperBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(permittedType, "value")
                .addStatement("this.value = value")
                .build());

        // accept implementation
        TypeVariableName rType = TypeVariableName.get("R");
        wrapperBuilder.addMethod(MethodSpec.methodBuilder("accept")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addTypeVariable(rType)
                .returns(rType)
                .addParameter(ParameterizedTypeName.get(visitorClassName, rType), "visitor")
                .addStatement("return visitor.on$L(value)", permitted.getSimpleName())
                .build());

        // Delegate methods
        // 1. Methods from Blueprint
        List<ExecutableElement> blueprintMethods = ElementFilter.methodsIn(elementUtils.getAllMembers(blueprint));
        for (ExecutableElement method : blueprintMethods) {
            if (method.getModifiers().contains(Modifier.STATIC) || method.getModifiers().contains(Modifier.DEFAULT)) continue;
             if (method.getEnclosingElement().equals(elementUtils.getTypeElement("java.lang.Object"))) continue;
            
            // Generate delegation
            MethodSpec.Builder override = MethodSpec.overriding(method);
            StringBuilder args = new StringBuilder();
            for (VariableElement param : method.getParameters()) {
                if (args.length() > 0) args.append(", ");
                args.append(param.getSimpleName());
            }
            
            if (method.getReturnType().getKind() == javax.lang.model.type.TypeKind.VOID) {
                override.addStatement("value.$L($L)", method.getSimpleName(), args.toString());
            } else {
                override.addStatement("return value.$L($L)", method.getSimpleName(), args.toString());
            }
            wrapperBuilder.addMethod(override.build());
        }

        // 2. equals, hashCode, toString
        wrapperBuilder.addMethod(MethodSpec.methodBuilder("equals")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(boolean.class)
                .addParameter(Object.class, "o")
                .addStatement("if (this == o) return true")
                .addStatement("if (o == null || getClass() != o.getClass()) return false")
                .addStatement("$L that = ($L) o", wrapperName, wrapperName)
                .addStatement("return $T.equals(value, that.value)", Objects.class)
                .build());

        wrapperBuilder.addMethod(MethodSpec.methodBuilder("hashCode")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(int.class)
                .addStatement("return $T.hash(value)", Objects.class)
                .build());

        wrapperBuilder.addMethod(MethodSpec.methodBuilder("toString")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(String.class)
                .addStatement("return $T.toString(value)", Objects.class)
                .build());

        return wrapperBuilder.build();
    }

    private MethodSpec generateWrapMethod(TypeElement permitted, ClassName rootClassName) {
        return MethodSpec.methodBuilder("wrap")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(rootClassName)
                .addParameter(TypeName.get(permitted.asType()), "s")
                .addStatement("if (s == null) throw new $T(\"Source cannot be null\")", NullPointerException.class)
                .addStatement("return new $LWrapper(s)", permitted.getSimpleName())
                .build();
    }

    // --- Functional DSL Generation ---

    private void generateFunctionDSL(TypeSpec.Builder rootBuilder, ClassName rootClassName, List<TypeElement> permittedClasses) {
        // Entry point: returning(Class<R> resultType)
        TypeVariableName rType = TypeVariableName.get("R");
        ClassName firstStage = rootClassName.nestedClass("MatcherStage0");
        
        MethodSpec returning = MethodSpec.methodBuilder("returning")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addTypeVariable(rType)
                .returns(ParameterizedTypeName.get(firstStage, rType))
                .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), rType), "resultType")
                .addStatement("return new MatcherBuilder<>()")
                .build();
        rootBuilder.addMethod(returning);

        // Generate Interfaces for each stage
        for (int i = 0; i < permittedClasses.size(); i++) {
            TypeElement currentClass = permittedClasses.get(i);
            ClassName currentStage = rootClassName.nestedClass("MatcherStage" + i);
            ClassName nextStage = (i == permittedClasses.size() - 1) 
                    ? rootClassName.nestedClass("MatcherTerminal") 
                    : rootClassName.nestedClass("MatcherStage" + (i + 1));
            
            TypeSpec.Builder stageBuilder = TypeSpec.interfaceBuilder(currentStage.simpleName())
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addTypeVariable(rType);
            
            stageBuilder.addMethod(MethodSpec.methodBuilder("on" + currentClass.getSimpleName())
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(ParameterizedTypeName.get(nextStage, rType))
                    .addParameter(ParameterizedTypeName.get(ClassName.get(java.util.function.Function.class), TypeName.get(currentClass.asType()), rType), "func")
                    .build());
            
            rootBuilder.addType(stageBuilder.build());
        }

        // Terminal Interface
        ClassName terminalStage = rootClassName.nestedClass("MatcherTerminal");
        rootBuilder.addType(TypeSpec.interfaceBuilder(terminalStage.simpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addTypeVariable(rType)
                .addMethod(MethodSpec.methodBuilder("asFunction")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(ParameterizedTypeName.get(ClassName.get(java.util.function.Function.class), rootClassName, rType))
                        .build())
                .build());

        // Builder Implementation
        generateMatcherBuilder(rootBuilder, rootClassName, permittedClasses, true);
    }

    // --- Consumer DSL Generation ---

    private void generateConsumerDSL(TypeSpec.Builder rootBuilder, ClassName rootClassName, List<TypeElement> permittedClasses) {
        // Entry point: match()
        ClassName firstStage = rootClassName.nestedClass("ConsumerMatcherStage0");

        MethodSpec match = MethodSpec.methodBuilder("match")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(firstStage)
                .addStatement("return new ConsumerMatcherBuilder()")
                .build();
        rootBuilder.addMethod(match);

        // Generate Interfaces for each stage
        for (int i = 0; i < permittedClasses.size(); i++) {
            TypeElement currentClass = permittedClasses.get(i);
            ClassName currentStage = rootClassName.nestedClass("ConsumerMatcherStage" + i);
            ClassName nextStage = (i == permittedClasses.size() - 1)
                    ? rootClassName.nestedClass("ConsumerMatcherTerminal")
                    : rootClassName.nestedClass("ConsumerMatcherStage" + (i + 1));

            TypeSpec.Builder stageBuilder = TypeSpec.interfaceBuilder(currentStage.simpleName())
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

            stageBuilder.addMethod(MethodSpec.methodBuilder("on" + currentClass.getSimpleName())
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(nextStage)
                    .addParameter(ParameterizedTypeName.get(ClassName.get(java.util.function.Consumer.class), TypeName.get(currentClass.asType())), "cons")
                    .build());

            rootBuilder.addType(stageBuilder.build());
        }

        // Terminal Interface
        ClassName terminalStage = rootClassName.nestedClass("ConsumerMatcherTerminal");
        rootBuilder.addType(TypeSpec.interfaceBuilder(terminalStage.simpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addMethod(MethodSpec.methodBuilder("asConsumer")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(ParameterizedTypeName.get(ClassName.get(java.util.function.Consumer.class), rootClassName))
                        .build())
                .build());

        // Builder Implementation
        generateMatcherBuilder(rootBuilder, rootClassName, permittedClasses, false);
    }

    private void generateMatcherBuilder(TypeSpec.Builder rootBuilder, ClassName rootClassName, List<TypeElement> permittedClasses, boolean isFunction) {
        String builderName = isFunction ? "MatcherBuilder" : "ConsumerMatcherBuilder";
        TypeVariableName rType = TypeVariableName.get("R");
        
        TypeSpec.Builder builder = TypeSpec.classBuilder(builderName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);

        if (isFunction) {
            builder.addTypeVariable(rType);
        }

        // Fields to store functions/consumers
        for (TypeElement permitted : permittedClasses) {
            TypeName type = isFunction 
                    ? ParameterizedTypeName.get(ClassName.get(java.util.function.Function.class), TypeName.get(permitted.asType()), rType)
                    : ParameterizedTypeName.get(ClassName.get(java.util.function.Consumer.class), TypeName.get(permitted.asType()));
            builder.addField(type, "on" + permitted.getSimpleName(), Modifier.PRIVATE);
        }

        // Implement interfaces
        for (int i = 0; i < permittedClasses.size(); i++) {
            String stageName = (isFunction ? "MatcherStage" : "ConsumerMatcherStage") + i;
            TypeName interfaceType = isFunction 
                    ? ParameterizedTypeName.get(rootClassName.nestedClass(stageName), rType) 
                    : rootClassName.nestedClass(stageName);
            builder.addSuperinterface(interfaceType);
            
            // Implement 'onX' method
            TypeElement permitted = permittedClasses.get(i);
            String nextStageName = (i == permittedClasses.size() - 1) 
                    ? (isFunction ? "MatcherTerminal" : "ConsumerMatcherTerminal") 
                    : (isFunction ? "MatcherStage" : "ConsumerMatcherStage") + (i + 1);
            
            TypeName returnType = isFunction
                    ? ParameterizedTypeName.get(rootClassName.nestedClass(nextStageName), rType)
                    : rootClassName.nestedClass(nextStageName);

            TypeName paramType = isFunction
                    ? ParameterizedTypeName.get(ClassName.get(java.util.function.Function.class), TypeName.get(permitted.asType()), rType)
                    : ParameterizedTypeName.get(ClassName.get(java.util.function.Consumer.class), TypeName.get(permitted.asType()));

            MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("on" + permitted.getSimpleName())
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .returns(returnType)
                    .addParameter(paramType, "func")
                    .addStatement("this.on$L = func", permitted.getSimpleName())
                    .addStatement("return this");
            builder.addMethod(methodBuilder.build());
        }

        // Implement Terminal Interface
        String terminalName = isFunction ? "MatcherTerminal" : "ConsumerMatcherTerminal";
        TypeName terminalType = isFunction 
                ? ParameterizedTypeName.get(rootClassName.nestedClass(terminalName), rType) 
                : rootClassName.nestedClass(terminalName);
        builder.addSuperinterface(terminalType);

        if (isFunction) {
            MethodSpec.Builder asFunc = MethodSpec.methodBuilder("asFunction")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .returns(ParameterizedTypeName.get(ClassName.get(java.util.function.Function.class), rootClassName, rType))
                    .addCode("return new $T<$T, $T>() {\n", java.util.function.Function.class, rootClassName, rType)
                    .addCode("    @Override\n")
                    .addCode("    public $T apply($T root) {\n", rType, rootClassName)
                    .addCode("        return root.accept(new Visitor<$T>() {\n", rType);
            
            for (TypeElement permitted : permittedClasses) {
                 asFunc.addCode("            @Override\n")
                       .addCode("            public $T on$L($T val) { return on$L.apply(val); }\n", 
                               rType, permitted.getSimpleName(), TypeName.get(permitted.asType()), permitted.getSimpleName());
            }
            
            asFunc.addCode("        });\n")
                  .addCode("    }\n")
                  .addCode("};\n");
            builder.addMethod(asFunc.build());
        } else {
            MethodSpec.Builder asCons = MethodSpec.methodBuilder("asConsumer")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .returns(ParameterizedTypeName.get(ClassName.get(java.util.function.Consumer.class), rootClassName))
                    .addCode("return new $T<$T>() {\n", java.util.function.Consumer.class, rootClassName)
                    .addCode("    @Override\n")
                    .addCode("    public void accept($T root) {\n", rootClassName)
                    .addCode("        root.accept(new Visitor<$T>() {\n", Object.class); // Visitor<Object> just to satisfy generics, return null
            
            for (TypeElement permitted : permittedClasses) {
                 asCons.addCode("            @Override\n")
                       .addCode("            public $T on$L($T val) { on$L.accept(val); return null; }\n", 
                               Object.class, permitted.getSimpleName(), TypeName.get(permitted.asType()), permitted.getSimpleName());
            }
            
            asCons.addCode("        });\n")
                  .addCode("    }\n")
                  .addCode("};\n");
            builder.addMethod(asCons.build());
        }

        rootBuilder.addType(builder.build());
    }
    
    private void error(Element e, String msg) {
        messager.printMessage(Diagnostic.Kind.ERROR, msg, e);
    }

    private void warning(Element e, String msg) {
        messager.printMessage(Diagnostic.Kind.WARNING, msg, e);
    }
}