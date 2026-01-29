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
        
        List<TypeElement> genericPermitted = permittedClasses.stream()
            .filter(pe -> !pe.getTypeParameters().isEmpty())
            .collect(Collectors.toList());

        boolean baseIsGeneric = !blueprint.getTypeParameters().isEmpty();

        if (genericPermitted.size() > 1) {
            error(blueprint, "Up to one permitted class can be generic. Found: " + genericPermitted.size());
            valid = false;
        }

        if (!genericPermitted.isEmpty()) {
            // Has 1 generic permitted class (or more, but error reported above)
            if (!baseIsGeneric) {
                error(blueprint, "The base interface MUST be generic because permitted class '" + genericPermitted.get(0).getSimpleName() + "' is generic. Either make the permitted class non-generic or add type parameters to the base interface.");
                valid = false;
            }
        } else {
            // 0 generic permitted classes
            if (baseIsGeneric) {
                error(blueprint, "The base interface MUST NOT be generic because no permitted classes are generic. Either remove type parameters from the base interface or make one permitted class generic.");
                valid = false;
            }
        }

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

        // Capture Type Variables from Blueprint
        List<TypeVariableName> typeVariables = new ArrayList<>();
        for (TypeParameterElement tpe : blueprint.getTypeParameters()) {
            typeVariables.add(TypeVariableName.get(tpe));
        }

        // Sort permitted classes alphabetically
        permittedClasses.sort(Comparator.comparing(e -> e.getSimpleName().toString()));

        TypeSpec.Builder rootBuilder = TypeSpec.interfaceBuilder(rootName)
                .addModifiers(Modifier.PUBLIC)
                .addTypeVariables(typeVariables);

        // Add superinterface with generics if applicable
        if (typeVariables.isEmpty()) {
            rootBuilder.addSuperinterface(ClassName.get(blueprint));
        } else {
             rootBuilder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(blueprint), typeVariables.toArray(new TypeName[0])));
        }

        // 1. Generate Visitor Interface
        ClassName visitorClassName = rootClassName.nestedClass("Visitor");
        TypeSpec visitorInterface = generateVisitorInterface(visitorClassName, permittedClasses, typeVariables);
        rootBuilder.addType(visitorInterface);

        // 2. Generate accept(Visitor) method in Root
        TypeVariableName rType = TypeVariableName.get("R");
        
        TypeName visitorType;
        if (typeVariables.isEmpty()) {
             visitorType = ParameterizedTypeName.get(visitorClassName, rType);
        } else {
             List<TypeName> visitorTypeArgs = new ArrayList<>(typeVariables);
             visitorTypeArgs.add(rType);
             visitorType = ParameterizedTypeName.get(visitorClassName, visitorTypeArgs.toArray(new TypeName[0]));
        }

        MethodSpec acceptMethod = MethodSpec.methodBuilder("accept")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addTypeVariable(rType)
                .returns(rType)
                .addParameter(visitorType, "visitor")
                .build();
        rootBuilder.addMethod(acceptMethod);

        // 3. Generate Wrapper classes
        for (TypeElement permitted : permittedClasses) {
            rootBuilder.addType(generateWrapperClass(permitted, rootClassName, visitorClassName, blueprint, typeVariables));
        }

        // 4. Generate wrap() methods (Factories)
        for (TypeElement permitted : permittedClasses) {
            rootBuilder.addMethod(generateWrapMethod(permitted, rootClassName, typeVariables));
        }

        // 5. Generate DSL Entry Points and Interfaces
        if (mode == GenerationMode.FUNCTION || mode == GenerationMode.BOTH) {
            generateFunctionDSL(rootBuilder, rootClassName, permittedClasses, typeVariables);
        }
        if (mode == GenerationMode.CONSUMER || mode == GenerationMode.BOTH) {
            generateConsumerDSL(rootBuilder, rootClassName, permittedClasses, typeVariables);
        }

        JavaFile.builder(packageName, rootBuilder.build())
                .skipJavaLangImports(true)
                .build()
                .writeTo(filer);
    }

    private TypeSpec generateVisitorInterface(ClassName visitorClassName, List<TypeElement> permittedClasses, List<TypeVariableName> rootTypeVars) {
        TypeVariableName rType = TypeVariableName.get("R");
        TypeSpec.Builder visitorBuilder = TypeSpec.interfaceBuilder(visitorClassName.simpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        
        visitorBuilder.addTypeVariables(rootTypeVars);
        visitorBuilder.addTypeVariable(rType);

        for (TypeElement permitted : permittedClasses) {
            TypeName paramType;
            if (!permitted.getTypeParameters().isEmpty()) {
                // If permitted is generic, it should match root's type variables (assumed based on validation)
                paramType = ParameterizedTypeName.get(ClassName.get(permitted), rootTypeVars.toArray(new TypeName[0]));
            } else {
                paramType = TypeName.get(permitted.asType());
            }

            visitorBuilder.addMethod(MethodSpec.methodBuilder("on" + permitted.getSimpleName())
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(rType)
                    .addParameter(paramType, String.valueOf(Character.toLowerCase(permitted.getSimpleName().charAt(0))) + permitted.getSimpleName().toString().substring(1))
                    .build());
        }
        return visitorBuilder.build();
    }

    private TypeSpec generateWrapperClass(TypeElement permitted, ClassName rootClassName, ClassName visitorClassName, TypeElement blueprint, List<TypeVariableName> rootTypeVars) {
        String wrapperName = permitted.getSimpleName() + "Wrapper";
        
        TypeName permittedType;
        if (!permitted.getTypeParameters().isEmpty()) {
             permittedType = ParameterizedTypeName.get(ClassName.get(permitted), rootTypeVars.toArray(new TypeName[0]));
        } else {
             permittedType = TypeName.get(permitted.asType());
        }

        TypeSpec.Builder wrapperBuilder = TypeSpec.classBuilder(wrapperName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);

        // Add Root Type Vars to Wrapper
        wrapperBuilder.addTypeVariables(rootTypeVars);

        // Implement Root Interface
        if (rootTypeVars.isEmpty()) {
            wrapperBuilder.addSuperinterface(rootClassName);
        } else {
            wrapperBuilder.addSuperinterface(ParameterizedTypeName.get(rootClassName, rootTypeVars.toArray(new TypeName[0])));
        }

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
        
        TypeName visitorType;
        if (rootTypeVars.isEmpty()) {
             visitorType = ParameterizedTypeName.get(visitorClassName, rType);
        } else {
             List<TypeName> visitorTypeArgs = new ArrayList<>(rootTypeVars);
             visitorTypeArgs.add(rType);
             visitorType = ParameterizedTypeName.get(visitorClassName, visitorTypeArgs.toArray(new TypeName[0]));
        }

        wrapperBuilder.addMethod(MethodSpec.methodBuilder("accept")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addTypeVariable(rType)
                .returns(rType)
                .addParameter(visitorType, "visitor")
                .addStatement("return visitor.on$L(value)", permitted.getSimpleName())
                .build());

        // Delegate methods
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

    private MethodSpec generateWrapMethod(TypeElement permitted, ClassName rootClassName, List<TypeVariableName> rootTypeVars) {
        TypeName returnType;
        if (rootTypeVars.isEmpty()) {
            returnType = rootClassName;
        } else {
            returnType = ParameterizedTypeName.get(rootClassName, rootTypeVars.toArray(new TypeName[0]));
        }

        MethodSpec.Builder builder = MethodSpec.methodBuilder("wrap")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addTypeVariables(rootTypeVars)
                .returns(returnType);

        TypeName paramType;
        if (!permitted.getTypeParameters().isEmpty()) {
            paramType = ParameterizedTypeName.get(ClassName.get(permitted), rootTypeVars.toArray(new TypeName[0]));
        } else {
            paramType = TypeName.get(permitted.asType());
        }

        builder.addParameter(paramType, "s");

        builder.addStatement("if (s == null) throw new $T(\"Source cannot be null\")", NullPointerException.class);
        
        if (rootTypeVars.isEmpty()) {
             builder.addStatement("return new $LWrapper(s)", permitted.getSimpleName());
        } else {
             // For generic wrapper, we infer the diamonds
             builder.addStatement("return new $LWrapper<>(s)", permitted.getSimpleName());
        }
        
        return builder.build();
    }

    // --- Functional DSL Generation ---

    private void generateFunctionDSL(TypeSpec.Builder rootBuilder, ClassName rootClassName, List<TypeElement> permittedClasses, List<TypeVariableName> rootTypeVars) {
        TypeVariableName rType = TypeVariableName.get("R");
        ClassName firstStage = rootClassName.nestedClass("MatcherStage0");
        
        TypeName returnType;
         if (rootTypeVars.isEmpty()) {
             returnType = ParameterizedTypeName.get(firstStage, rType);
        } else {
             List<TypeName> args = new ArrayList<>(rootTypeVars);
             args.add(rType);
             returnType = ParameterizedTypeName.get(firstStage, args.toArray(new TypeName[0]));
        }

        MethodSpec.Builder returningBuilder = MethodSpec.methodBuilder("returning")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addTypeVariables(rootTypeVars)
                .addTypeVariable(rType)
                .returns(returnType)
                .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), rType), "resultType");

        if (rootTypeVars.isEmpty()) {
            returningBuilder.addStatement("return new MatcherBuilder()");
        } else {
            returningBuilder.addStatement("return new MatcherBuilder<>()");
        }

        rootBuilder.addMethod(returningBuilder.build());

        // Generate Interfaces
        for (int i = 0; i < permittedClasses.size(); i++) {
            TypeElement currentClass = permittedClasses.get(i);
            ClassName currentStage = rootClassName.nestedClass("MatcherStage" + i);
            ClassName nextStage = (i == permittedClasses.size() - 1) 
                    ? rootClassName.nestedClass("MatcherTerminal") 
                    : rootClassName.nestedClass("MatcherStage" + (i + 1));
            
            TypeSpec.Builder stageBuilder = TypeSpec.interfaceBuilder(currentStage.simpleName())
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addTypeVariables(rootTypeVars)
                    .addTypeVariable(rType);
            
             TypeName nextStageType;
             if (rootTypeVars.isEmpty()) {
                 nextStageType = ParameterizedTypeName.get(nextStage, rType);
             } else {
                 List<TypeName> args = new ArrayList<>(rootTypeVars);
                 args.add(rType);
                 nextStageType = ParameterizedTypeName.get(nextStage, args.toArray(new TypeName[0]));
             }

            TypeName paramType;
            if (!currentClass.getTypeParameters().isEmpty()) {
                 paramType = ParameterizedTypeName.get(ClassName.get(currentClass), rootTypeVars.toArray(new TypeName[0]));
            } else {
                 paramType = TypeName.get(currentClass.asType());
            }

            stageBuilder.addMethod(MethodSpec.methodBuilder("on" + currentClass.getSimpleName())
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(nextStageType)
                    .addParameter(ParameterizedTypeName.get(ClassName.get(java.util.function.Function.class), paramType, rType), "func")
                    .build());
            
            rootBuilder.addType(stageBuilder.build());
        }

        // Terminal Interface
        ClassName terminalStage = rootClassName.nestedClass("MatcherTerminal");
        TypeSpec.Builder terminalBuilder = TypeSpec.interfaceBuilder(terminalStage.simpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addTypeVariables(rootTypeVars)
                .addTypeVariable(rType);
                
        TypeName rootType;
        if (rootTypeVars.isEmpty()) {
             rootType = rootClassName;
        } else {
             rootType = ParameterizedTypeName.get(rootClassName, rootTypeVars.toArray(new TypeName[0]));
        }

        terminalBuilder.addMethod(MethodSpec.methodBuilder("asFunction")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(ParameterizedTypeName.get(ClassName.get(java.util.function.Function.class), rootType, rType))
                        .build());
                        
        rootBuilder.addType(terminalBuilder.build());

        // Builder Implementation
        generateMatcherBuilder(rootBuilder, rootClassName, permittedClasses, true, rootTypeVars);
    }

    // --- Consumer DSL Generation ---

    private void generateConsumerDSL(TypeSpec.Builder rootBuilder, ClassName rootClassName, List<TypeElement> permittedClasses, List<TypeVariableName> rootTypeVars) {
        // Entry point: match()
        ClassName firstStage = rootClassName.nestedClass("ConsumerMatcherStage0");

        TypeName returnType;
        if (rootTypeVars.isEmpty()) {
             returnType = firstStage;
        } else {
             returnType = ParameterizedTypeName.get(firstStage, rootTypeVars.toArray(new TypeName[0]));
        }

        MethodSpec.Builder matchBuilder = MethodSpec.methodBuilder("match")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addTypeVariables(rootTypeVars)
                .returns(returnType);

        if (rootTypeVars.isEmpty()) {
            matchBuilder.addStatement("return new ConsumerMatcherBuilder()");
        } else {
            matchBuilder.addStatement("return new ConsumerMatcherBuilder<>()");
        }

        rootBuilder.addMethod(matchBuilder.build());

        // Generate Interfaces for each stage
        for (int i = 0; i < permittedClasses.size(); i++) {
            TypeElement currentClass = permittedClasses.get(i);
            ClassName currentStage = rootClassName.nestedClass("ConsumerMatcherStage" + i);
            ClassName nextStage = (i == permittedClasses.size() - 1)
                    ? rootClassName.nestedClass("ConsumerMatcherTerminal")
                    : rootClassName.nestedClass("ConsumerMatcherStage" + (i + 1));

            TypeSpec.Builder stageBuilder = TypeSpec.interfaceBuilder(currentStage.simpleName())
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addTypeVariables(rootTypeVars);

            TypeName nextStageType;
            if (rootTypeVars.isEmpty()) {
                nextStageType = nextStage;
            } else {
                nextStageType = ParameterizedTypeName.get(nextStage, rootTypeVars.toArray(new TypeName[0]));
            }

            TypeName paramType;
            if (!currentClass.getTypeParameters().isEmpty()) {
                 paramType = ParameterizedTypeName.get(ClassName.get(currentClass), rootTypeVars.toArray(new TypeName[0]));
            } else {
                 paramType = TypeName.get(currentClass.asType());
            }

            stageBuilder.addMethod(MethodSpec.methodBuilder("on" + currentClass.getSimpleName())
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(nextStageType)
                    .addParameter(ParameterizedTypeName.get(ClassName.get(java.util.function.Consumer.class), paramType), "cons")
                    .build());

            rootBuilder.addType(stageBuilder.build());
        }

        // Terminal Interface
        ClassName terminalStage = rootClassName.nestedClass("ConsumerMatcherTerminal");
        
        TypeName rootType;
        if (rootTypeVars.isEmpty()) {
             rootType = rootClassName;
        } else {
             rootType = ParameterizedTypeName.get(rootClassName, rootTypeVars.toArray(new TypeName[0]));
        }

        rootBuilder.addType(TypeSpec.interfaceBuilder(terminalStage.simpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addTypeVariables(rootTypeVars)
                .addMethod(MethodSpec.methodBuilder("asConsumer")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(ParameterizedTypeName.get(ClassName.get(java.util.function.Consumer.class), rootType))
                        .build())
                .build());

        // Builder Implementation
        generateMatcherBuilder(rootBuilder, rootClassName, permittedClasses, false, rootTypeVars);
    }

    private void generateMatcherBuilder(TypeSpec.Builder rootBuilder, ClassName rootClassName, List<TypeElement> permittedClasses, boolean isFunction, List<TypeVariableName> rootTypeVars) {
        String builderName = isFunction ? "MatcherBuilder" : "ConsumerMatcherBuilder";
        TypeVariableName rType = TypeVariableName.get("R");
        
        TypeSpec.Builder builder = TypeSpec.classBuilder(builderName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);

        builder.addTypeVariables(rootTypeVars);
        if (isFunction) {
            builder.addTypeVariable(rType);
        }

        // Fields to store functions/consumers
        for (TypeElement permitted : permittedClasses) {
            TypeName permittedType;
            if (!permitted.getTypeParameters().isEmpty()) {
                 permittedType = ParameterizedTypeName.get(ClassName.get(permitted), rootTypeVars.toArray(new TypeName[0]));
            } else {
                 permittedType = TypeName.get(permitted.asType());
            }

            TypeName type = isFunction 
                    ? ParameterizedTypeName.get(ClassName.get(java.util.function.Function.class), permittedType, rType)
                    : ParameterizedTypeName.get(ClassName.get(java.util.function.Consumer.class), permittedType);
            builder.addField(type, "on" + permitted.getSimpleName(), Modifier.PRIVATE);
        }

        // Implement interfaces
        for (int i = 0; i < permittedClasses.size(); i++) {
            String stageName = (isFunction ? "MatcherStage" : "ConsumerMatcherStage") + i;
            
            TypeName interfaceType;
            if (isFunction) {
                if (rootTypeVars.isEmpty()) {
                     interfaceType = ParameterizedTypeName.get(rootClassName.nestedClass(stageName), rType);
                } else {
                     List<TypeName> args = new ArrayList<>(rootTypeVars);
                     args.add(rType);
                     interfaceType = ParameterizedTypeName.get(rootClassName.nestedClass(stageName), args.toArray(new TypeName[0]));
                }
            } else {
                if (rootTypeVars.isEmpty()) {
                     interfaceType = rootClassName.nestedClass(stageName);
                } else {
                     interfaceType = ParameterizedTypeName.get(rootClassName.nestedClass(stageName), rootTypeVars.toArray(new TypeName[0]));
                }
            }
            
            builder.addSuperinterface(interfaceType);
            
            // Implement 'onX' method
            TypeElement permitted = permittedClasses.get(i);
            String nextStageName = (i == permittedClasses.size() - 1) 
                    ? (isFunction ? "MatcherTerminal" : "ConsumerMatcherTerminal") 
                    : (isFunction ? "MatcherStage" : "ConsumerMatcherStage") + (i + 1);
            
            TypeName returnType;
            if (isFunction) {
                 if (rootTypeVars.isEmpty()) {
                     returnType = ParameterizedTypeName.get(rootClassName.nestedClass(nextStageName), rType);
                 } else {
                     List<TypeName> args = new ArrayList<>(rootTypeVars);
                     args.add(rType);
                     returnType = ParameterizedTypeName.get(rootClassName.nestedClass(nextStageName), args.toArray(new TypeName[0]));
                 }
            } else {
                if (rootTypeVars.isEmpty()) {
                     returnType = rootClassName.nestedClass(nextStageName);
                } else {
                     returnType = ParameterizedTypeName.get(rootClassName.nestedClass(nextStageName), rootTypeVars.toArray(new TypeName[0]));
                }
            }

            TypeName permittedType;
            if (!permitted.getTypeParameters().isEmpty()) {
                 permittedType = ParameterizedTypeName.get(ClassName.get(permitted), rootTypeVars.toArray(new TypeName[0]));
            } else {
                 permittedType = TypeName.get(permitted.asType());
            }

            TypeName paramType = isFunction
                    ? ParameterizedTypeName.get(ClassName.get(java.util.function.Function.class), permittedType, rType)
                    : ParameterizedTypeName.get(ClassName.get(java.util.function.Consumer.class), permittedType);

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
        
        TypeName terminalType;
        if (isFunction) {
             if (rootTypeVars.isEmpty()) {
                 terminalType = ParameterizedTypeName.get(rootClassName.nestedClass(terminalName), rType);
             } else {
                 List<TypeName> args = new ArrayList<>(rootTypeVars);
                 args.add(rType);
                 terminalType = ParameterizedTypeName.get(rootClassName.nestedClass(terminalName), args.toArray(new TypeName[0]));
             }
        } else {
             if (rootTypeVars.isEmpty()) {
                 terminalType = rootClassName.nestedClass(terminalName);
             } else {
                 terminalType = ParameterizedTypeName.get(rootClassName.nestedClass(terminalName), rootTypeVars.toArray(new TypeName[0]));
             }
        }
        
        builder.addSuperinterface(terminalType);

        TypeName rootType;
        if (rootTypeVars.isEmpty()) {
             rootType = rootClassName;
        } else {
             rootType = ParameterizedTypeName.get(rootClassName, rootTypeVars.toArray(new TypeName[0]));
        }

        if (isFunction) {
            MethodSpec.Builder asFunc = MethodSpec.methodBuilder("asFunction")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .returns(ParameterizedTypeName.get(ClassName.get(java.util.function.Function.class), rootType, rType))
                    .addCode("return new $T<$T, $T>() {\n", java.util.function.Function.class, rootType, rType)
                    .addCode("    @Override\n")
                    .addCode("    public $T apply($T root) {\n", rType, rootType)
                    .addCode("        return root.accept(new Visitor");
            
            // Generate Visitor type args <T, R>
            if (!rootTypeVars.isEmpty()) {
                asFunc.addCode("<");
                for (int i = 0; i < rootTypeVars.size(); i++) {
                    if (i > 0) asFunc.addCode(", ");
                    asFunc.addCode("$T", rootTypeVars.get(i));
                }
                asFunc.addCode(", $T>", rType);
            } else {
                asFunc.addCode("<$T>", rType);
            }
            
            asFunc.addCode("() {\n");
            
            for (TypeElement permitted : permittedClasses) {
                 TypeName permittedType;
                 if (!permitted.getTypeParameters().isEmpty()) {
                      permittedType = ParameterizedTypeName.get(ClassName.get(permitted), rootTypeVars.toArray(new TypeName[0]));
                 } else {
                      permittedType = TypeName.get(permitted.asType());
                 }

                 asFunc.addCode("            @Override\n")
                       .addCode("            public $T on$L($T val) { return on$L.apply(val); }\n", 
                               rType, permitted.getSimpleName(), permittedType, permitted.getSimpleName());
            }
            
            asFunc.addCode("        });\n")
                  .addCode("    }\n")
                  .addCode("};\n");
            builder.addMethod(asFunc.build());
        } else {
            MethodSpec.Builder asCons = MethodSpec.methodBuilder("asConsumer")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .returns(ParameterizedTypeName.get(ClassName.get(java.util.function.Consumer.class), rootType))
                    .addCode("return new $T<$T>() {\n", java.util.function.Consumer.class, rootType)
                    .addCode("    @Override\n")
                    .addCode("    public void accept($T root) {\n", rootType)
                    .addCode("        root.accept(new Visitor");

            // Generate Visitor type args <T, Object>
             if (!rootTypeVars.isEmpty()) {
                asCons.addCode("<");
                for (int i = 0; i < rootTypeVars.size(); i++) {
                    if (i > 0) asCons.addCode(", ");
                    asCons.addCode("$T", rootTypeVars.get(i));
                }
                asCons.addCode(", $T>", Object.class);
            } else {
                asCons.addCode("<$T>", Object.class);
            }

            asCons.addCode("() {\n");
            
            for (TypeElement permitted : permittedClasses) {
                 TypeName permittedType;
                 if (!permitted.getTypeParameters().isEmpty()) {
                      permittedType = ParameterizedTypeName.get(ClassName.get(permitted), rootTypeVars.toArray(new TypeName[0]));
                 } else {
                      permittedType = TypeName.get(permitted.asType());
                 }

                 asCons.addCode("            @Override\n")
                       .addCode("            public $T on$L($T val) { on$L.accept(val); return null; }\n", 
                               Object.class, permitted.getSimpleName(), permittedType, permitted.getSimpleName());
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
