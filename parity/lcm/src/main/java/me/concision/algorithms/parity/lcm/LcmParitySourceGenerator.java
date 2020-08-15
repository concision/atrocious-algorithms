package me.concision.algorithms.parity.lcm;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Log4j2
public class LcmParitySourceGenerator {
    /**
     * Generated Parity.java location
     */
    private static final File PARITY_JAVA = Paths.get(
            LcmParitySourceGenerator.class.getPackage().getName().replace('.', File.separatorChar),
            "Parity.java"
    ).toFile();

    /**
     * Initiate source code generation
     *
     * @param args an empty {@link String[]}
     */
    @SneakyThrows({InterruptedException.class})
    public static void main(String[] args) {
        log.info("Parity.java generated source file: {}", PARITY_JAVA.getAbsolutePath());

        // create parent directories of Parity.java
        PARITY_JAVA.getParentFile().mkdirs();

        // determine processor parallelism; attempt to leave 1 processor available for system stability
        int processors = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
        // submit task to a ForkJoinPool to cap a parallel Stream's computational usage
        ForkJoinPool pool = new ForkJoinPool(processors);
        pool.submit(() -> {
            try {
                generateSourceFile();
            } catch (Throwable throwable) {
                log.error("An unexpected exception occurred during execution", throwable);
                System.exit(-1);
            }
        });

        // shutdown pool and wait for computation to complete
        pool.shutdown();
        pool.awaitTermination(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    /**
     * Generate {@link #PARITY_JAVA} source file
     */
    private static void generateSourceFile() {

    }
}
