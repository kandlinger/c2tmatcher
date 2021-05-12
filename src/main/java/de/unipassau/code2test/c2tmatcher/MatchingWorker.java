package de.unipassau.code2test.c2tmatcher;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.utils.Pair;
import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Callable;


public class MatchingWorker extends SimpleFileVisitor<Path>  implements Callable<Void> {
    Map<String, Path> testClasses;
    Map<String, Path> classes;
    Map<Path, Path> testClassMapping;
    Map<Path, List<MethodDeclaration>> testClassMethods;
    Map<Path, List<MethodDeclaration>> classMethods;
    Map<Pair<Path, MethodDeclaration>, List<MethodCallExpr>> testClassMethodCalls;
    Map<Pair<Path, MethodDeclaration>, List<Pair<Path, MethodDeclaration>>> classMethodToTestMethodsMapping;
    Map<Pair<Path, MethodDeclaration>, List<Pair<Path, MethodDeclaration>>> testMethodsToClassMethodsMapping;

    NodeIterator nodeIterator;
    private Path projectDirectory;
    private MappingFile mappingFile;

    MatchingWorker(Path projectDirectory, MappingFile mappingFile) {
        Matcher.incrementProjectCounter();
        this.projectDirectory = projectDirectory;
        this.mappingFile = mappingFile;

        this.testClasses = new HashMap<>();
        this.classes = new HashMap<>();

        this.testClassMapping = new HashMap<>();

        this.testClassMethods = new HashMap<>();
        this.classMethods = new HashMap<>();

        this.testClassMethodCalls = new HashMap<>();

        this.classMethodToTestMethodsMapping = new HashMap<>();
        this.testMethodsToClassMethodsMapping = new HashMap<>();

        this.nodeIterator = new NodeIterator();

    }

    public static String removeJavaEnding(String filename) {
        return filename.substring(0, filename.length()-5);
    }

    @Override
    public Void call() throws Exception {
        try {
            Files.walkFileTree(projectDirectory, this);
            matchTestClassesToClasses();
            extractTestClassMethods();
            extractClassMethods();
            generateMappings();
            mappingFile.printMapping(classMethodToTestMethodsMapping);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Matcher.decrementProjectCounter();
        }
        return null;
    }

    private void generateMappings() {
        for(Path testClass : testClassMethods.keySet()) {
            Path classUnderTest = testClassMapping.get(testClass);
            List<MethodDeclaration> classUnderTestMethods = classMethods.get(classUnderTest);
            for (MethodDeclaration testMethod : testClassMethods.get(testClass)) {
                List<MethodCallExpr> currentTestClassMethodCalls = testClassMethodCalls.get(new Pair<>(testClass, testMethod));
                if(currentTestClassMethodCalls != null) {
                    for (MethodCallExpr methodCallExpr : currentTestClassMethodCalls) {
                        for (MethodDeclaration classUnderTestMethod : classUnderTestMethods) {
                            if (methodCallExpr.getNameAsString().matches(classUnderTestMethod.getNameAsString()) && methodCallExpr.getArguments().size() == classUnderTestMethod.getParameters().size()) {
                                    Pair<Path, MethodDeclaration> cmttmKey = new Pair<>(classUnderTest, classUnderTestMethod);
                                    Pair<Path, MethodDeclaration> tmtcmKey = new Pair<>(testClass, testMethod);

                                    if(classMethodToTestMethodsMapping.containsKey(cmttmKey)) {
                                        classMethodToTestMethodsMapping.get(cmttmKey).add(new Pair<>(testClass, testMethod));
                                    } else {
                                        List<Pair<Path, MethodDeclaration>> cmttmList = new ArrayList<>();
                                        cmttmList.add((new Pair<>(testClass, testMethod)));
                                        classMethodToTestMethodsMapping.put(cmttmKey, cmttmList);
                                    }

                                    if(testMethodsToClassMethodsMapping.containsKey(tmtcmKey)) {
                                        testMethodsToClassMethodsMapping.get(tmtcmKey).add(new Pair<>(testClass, testMethod));
                                    } else {
                                        List<Pair<Path, MethodDeclaration>> tmtcmList = new ArrayList<>();
                                        tmtcmList.add((new Pair<>(testClass, testMethod)));
                                        testMethodsToClassMethodsMapping.put(tmtcmKey, tmtcmList);
                                    }
                                break;
                            }
                        }
                    }
                }

            }
        }
    }

    private void extractTestClassMethods() throws IOException {
        for (Path testClass : testClassMapping.keySet()) {
            CompilationUnit compilationUnitTestClass = StaticJavaParser.parse(testClass);
            TestClassVisitor testClassVisitor = new TestClassVisitor(testClass);
            testClassVisitor.visit(compilationUnitTestClass, null);
            this.testClassMethodCalls.putAll(testClassVisitor.getTestClassMethodCalls());
            testClassMethods.put(testClass, testClassVisitor.getMethodDeclarations());
        }
    }

    private void extractClassMethods() throws IOException {
        for (Path classUnderTest : testClassMapping.values()) {
            if(!classMethods.containsKey(classUnderTest)) {
                CompilationUnit compilationUnitTestClass = StaticJavaParser.parse(classUnderTest);
                ClassVisitor classVisitor = new ClassVisitor();
                classVisitor.visit(compilationUnitTestClass, null);
                classMethods.put(classUnderTest, classVisitor.getMethodDeclarations());
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
}
