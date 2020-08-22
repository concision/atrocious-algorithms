package me.concision.algorithms.parity.lcm;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.core.util.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.IntStream;

import static java.lang.Math.ceil;
import static java.lang.Math.floor;
import static java.lang.Math.log;
import static java.lang.Math.round;
import static java.lang.Math.sqrt;
import static java.lang.Math.toIntExact;

/**
 * @author Concision
 */
@Log4j2
public class LcmParitySourceGenerator {
    /**
     * Generated Parity.java location
     */
    private static final File PARITY_JAVA = Paths.get(
            System.getProperty("parity.target", String.join(File.separator, ".", "target", "generated-sources", "java")),
            LcmParitySourceGenerator.class.getPackage().getName().replace('.', File.separatorChar),
            "Parity.java"
    ).toFile();

    /**
     * LCM lookup table upper limit
     */
    private static final int LIMIT = Integer.MAX_VALUE;

    /**
     * Initiate source code generation
     *
     * @param args an empty {@link String[]}
     */
    public static void main(String[] args) {
        log.info("Target Parity.java generated source file: {}", PARITY_JAVA.getAbsolutePath());

        if (PARITY_JAVA.exists()) {
            log.info("Source file already exists");
            return;
        }

        // exit process if parent process (e.g. maven) is closed
        Thread watchdog = new Thread(() -> {
            try {
                //noinspection StatementWithEmptyBody
                while (0 <= System.in.read()) ;
            } catch (IOException ignored) {
            }
            System.exit(-1);
        }, "parent-watchdog");
        // allow system to exit
        watchdog.setDaemon(true);
        watchdog.start();

        log.info("Starting source generation...");
        log.info("");
        StopWatch watch = new StopWatch();
        watch.start();
        try {
            generateSourceFile();
        } catch (Throwable throwable) {
            log.error("An unexpected exception occurred during execution", throwable);
            System.exit(-1);
        }
        watch.stop();
        log.info("");
        log.info("Source generation completed; {} elapsed", watch.formatTime());
    }

