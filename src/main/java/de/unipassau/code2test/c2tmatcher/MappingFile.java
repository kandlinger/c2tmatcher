package de.unipassau.code2test.c2tmatcher;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.utils.Pair;
import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class MappingFile {
    private PrintWriter printWriter;
    private Converter<String, String> methodConverter;
    private Converter<String, String> classConverter;

    public MappingFile(Path mappingFile) throws IOException {
        this.printWriter = new PrintWriter(Files.newBufferedWriter(mappingFile));
        this.classConverter = CaseFormat.UPPER_CAMEL.converterTo(CaseFormat.LOWER_UNDERSCORE);
        this.methodConverter = CaseFormat.LOWER_CAMEL.converterTo(CaseFormat.LOWER_UNDERSCORE);
    }

    public synchronized void printMapping(Map<Pair<Path, MethodDeclaration>, List<Pair<Path, MethodDeclaration>>> map) {
        for (Pair<Path, MethodDeclaration> key : map.keySet()) {
            List<Pair<Path, MethodDeclaration>> value = map.get(key);
            printWriter.print(methodConverter.convert(key.b.getNameAsString()));
            for (Pair<Path, MethodDeclaration> element : value) {
                printWriter.print("," + methodConverter.convert(element.b.getNameAsString()));
            }
            printWriter.println("");
           /* printWriter.print(
                    classConverter.convert(MatchingWorker.removeJavaEnding(key.a.getFileName().toString())) + ","
                            + methodConverter.convert(key.b.getNameAsString()));
            for (Pair<Path, MethodDeclaration> element : value) {
                printWriter.print(",(" + classConverter.convert(MatchingWorker.removeJavaEnding(element.a.getFileName().toString())) + ","
                        + methodConverter.convert(element.b.getNameAsString())
                        + ")");
            }
            printWriter.println("");*/
        }
    }

    public void close() {
        printWriter.close();
    }
}
