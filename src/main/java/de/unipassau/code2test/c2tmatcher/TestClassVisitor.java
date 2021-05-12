package de.unipassau.code2test.c2tmatcher;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.utils.Pair;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestClassVisitor extends VoidVisitorAdapter<Void> {

    private List<MethodDeclaration> methodDeclarations;
    private MethodDeclaration lastMethodDeclaration;

    private Map<Pair<Path, MethodDeclaration>, List<MethodCallExpr>> testClassMethodCalls;
    private Path testClass;

    public TestClassVisitor(Path testClass) {
        this.testClassMethodCalls = new HashMap<>();
        this.methodDeclarations = new ArrayList<>();
        this.testClass = testClass;
    }

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

    public List<MethodDeclaration> getMethodDeclarations() {
        return methodDeclarations;
    }

    public Map<Pair<Path, MethodDeclaration>, List<MethodCallExpr>> getTestClassMethodCalls() {
        return testClassMethodCalls;
    }
}
