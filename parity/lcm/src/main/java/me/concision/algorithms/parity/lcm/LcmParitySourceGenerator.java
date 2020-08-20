package me.concision.algorithms.parity.lcm;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.time.StopWatch;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.lang.Math.ceil;
import static java.lang.Math.floor;
import static java.lang.Math.log;
import static java.lang.Math.round;
import static java.lang.Math.sqrt;
import static java.lang.Math.toIntExact;

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
     * LCM lookup table upper limit
     */
    private static final Integer LIMIT = Integer.MAX_VALUE;

    /**
     * Initiate source code generation
     *
     * @param args an empty {@link String[]}
     */
    @SneakyThrows({InterruptedException.class})
    public static void main(String[] args) {
        log.info("Target Parity.java generated source file: {}", PARITY_JAVA.getAbsolutePath());

        // create parent directories of Parity.java
        PARITY_JAVA.getParentFile().mkdirs();

        // determine processor parallelism; attempt to leave 1 processor available for system stability
        int processors = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
        // submit task to a ForkJoinPool to cap a parallel Stream's computational usage
        ForkJoinPool pool = new ForkJoinPool(processors);
        pool.submit(() -> {
            try {
                log.info("Starting source generation...");
                log.info("");
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
     * Generate the {@link #PARITY_JAVA} source file
     */
    private static void generateSourceFile() {
        // computes the factors for the magic lookup numbers used for parity checking
        log.info("Computing LCM prime power factors...");
        int[][] factorSets = computeFactorSets();
        log.info("Computed LCM prime power factors");

        log.info("");

        log.info("Computing products...");
        BigInteger[] products = products(factorSets);
        log.info("Computed products");
    }

    /**
     * Computes all prime-powers (excluding 2) for an LCM of up to {@link #LIMIT} and distributes the factors into
     * 2 disjoint and approximately equal sets (based on the magnitude of the products).
     * Note that all prime powers for primes p '3 <= p <= sqrt(LIMIT)` must be in the same set. Any other prime will
     * only be a prime-power exponent of 1, as a prime-power exponent of 2 will be larger than the supported limit
     * (for LIMIT = 2^31 - 1, exponents of 2 would overflow).
     *
     * @return an array of 2 prime-power factor sets
     */
    private static int[][] computeFactorSets() {
        StopWatch watch = StopWatch.create();

        // The last index that a prime was inserted into; after the prime-power computation, this is the effective
        // length of the following prime-powers array.
        int primeIndex = 0;
        // Computed prime-powers up to the LIMIT; note that not all positions are used in this array.
        // See initialization comments below.
        int[] primePowers;
        // compute the prime-powers
        {
            log.info("Computing primes and their respective exponents...");
            watch.reset();
            watch.start();

            // An upper bound of the prime-counting function, pi(x), is used: pi(x) = x/(log x) * (1 + 3/(2log x))
            // It cannot be precisely known ahead-of-time how many prime powers that will be discovered during sieving,
            // but an approximation of an upper bound would be close. An approximation is preferred here, as
            // List<Integer>'s required memory and computational has a significantly larger overhead than the memory
            // overhead from a mere approximation.
            primePowers = new int[toIntExact(round(ceil((double) LIMIT / log(LIMIT) * (1.0D + 1.5D / log(LIMIT)))))];

            // BitSet is 8x more memory space efficient than a boolean[] as the JVM uses a byte for each boolean.
            // This is indexed with odd numbers only; access with set[f(x)] with f(x)=floor((x-1)/2)
            // (e.g 1 => set[0], 3 => set[1], ...).
            BitSet sieve = new BitSet((LIMIT - 1) / 2 + 1);

            // cached computed constants
            double limitLog = log(LIMIT);
            double limitSqrt = sqrt(LIMIT);

            // compute primes using a sieve of eratosthenes
            // iterate only odd non-unit (e.g. 1) numbers
            for (int n = 3; 0 <= n /* <-- int overflows */ && n <= LIMIT; n += 2) {
                // translate number to an odd BitSet index
                int fn = (n - 1) / 2;
                // if not marked as composite, then it is prime
                if (!sieve.get(fn)) {
                    // mark all odd multiples of n as composite
                    for (long m = (long) fn + (long) n; m < sieve.size(); m += n) { // incrementation is equal to 2n
                        sieve.set((int) m);
                    }

                    // compute the prime-power n^floor(log(limit) / log(n)); thanks Java for Math#pow(int, int)
                    int primePower = 1;
                    for (int i = 0, max = (int) round(floor(limitLog / log(n))); i < max; i++) {
                        primePower *= n;
                    }

                    // a cautious check to ensure that all prime-powers for primes above sqrt(LIMIT) have an exponent of 1
                    assert !(limitSqrt <= n) || primePower == n : String.format("expected prime power exponent to be one (prime: %d; prime power: %d)", n, primePower);
                    // ensure there is a position available in the primePowers array
                    assert primeIndex < primePowers.length : "upper bound approximation of pi(x) was too small";

                    // insert prime power to the array
                    primePowers[primeIndex] = primePower;
                    // update the prime-power insertion index
                    primeIndex++;
                }
            }

            watch.stop();
            log.info("Computed {} prime powers; {} elapsed", String.format("%,d", primeIndex), watch.formatTime());
        }

        // Unfortunately, the product of all the prime-powers can exceed Integer.MAX_VALUE. The prime-powers must be
        // split into at least 2 distinct sets for the integer range. Note that during this split process, all
        // prime-powers whose prime is in the range '3 <= p <= sqrt(LIMIT)` must be grouped together.
        int splitIndex;
        // split the prime-powers into 2 sets of equal magnitude as a product
        {
            log.info("Splitting prime power factors equally into 2 numbers...");
            watch.reset();
            watch.start();

            // create a cumulative magnitude array
            double[] cumulativeMagnitude = new double[primeIndex];
            double cumulative = 0;
            for (int i = 0; i < primeIndex; i++) {
                cumulative += log(primePowers[i]);
                cumulativeMagnitude[i] = cumulative;
            }

            // efficiently search for a split point where the magnitude of the product of the sets are approximately the same
            splitIndex = Arrays.binarySearch(cumulativeMagnitude, cumulativeMagnitude[cumulativeMagnitude.length - 1] / 2);
            if (splitIndex < 0) splitIndex = ~splitIndex - 1;

            watch.stop();
            log.info("Total base 2 magnitude: {}", String.format("%,d", round(ceil(cumulativeMagnitude[cumulativeMagnitude.length - 1] / log(2)))));
            log.info("Split into magnitudes {} (prime-power count: {}); {} elapsed",
                    new String[]{
                            String.format("%,d", round(ceil(cumulativeMagnitude[splitIndex] / log(2)))),
                            String.format("%,d", round(ceil((cumulativeMagnitude[cumulativeMagnitude.length - 1] - cumulativeMagnitude[splitIndex]) / log(2))))
                    },
                    new String[]{
                            String.format("%,d", splitIndex),
                            String.format("%,d", primeIndex - splitIndex)
                    },
                    watch.formatTime()
            );
        }

        // split the prime-powers based on the determined split location
        return new int[][]{
                Arrays.copyOfRange(primePowers, 0, splitIndex),
                Arrays.copyOfRange(primePowers, splitIndex, primeIndex)
        };
    }

    private static BigInteger[] products(int[][] factorSets) {
        BigInteger[] products = new BigInteger[factorSets.length];

        for (int f = 0; f < factorSets.length; f++) {
            log.info("Multiplying product {} of {}", f + 1, factorSets.length);

            byte[] productBytes;
            {
                StopWatch watch = StopWatch.create();

                // sort numbers while they are still integers for performance
                log.info("Initializing factors...");
                watch.start();
                Arrays.sort(factorSets[f]);
                BigInteger[] factors = Arrays.stream(factorSets[f]).parallel().mapToObj(BigInteger::valueOf).toArray(BigInteger[]::new);
                // release reference of factors set for memory
                factorSets[f] = null;
                log.info("Factors sorted and converted to big integers; {} elapsed", watch.formatTime());

                // iterative multiplication of factors
                StopWatch stepWatch = StopWatch.create();
                for (int length = factors.length; length != 1; ) {
                    stepWatch.reset();
                    stepWatch.start();

                    // sort factors and release memory
                    {
                        BigInteger[] finalFactors = factors;
                        factors = IntStream.range(0, length)
                                .parallel()
                                .mapToObj(i -> finalFactors[i])
                                .sorted()
                                .toArray(BigInteger[]::new);
                    }

                    // multiply smallest numbers with largest numbers
                    {
                        // thanks for closures, Java
                        int finalLength = length;
                        BigInteger[] finalFactors = factors;
                        // parallelized multiplication
                        IntStream.range(0, length / 2)
                                .parallel()
                                .unordered()
                                .forEach(i -> {
                                    int l = finalLength - 1 /* one less because 0 indexed */ - i /* go backwards */ - (finalLength % 2) /* skip last one */;
                                    finalFactors[i] = finalFactors[i].multiply(finalFactors[l]);
                                    finalFactors[l] = null;
                                });
                    }
                    // move the odd prime out that did NOT get multiplied with anything
                    if (length % 2 != 0) {
                        factors[length / 2] = factors[length - 1];
                        factors[length - 1] = null;
                    }

                    stepWatch.stop();
                    log.info("Multiplied {} factors; {} elapsed", length, stepWatch.formatTime());

                    // shrink number of factors
                    length = length / 2 + length % 2;
                }

                // pick the last one off
                BigInteger product = factors[0];
                productBytes = product.toByteArray();

                watch.stop();
                log.info("Product computed; digits: {}; {} bytes; {} elapsed",
                        String.format("%,d", round(ceil(product.bitLength() * Math.log(2) / Math.log(10)))),
                        productBytes.length,
                        watch.formatTime()
                );
            }
        }

        return products;
    }
}
