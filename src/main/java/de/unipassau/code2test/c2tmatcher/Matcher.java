package de.unipassau.code2test.c2tmatcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import picocli.CommandLine;

@CommandLine.Command(name = "c2tmatcher", mixinStandardHelpOptions = true, version = "c2tmatcher 1.0",
        description = "Matches methods to their corresponding unit tests.")
public class Matcher implements Callable<Integer> {

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;
    private Path rootDirectory;
    private int numberOfThreads;
    private boolean printMethodCalls;
    private boolean printProgress;

    private static int projectCounter = 0;

    private ExecutorService executorService;

    @CommandLine.Parameters(index = "0", description = "Directory containing the source project directories.")
    public void setRootDirectory(Path rootDirectory) {
        if(!Files.exists(rootDirectory)) throw new CommandLine.ParameterException(spec.commandLine(),
                String.format("Invalid value '%s' for option 'rootDirectory': directory is not existing.", rootDirectory));
        if(!Files.isDirectory(rootDirectory)) throw new CommandLine.ParameterException(spec.commandLine(),
                String.format("Invalid value '%s' for option 'rootDirectory': %s is not a directory.", rootDirectory, rootDirectory));
        this.rootDirectory = rootDirectory;
    }

    @CommandLine.Option(names={"-t", "--threads"}, defaultValue = "1",
            description = "Number of worker threads (default: ${DEFAULT-VALUE}).")
    public void setNumberOfThreads(int numberOfThreads) {
        if(numberOfThreads <= 0 ) throw new CommandLine.ParameterException(spec.commandLine(),
                String.format("Invalid value '%s' for option '-t, --threads': Number of threads need to be greater than 0", numberOfThreads));
        this.numberOfThreads = numberOfThreads;
    }

    @CommandLine.Option(names={"-p", "--progress"},
            description = "Print progress to stderr.")
    public void setPrintProgress(boolean printProgress) {
        this.printProgress = printProgress;
    }

    @CommandLine.Option(names={"-m", "--methodCalls"},
            description = "Print matching method calls of test methods.")
    public void setPrintMethodCalls(boolean printMethodCalls) {
        this.printMethodCalls = printMethodCalls;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Matcher()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        int totalProjects = 0;
        executorService = Executors.newFixedThreadPool(numberOfThreads);

        for (Path projectDirectory : Files.newDirectoryStream(rootDirectory)) {
            if (Files.isDirectory(projectDirectory)) {
                totalProjects++;
                executorService.submit(new MatchingWorker(projectDirectory, printMethodCalls));
            }
        }

        while(Matcher.projectCounter > 0) {
            if(printProgress) {
                System.err.println("Processing project " + (totalProjects - Matcher.projectCounter) + " of " + totalProjects);
            }
            Thread.sleep(1000);
        }
        System.err.println("Processing project " + (totalProjects - Matcher.projectCounter) + " of " + totalProjects);

        return 0;
    }

    public static synchronized void incrementProjectCounter() {
        Matcher.projectCounter++;
    }

    public static synchronized void decrementProjectCounter() {
        Matcher.projectCounter--;
    }
}
