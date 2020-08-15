package me.concision.algorithms.parity.lcm;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Paths;

public class LcmParitySourceGenerator {
    public static void main(String[] args) throws FileNotFoundException {
        File file = Paths.get(LcmParitySourceGenerator.class.getPackage().getName().replace('.', File.separatorChar), "Parity.java").toFile();
        file.getParentFile().mkdirs();

        try (PrintStream stream = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            stream.println("package " + LcmParitySourceGenerator.class.getPackage().getName() + ";");
            stream.println();
            stream.println("public class Parity {");
            stream.println("    public static boolean isEven(int n) {");
            stream.println("        return n % 2 == 0;");
            stream.println("    }");
            stream.println();
            stream.println("    public static boolean isOdd(int n) {");
            stream.println("        return !isEven(n);");
            stream.println("    }");
            stream.println("}");
        }
    }
}
