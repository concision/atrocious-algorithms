package me.concision.algorithms.parity.lcm.test;

import me.concision.algorithms.parity.lcm.Parity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("ConstantConditions")
public class EvenParityTest {
    @BeforeAll
    public static void initialize() {
        Parity.load();
    }

    private static void test(int n) {
        assertEquals(n % 2 == 0, Parity.isEven(n), "unexpected even parity: " + n);
    }

    @Test
    public void baseRange() {
        for (int n = -8; n < 8; n++) {
            test(n);
        }
    }

    @Test
    public void maxLimit() {
        test(Parity.LIMIT - 1);
        test(Parity.LIMIT);
    }

    @Test
    public void sqrtRange() {
        int sqrt = (int) (Math.sqrt(Parity.LIMIT));
        for (int i = Math.max(-Parity.LIMIT, sqrt - 4); i <= Math.min(sqrt + 4, Parity.LIMIT); i++) {
            test(i);
        }
    }

    @Test
    public void randomValues() {
        Random random = new Random();

        for (int i = 0; i < 16; i++) {
            test(random.nextInt(Parity.LIMIT));
        }
    }
}
