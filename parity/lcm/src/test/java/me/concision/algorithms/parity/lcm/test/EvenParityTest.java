package me.concision.algorithms.parity.lcm.test;

import me.concision.algorithms.parity.lcm.Parity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static me.concision.algorithms.parity.lcm.LcmParitySourceGenerator.LIMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("ConstantConditions")
public class EvenParityTest {
    @BeforeAll
    public static void initialize() {
        Parity.load();
    }

    private static void test(long n) {
        assertEquals(n % 2L == 0L, Parity.isEven(n), "unexpected even parity: " + n);
    }

    @Test
    public void baseRange() {
        for (int n = -8; n < 8; n++) {
            test(n);
        }
    }

    @Test
    public void maxLimit() {
        test(LIMIT - 1);
        test(LIMIT);
    }

    @Test
    public void sqrtRange() {
        int sqrt = (int) (Math.sqrt(LIMIT));
        for (int i = Math.max(-LIMIT, sqrt - 4); i <= Math.min(sqrt + 4, LIMIT); i++) {
            test(i);
        }
    }

    @Test
    public void randomValues() {
        Random random = new Random();

        for (int i = 0; i < 16; i++) {
            test(random.nextInt(LIMIT));
        }
    }

    @Test
    public void longValues() {
        if (LIMIT == Integer.MAX_VALUE) {
            Random random = new Random();

            for (int i = 0; i < 16; i++) {
                test(random.nextLong());
            }
        }
    }
}
