package de.unipassau.code2test.c2tmatcher;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.utils.Pair;
import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import javassist.expr.MethodCall;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Callable;


public class MatchingWorker extends SimpleFileVisitor<Path>  implements Callable {
    private Path projectDirectory;
    private boolean printMethodCalls;

    Map<String, Path> testClasses;
    Map<String, Path> classes;

    Map<Path, Path> testClassMapping;

    Map<Path, List<MethodDeclaration>> testClassMethods;
    Map<Path, List<MethodDeclaration>> classMethods;

    Map<Pair<Path, MethodDeclaration>, List<MethodCallExpr>> testClassMethodCalls;

    Map<MethodDeclaration, List<MethodDeclaration>> classMethodToTestMethodsMapping;
    Map<MethodDeclaration, List<MethodDeclaration>> testMethodsToClassMethodsMapping;

    Converter<String, String> methodConverter;
    Converter<String, String> classConverter;

    MatchingWorker(Path projectDirectory, boolean printMethodCalls) {
        Matcher.incrementProjectCounter();
        this.projectDirectory = projectDirectory;
        this.printMethodCalls = printMethodCalls;

        this.testClasses = new HashMap<>();
        this.classes = new HashMap<>();

        this.testClassMapping = new HashMap<>();

        this.testClassMethods = new HashMap<>();
        this.classMethods = new HashMap<>();

        this.testClassMethodCalls = new HashMap<>();

        this.classMethodToTestMethodsMapping = new HashMap<>();
        this.testMethodsToClassMethodsMapping = new HashMap<>();

        this.classConverter = CaseFormat.UPPER_CAMEL.converterTo(CaseFormat.LOWER_UNDERSCORE);
        this.methodConverter = CaseFormat.LOWER_CAMEL.converterTo(CaseFormat.LOWER_UNDERSCORE);

    }

    @Override
    public Object call() throws Exception {
        try {
        Files.walkFileTree(projectDirectory, this);
        matchTestClassesToClasses();
        extractTestClassMethods();
        extractClassMethods();

        for(Path testClass : testClassMethods.keySet()) {
            Path classUnderTest = testClassMapping.get(testClass);
            List<MethodDeclaration> classUnderTestMethods = classMethods.get(classUnderTest);
            for (MethodDeclaration testMethod : testClassMethods.get(testClass)) {
                List<MethodCallExpr> currentTestClassMethodCalls = testClassMethodCalls.get(new Pair<>(testClass, testMethod));
                if(currentTestClassMethodCalls != null) {
                    for (MethodCallExpr methodCallExpr : currentTestClassMethodCalls) {
                        for (MethodDeclaration classUnderTestMethod : classUnderTestMethods) {
                            if (methodCallExpr.getNameAsString().matches(classUnderTestMethod.getNameAsString()) && methodCallExpr.getArguments().size() == classUnderTestMethod.getParameters().size()) {
                                if (printMethodCalls) {
                                    System.out.println(projectDirectory.getFileName() +
                                            "," + classConverter.convert(removeJavaEnding(testClass.getFileName().toString())) +
                                            "," + methodConverter.convert(testMethod.getNameAsString()) +
                                            "," + classConverter.convert(removeJavaEnding(classUnderTest.getFileName().toString())) +
                                            "," + methodConverter.convert(classUnderTestMethod.getNameAsString() + "(" + classUnderTestMethod.getParameters().size() + ")"));
                                } else {
                                    //TODO
                                }
                                break;
                            }
                        }
                    }
                }

            }
        }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Matcher.decrementProjectCounter();
        }
        return null;
    }

    private void extractTestClassMethods() throws IOException {
        for (Path testClass : testClassMapping.keySet()) {
            List<MethodDeclaration> methodDeclarations = new ArrayList<>();

            CompilationUnit compilationUnitTestClass = StaticJavaParser.parse(testClass);
            VoidVisitorAdapter<Void> compilationUnitTestClassVisitor = new VoidVisitorAdapter<Void>() {
                private MethodDeclaration lastMethodDeclaration;

                @Override
                public void visit(MethodDeclaration n, Void arg) {
                    if(n.getAnnotationByName("Test") != null) {
                        methodDeclarations.add(n);
                        lastMethodDeclaration = n;
                        super.visit(n, arg);
                    } else {
                        lastMethodDeclaration = null;
                    }
                }

                @Override
                public void visit(MethodCallExpr n, Void arg) {
                    if(lastMethodDeclaration != null) {
                        if (testClassMethodCalls.containsKey(new Pair<>(testClass, lastMethodDeclaration))) {
                            testClassMethodCalls.get(new Pair<>(testClass, lastMethodDeclaration)).add(n);
                        } else {
                            List<MethodCallExpr> newList = new ArrayList<>();
                            newList.add(n);
                            testClassMethodCalls.put(new Pair<>(testClass, lastMethodDeclaration), newList);
                        }
                        super.visit(n, arg);
                    }
                }
            };
            compilationUnitTestClassVisitor.visit(compilationUnitTestClass, null);
            testClassMethods.put(testClass, methodDeclarations);
        }
    }

    private void extractClassMethods() throws IOException {
        for (Path classUnderTest : testClassMapping.values()) {
            if(!classMethods.containsKey(classUnderTest)) {
                List<MethodDeclaration> methodDeclarations = new ArrayList<>();

                CompilationUnit compilationUnitTestClass = StaticJavaParser.parse(classUnderTest);
                VoidVisitorAdapter<Void> compilationUnitClassVisitor = new VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(MethodDeclaration n, Void arg) {
                        methodDeclarations.add(n);
                        super.visit(n, arg);
                    }
                };
                compilationUnitClassVisitor.visit(compilationUnitTestClass, null);

                classMethods.put(classUnderTest, methodDeclarations);
            }
        }
    }


    private void matchTestClassesToClasses() {
        for(String testClassName : testClasses.keySet()) {
            String expectedClassUnderTestName = testClassName.replace("Test", "");
            if (classes.containsKey(expectedClassUnderTestName)) {
                testClassMapping.put(testClasses.get(testClassName), classes.get(expectedClassUnderTestName));
            }
        }
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if(file.getFileName().toString().contains("Test") && file.getFileName().toString().endsWith(".java")) {
            testClasses.put(file.getFileName().toString(), file);
        } else if(file.getFileName().toString().endsWith(".java")) {
            classes.put(file.getFileName().toString(), file);
        }
        return super.visitFile(file, attrs);
    }

    private static String removeJavaEnding(String filename) {
        return filename.substring(0, filename.length()-5);
    }
}