    /**
     * Generate the {@link #PARITY_JAVA} source file
     */
    private static void generateSourceFile() {
        BigInteger[] products;

        {
            File cacheFile = Paths.get(System.getProperty("parity.cache", ".cache"), String.valueOf(LIMIT)).toFile();
            // if no cache file is found, compute products
            if (!cacheFile.exists()) {
                // computes the factors for the magic lookup numbers used for parity checking
                log.info("Computing LCM prime power factors...");
                int[][] factorSets = computeFactorSets();
                log.info("Computed LCM prime power factors");

                log.info("");

                log.info("Computing products...");
                products = products(factorSets);
                log.info("Computed products");

                // save products to cache file
                //noinspection ResultOfMethodCallIgnored
                cacheFile.getParentFile().mkdirs();
                try (DataOutputStream stream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cacheFile), 16 * 1024 * 1024 /* 16MB */))) {
                    for (BigInteger product : products) {
                        byte[] bytes = product.toByteArray();
                        stream.writeInt(bytes.length);
                        stream.write(bytes);
                    }
                } catch (IOException exception) {
                    log.error("Failed to write cache file: {}", cacheFile.getAbsolutePath(), exception);
                }
                log.info("Cached products: {}", cacheFile.getAbsolutePath());
            } else { // use cached computed products
                log.info("Reading from cache file: {}", cacheFile.getAbsolutePath());
                List<BigInteger> numbers = new ArrayList<>();
                // read products from cache file
                try (DataInputStream stream = new DataInputStream(new BufferedInputStream(new FileInputStream(cacheFile), 16 * 1024 * 1024 /* 16MB */))) {
                    //noinspection InfiniteLoopStatement
                    while (true) {
                        int length = stream.readInt();
                        byte[] bytes = new byte[length];
                        int read = stream.read(bytes);
                        assert read == bytes.length : "unexpected end of file";
                        numbers.add(new BigInteger(bytes));
                    }
                } catch (EOFException ignored) {
                } catch (IOException exception) {
                    throw new RuntimeException("failed to read from cache file: " + cacheFile.getAbsolutePath(), exception);
                }
                //noinspection ToArrayCallWithZeroLengthArrayArgument
                products = numbers.toArray(new BigInteger[numbers.size()]);
            }
        }

        log.info("");

        log.info("Writing source file...");
        write(products);
        log.info("Source file written");
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
            //noinspection ConstantConditions
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
        // If no split is necessary, the value will be negative
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

            log.info("Total base 2 magnitude: {}", String.format("%,d", round(ceil(cumulativeMagnitude[cumulativeMagnitude.length - 1] / log(2)))));

            // check if a split is necessary
            if (Integer.MAX_VALUE <= cumulativeMagnitude[cumulativeMagnitude.length - 1]) {
                // efficiently search for a split point where the magnitude of the product of the sets are approximately the same
                splitIndex = Arrays.binarySearch(cumulativeMagnitude, cumulativeMagnitude[cumulativeMagnitude.length - 1] / 2);
                if (splitIndex < 0) splitIndex = ~splitIndex - 1;

                watch.stop();
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
            } else {
                splitIndex = -1;

                watch.stop();
                log.info("No split necessary; {} elapsed", watch.formatTime());
            }
        }

        // no split necessary
        if (splitIndex < 0) {
            // remove trailing empty values
            return new int[][]{
                    Arrays.copyOfRange(primePowers, 0, primeIndex)
            };
        } else {
            // split the prime-powers based on the determined split location
            return new int[][]{
                    Arrays.copyOfRange(primePowers, 0, splitIndex),
                    Arrays.copyOfRange(primePowers, splitIndex, primeIndex)
            };
        }
    }

    /**
     * Computes products for each integer set. Each first-level array element in {@param factorSets} will be multiplied
     * together into a {@link BigInteger}. Each product is sequentially computed in parallel. Factors are multiplied
     * together in an order that is more efficient than naive sequential multiplication. Since multiplication
     * time-complexity is based off of the largest factor involved, minimizing the largest factor (i.e. roughly equal
     * factors) at each step reduces the overall time-complexity of the final product.
     *
     * @param factorSets array of integer factors
     * @return {@link BigInteger} products
     */
    private static BigInteger[] products(int[][] factorSets) {
        // computed products
        BigInteger[] products = new BigInteger[factorSets.length];

        // compute the products
        for (int f = 0; f < factorSets.length; f++) {
            log.info("Multiplying product {} of {}", f + 1, factorSets.length);
            // generate the raw bytes
            StopWatch watch = StopWatch.create();

            log.info("Initializing factors...");
            watch.start();
            // sort numbers before boxing, for performance
            Arrays.sort(factorSets[f]);
            // box to BigInteger objects
            BigInteger[] factors = Arrays.stream(factorSets[f]).parallel().mapToObj(BigInteger::valueOf).toArray(BigInteger[]::new);
            // release factors set to be garbage collected
            factorSets[f] = null;
            log.info("Factors sorted and converted to big integers; {} elapsed", watch.formatTime());

            // iteratively multiply factors in parallel
            StopWatch stepWatch = StopWatch.create();
            for (int length = factors.length; length != 1; ) {
                // reset stop watch for the current iteration
                stepWatch.reset();
                stepWatch.start();

                // sort factors and release old array elements to be garbage collected
                Arrays.parallelSort(factors, 0, length);

                // thanks for closures, Java
                int finalLength = length;
                // multiply smallest numbers with largest numbers in parallel
                IntStream.range(0, length / 2)
                        .parallel()
                        .unordered()
                        .forEach(i -> {
                            int l = finalLength - 1 /* one less because 0 indexed */ - i /* go backwards */ - (finalLength % 2) /* skip last one */;
                            factors[i] = factors[i].multiply(factors[l]);
                            factors[l] = null;
                        });

                // shift the prime that was not used in the current multiplication step
                if (length % 2 != 0) {
                    factors[length / 2] = factors[length - 1];
                    factors[length - 1] = null;
                }

                stepWatch.stop();
                log.info("Multiplied {} factors; {} elapsed", length, stepWatch.formatTime());

                // shrink number of factors
                length = length / 2 + length % 2;
            }

            // the only element in factors remaining is the final computed product
            BigInteger product = factors[0];
            // return the product
            products[f] = product;

            watch.stop();
            log.info("Product computed; digits: {}; {} elapsed",
                    String.format("%,d", round(ceil(product.bitLength() * Math.log(2) / Math.log(10)))),
                    watch.formatTime()
            );
        }

        return products;
    }

    /**
     * Generates and writes {@link #PARITY_JAVA} with the BigInteger products encoded inside. This is technically less
     * space efficient, as all strings in classes are UTF-8 encoded by the Java compiler. In order to (relatively)
     * efficiently store them, only the characters in the range 0 to 127 (inclusive) are used, as they only take a
     * single byte, whereas higher numbers use more than 1 bytes. This storage ratio is 1/8th less efficient, as a bit
     * must be set to 0 each time. Fortunately, storing the compiled .class in a .jar will yield compression back to
     * the approximately the original product bytes.
     *
     * @param products products to serialize
     */
    private static void write(BigInteger[] products) {
        // byte lookup table for Java-legal UTF-8 escape sequences
        byte[][] escapedChar = new byte[128][];
        {
            String[] lookup = IntStream.rangeClosed(0, 127).mapToObj(i -> String.valueOf((char) i)).toArray(String[]::new);
            lookup['\0'] = "\\0";
            lookup['\b'] = "\\b";
            lookup['\n'] = "\\n";
            lookup['\r'] = "\\r";
            lookup['\t'] = "\\t";
            lookup['\f'] = "\\f";
            lookup['\"'] = "\\\"";
            lookup['\\'] = "\\\\";
            // write lookups as UTF-8 byte sequences
            for (int i = 0; i < lookup.length; i++) {
                escapedChar[i] = lookup[i].getBytes(StandardCharsets.UTF_8);
            }
        }

        // UTF-8 bytecode encoded character lengths
        int[] utf8lengths = new int[128];
        Arrays.fill(utf8lengths, 1);
        utf8lengths['\0'] = 2; // UTF-8 encoding for '\0' uses bytes C0 80

        // open template file as a template writer
        TemplateWriter templater;
        try {
            templater = new TemplateWriter(IOUtils.toString(new InputStreamReader(LcmParitySourceGenerator.class.getResourceAsStream("/Parity.java"))));
        } catch (IOException exception) {
            throw new RuntimeException("failed to read template Parity.java");
        }

        // generate Java file with computed products
        //noinspection ResultOfMethodCallIgnored
        PARITY_JAVA.getParentFile().mkdirs();
        try (PrintStream output = new PrintStream(new BufferedOutputStream(new FileOutputStream(PARITY_JAVA), 1024 * 1024 /* 1MB */), false, StandardCharsets.ISO_8859_1.name())) {
            // write package declaration
            templater.seek(output, "PACKAGE_DECLARATION");
            output.printf("package %s;%n", LcmParitySourceGenerator.class.getPackage().getName());

            // write product counts
            templater.seek(output, "PRODUCTS_COUNT");
            output.print(products.length);

            // write expected byte lengths of each product
            templater.seek(output, "PRODUCT_BYTE_LENGTHS");
            for (int i = 0; i < products.length; i++) {
                // ceiling of bytes
                output.print((products[i].bitLength() + 7) / 8);

                // delimit next byte length
                if (i + 1 != products.length) {
                    output.print(", ");
                }
            }

            // write computed products
            StopWatch watch = StopWatch.create();
            templater.seek(output, "PRODUCT_BYTES");
            for (int p = 0; p < products.length; p++) {
                log.info("Encoding product {} of {}", p + 1, products.length);
                watch.reset();
                watch.start();

                // convert product to a byte buffer
                ByteBuffer input = ByteBuffer.wrap(products[p].toByteArray());
                // release product to be garbage collected
                products[p] = null;

                output.println("            {");

                // temporarily cached unused bits while encoding the product
                int bits = 0;
                // number of bits cached in the bits variable
                int cachedBits = 0;
                // encode into several UTF-8 strings; 7 bits are encoded at a time
                while (input.hasRemaining() || 0 < cachedBits) {
                    // start of string
                    output.print("                \"");

                    // write up to a maximum of 0xFFFE characters
                    int length = 0;
                    while (input.hasRemaining() || 0 < cachedBits) {
                        // if there are no cached bits, obtain more if there are any
                        if (cachedBits < 7 && input.hasRemaining()) {
                            bits = (bits << 8) | (input.get() & 0xFF);
                            cachedBits += 8;
                        }

                        // read the next 7 bits
                        byte b;
                        if (7 <= cachedBits) {
                            // read highest 7 bits
                            b = (byte) ((bits >> (cachedBits - 7)) & 0x7F);
                        } else {
                            // read last bits, but make them start at the 7 bit mark
                            b = (byte) ((bits << (7 - cachedBits)) & 0x7F); // & 0x7F should be redundant...

                            assert !input.hasRemaining() : "expected end of byte buffer while reading last bits";
                        }

                        // break if this string length will exceed 0xFFFE bytes
                        if (0xFFFE < length + utf8lengths[b]) {
                            break;
                        }

                        // write character as UTF-8 escape sequences
                        output.write(escapedChar[b]);
                        // add the bytecode UTF-8 encoding length
                        length += utf8lengths[b];

                        // remove bits
                        bits &= ~(0x7F << (cachedBits - 7));
                        cachedBits -= Math.min(cachedBits, 7);
                    }

                    // end of string
                    output.print("\"");
                    // add another comma if there is another String next
                    if (input.hasRemaining() || 0 < cachedBits) {
                        output.print(",");
                    }
                    output.println();
                }

                output.print("            }");
                // add another comma if there is another product
                if (p != products.length - 1) {
                    output.print(',');
                }

                watch.stop();
                log.info("Encoded product; {} elapsed", watch.formatTime());
            }

            // write rest of the template
            templater.finish(output);
        } catch (IOException exception) {
            throw new RuntimeException("failed to write Parity class", exception);
        }
    }
}
