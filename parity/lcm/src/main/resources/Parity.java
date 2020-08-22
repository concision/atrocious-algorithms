/* PACKAGE_DECLARATION */

import java.math.BigInteger;
import java.util.Arrays;

@SuppressWarnings("ALL")
public class Parity {
    private static final BigInteger[] PRIME_POWERS = new BigInteger[/* PRODUCTS_COUNT */ 0 /* /PRODUCTS_COUNT */];

    /**
     * Initialize {@link PRIME_POWERS} from UTF-8 encoded string constants
     */
    static {
        int[] lengths = {/* PRODUCT_BYTE_LENGTHS */};
        String[][] products = {
/* PRODUCT_BYTES */
        };

        // read each product
        for (int n = 0; n < PRIME_POWERS.length; n++) {
            // decoded product bytes to reassemble
            int bufferPos = 0;
            byte[] buffer = new byte[lengths[n]];

            // temporarily cached unused bits while decoding the UTF-8 encoded product
            int bits = 0;
            // number of bits cached in the bits variable
            int bitsCached = 0;

            // encoded product
            String[] product = products[n];
            // read chunk by chunk
            for (int i = 0; i < product.length; i++) {
                String chunk = product[i];
                // read character by character
                for (int c = 0; c < chunk.length(); c++) {
                    // read next 7 bits from chunk
                    bits = (bits << 7) | chunk.charAt(c); // read char should be already be '& 0x7F'd
                    bitsCached += 7; // note that the last character of the last chunk might not actually hold 7 bits

                    // check if this is the last iteration
                    if (bufferPos + 1 == buffer.length) {
                        // assert this is the last chunk
                        assert i + 1 == product.length : "expected end to be at last chunk";
                        assert c + 1 == chunk.length() : "expected end to be at last character of last chunk";

                        // take up to last 8 bits, left-aligned
                        buffer[bufferPos] = (byte) (bits << 8 - Math.min(8, bitsCached) & 0xFF); // & 0xFF should be redundant...
                        // update buffer position to final position; should be now equivalent to 'buffer.length'
                        bufferPos++;

                        // clear bits cached
                        bitsCached = 0;
                    } else if (8 <= bitsCached) {
                        // take top 8 bits
                        buffer[bufferPos] = (byte) ((bits >> (bitsCached - 8)) & 0xFF);
                        // increment next buffer position
                        bufferPos++;

                        // remove top 8 bits; removing from 'bits' is technically unnecessary
                        // bits &= ~(0xFF << (bitsCached - 8));
                        bitsCached -= 8;
                    }
                }
                // release chunk for garbage collection
                product[i] = null;
            }
            // all bits should have been consuming by now
            assert bufferPos == buffer.length : "expected buffer position to be at end";
            assert bitsCached == 0 : "all cached bits should have been consumed";

            // release chunks for garbage collection
            products[n] = null;

            // initialize product as value
            PRIME_POWERS[n] = new BigInteger(buffer);
        }
    }

    /**
     * Tests if a specified integer is even.
     *
     * @param n integer to test
     * @return {@code true} if {@param n} is even; {@code false} otherwise
     */
    public static boolean isEven(int n) {
        return Arrays.stream(PRIME_POWERS)
                .noneMatch(lookup -> lookup.remainder(BigInteger.valueOf(+n)).equals(BigInteger.ZERO));
    }

    /**
     * Tests if a specified integer is odd.
     *
     * @param n integer to test
     * @return {@code true} if {@param n} is odd; {@code false} otherwise
     */
    public static boolean isOdd(int n) {
        return Arrays.stream(PRIME_POWERS)
                .allMatch(lookup -> lookup.remainder(BigInteger.valueOf(+n)).equals(BigInteger.ZERO));
    }
}
