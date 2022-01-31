package dev.derivada;

import java.util.List;
import java.util.concurrent.RecursiveAction;

public class ParallelSolver extends RecursiveAction {
    static final int THRESHOLD = 1024;
    private static final int[] primes = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73,
            79, 83, 89, 97, 101};

    private static String[] words;
    private static int[] wordValues;
    private static boolean setup = false;

    private static List<String> solution;
    private static int[] clueTypes;
    private static char[] clueLetters;
    private static int[] clueLettersInt;
    private static int maxLength;

    private final int startIndex, endIndex;

    ParallelSolver(int startIndex, int endIndex) {
        if (startIndex < endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        } else {
            this.endIndex = startIndex;
            this.startIndex = endIndex;
        }
    }

    public static void setup(String[] words, int[] wordValues, List<String> solution,
                             int[] clueTypes, char[] clueLetters, int[] clueLettersInt, int maxLength) {
        ParallelSolver.words = words;
        ParallelSolver.wordValues = wordValues;
        ParallelSolver.setup = true;
        ParallelSolver.solution = solution;
        ParallelSolver.clueTypes = clueTypes;
        ParallelSolver.clueLetters = clueLetters;
        ParallelSolver.clueLettersInt = clueLettersInt;
        ParallelSolver.maxLength = maxLength;
    }

    public static boolean isSetup() {
        return ParallelSolver.setup;
    }

    @Override
    protected void compute() {
        //System.out.println("Start index = " + startIndex + " Half index = " + halfIndex + " End Index = " + endIndex + " Length = " + (endIndex - startIndex));

        if ((endIndex - startIndex) < THRESHOLD) {
            computeLeaf();
        } else {
            int halfIndex = startIndex + (endIndex - startIndex) / 2;
            ParallelSolver left = new ParallelSolver(startIndex, halfIndex);
            ParallelSolver right = new ParallelSolver(halfIndex, endIndex);
            left.invoke();
            right.invoke();
            left.join();
            right.join();
        }
    }

    private void computeLeaf() {
        // The main algorithm
        String word;
        int value, p;
        WORD:
        for (int i = startIndex; i < endIndex; i++) { // from start index to end index!
            word = words[i];
            for (int j = 0; j < clueLetters.length; j++) {
                value = wordValues[i];
                p = primes[clueLettersInt[j]];
                switch (clueTypes[j]) {
                    case -1:
                        if ((value % p == 0))
                            continue WORD;
                        break;
                    case 0:
                        if ((value % p != 0))
                            continue WORD;
                        break;
                    default:
                        if ((value % p != 0) && (word.indexOf(clueLetters[j]) != clueTypes[j]))
                            continue WORD;
                        break;
                }
            }
            solution.add(word);
        }
    }

    public List<String> getSolution() {
        return solution;
    }
}
