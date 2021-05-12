package de.unipassau.code2test.c2tmatcher;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;

public class ClassVisitor extends VoidVisitorAdapter<Void> {
    private List<MethodDeclaration> methodDeclarations;

    ClassVisitor() {
        this.methodDeclarations = new ArrayList<>();
    }

    @Override
    public void visit(MethodDeclaration n, Void arg) {
        methodDeclarations.add(n);
        super.visit(n, arg);
    }

    public List<MethodDeclaration> getMethodDeclarations() {
        return methodDeclarations;
    }
}
