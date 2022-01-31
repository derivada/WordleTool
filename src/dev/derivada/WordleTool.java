package dev.derivada;


import java.io.*;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.Stream;

/**
 * Wordle Tool by derivadas
 * <p>
 * Clue Types:
 * Format: Character -> n
 * n = -1 : Character not in word (gray)
 * n = 0 : Character in unknown position
 * n in range [1, 5]: Character in position n
 */

public class WordleTool {

    private static Properties config;
    private static final String CONFIG_FILE = "config.txt";
    private static final List<Clue> DEFAULT_CLUES;


    // Optimized algorithm data structures
    private static final char[] letters = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's',
            't', 'u', 'v', 'w', 'x', 'y', 'z'};
    private static final int[] primes = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73,
            79, 83, 89, 97, 101};

    private static String[] words;
    private static int[] wordValues;

    private static Clue[] sortedClues;

    static {
        DEFAULT_CLUES = new ArrayList<>();
        DEFAULT_CLUES.add(new Clue(-1, 'a'));
        DEFAULT_CLUES.add(new Clue(-1, 'e'));
        DEFAULT_CLUES.add(new Clue(0, 'i'));
        DEFAULT_CLUES.add(new Clue(0, 's'));
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
        loadConfig();
        if (!setup()) {
            System.out.println("Data preprocessing failed!");
            return;
        }
        int test_mode, test_size, number_of_tests, time_between_tests;
        try {
            test_mode = Integer.parseInt(config.getProperty("test-mode"));
            test_size = Integer.parseInt(config.getProperty("test-size"));
            number_of_tests = Integer.parseInt(config.getProperty("number-of-tests"));
            time_between_tests = Integer.parseInt(config.getProperty("time-between-tests"));
        } catch (NumberFormatException err) {
            System.out.println("Couldn't parse test parameters, check config file!");
            err.printStackTrace();
            return;
        }

        switch (test_mode) {
            case 1:
                repeatedTest(test_size, number_of_tests, time_between_tests);
                break;
            case 2:
                repeatedTestPrimes(test_size, number_of_tests, time_between_tests);
                break;
            default:
                System.out.println("Couldn't recognize test mode!");
                break;
        }

        saveConfig();
    }

    private static void repeatedTest(int test_size, int number_of_tests, int time_between_tests) {
        String[] results = null;
        System.out.println("--- REPEATED ITERATIONS TEST ---");
        System.out.println("--- BASIC ALGORITHM V2 ---");
        System.out.println("Test size: " + test_size);
        System.out.println("Number of tests: " + test_size);
        System.out.println("Time between tests: " + time_between_tests + " ms");
        System.out.println("test | total time of test | average iteration time");
        double avgTestTime = 0;
        double avgIterTime = 0;
        long[] testTimes = new long[number_of_tests];
        double[] avgIterTimes = new double[number_of_tests];

        for (int i = 0; i < number_of_tests; i++) {
            System.out.printf("TEST %d | ", i);
            long startTime, stopTime;
            startTime = System.currentTimeMillis();
            // Save first iteration results
            results = solveTrivial(DEFAULT_CLUES, 0);
            for (int j = 1; j < test_size; j++) {
                solveTrivial(DEFAULT_CLUES, 0);
            }
            stopTime = System.currentTimeMillis();
            testTimes[i] = stopTime - startTime;
            avgIterTimes[i] = (double) testTimes[i] / test_size;
            System.out.printf("%d ms | ", testTimes[i]);
            System.out.printf("%.4f ms\n", avgIterTimes[i]);
            try {
                Thread.sleep(time_between_tests);
            } catch (InterruptedException err) {
                System.out.println("Tests interrupted");
                return;
            }
        }
        for (int i = 0; i < number_of_tests; i++) {
            avgTestTime += testTimes[i];
            avgIterTime += avgIterTimes[i];
        }
        avgTestTime = avgTestTime / number_of_tests;
        avgIterTime = avgIterTime / number_of_tests;

        System.out.printf("AVERAGE: %.4f ms | %.4f ms\n", avgTestTime, avgIterTime);

        assert results != null;
        printResults(results, 10, 100);
    }

    private static void repeatedTestPrimes(int test_size, int number_of_tests, int time_between_tests) {
        String[] results = null;
        System.out.println("--- REPEATED ITERATIONS TEST ---");
        System.out.println("--- PRIMES ALGORITHM V1 ---");
        System.out.println("Test size: " + test_size);
        System.out.println("Number of tests: " + test_size);
        System.out.println("Time between tests: " + time_between_tests + " ms");
        System.out.println("test | total time of test | average iteration time");
        double avgTestTime = 0;
        double avgIterTime = 0;
        long[] testTimes = new long[number_of_tests];
        double[] avgIterTimes = new double[number_of_tests];
        // Prepare input for test
        int[] clueTypes = new int[DEFAULT_CLUES.size()];
        char[] clueLetters = new char[DEFAULT_CLUES.size()];
        for (int i = 0; i < DEFAULT_CLUES.size(); i++) {
            clueTypes[i] = DEFAULT_CLUES.get(i).getType();
            clueLetters[i] = DEFAULT_CLUES.get(i).getLetter();
        }
        // Sort the clueTypes and clueLetters parallel arrays by the sortedClues order
        int a = 0;
        int[] clueTypesSorted = new int[clueTypes.length];
        char[] clueLettersSorted = new char[clueLetters.length];

        for (Clue c : sortedClues) {
            for (int i = 0; i < clueLetters.length; i++) {
                if (c.getLetter() == clueLetters[i] && c.getType() == clueTypes[i]) {
                    clueTypesSorted[a] = clueTypes[i];
                    clueLettersSorted[a] = clueLetters[i];
                    a++;
                    break;
                }
            }
        }
        clueLetters = clueLettersSorted;
        clueTypes = clueTypesSorted;

        // Convert clueLetters to get adequate indexes in the primes array
        int[] clueLettersInt = new int[clueLetters.length];
        for (int i = 0; i < clueLetters.length; i++)
            clueLettersInt[i] = (int) clueLetters[i] - 97;


        for (int i = 0; i < number_of_tests; i++) {
            System.out.printf("TEST %d | ", i);
            System.out.flush();
            long startTime, stopTime;
            startTime = System.currentTimeMillis();
            // Save first iteration results
            results = solveMultithread(clueTypes, clueLetters, clueLettersInt,0);
            for (int j = 1; j < test_size; j++) {
                solveMultithread(clueTypes, clueLetters,clueLettersInt, 0);
            }
            stopTime = System.currentTimeMillis();
            testTimes[i] = stopTime - startTime;
            avgIterTimes[i] = (double) testTimes[i] / test_size;
            System.out.printf("%d ms | ", testTimes[i]);
            System.out.printf("%.4f ms\n", avgIterTimes[i]);
            System.out.flush();
            try {
                Thread.sleep(time_between_tests);
            } catch (InterruptedException err) {
                System.out.println("Tests interrupted");
                return;
            }
        }
        for (int i = 0; i < number_of_tests; i++) {
            avgTestTime += testTimes[i];
            avgIterTime += avgIterTimes[i];
        }
        avgTestTime = avgTestTime / number_of_tests;
        avgIterTime = avgIterTime / number_of_tests;

        System.out.printf("AVERAGE: %.4f ms | %.4f ms\n", avgTestTime, avgIterTime);

        assert results != null;
        printResults(results, 10, 100);
    }

    private static void printResults(String[] results, int wordsPerLine, int maxLines) {
        int n = results.length;
        if (n == 0) {
            System.out.println("No words were found in the test!");
        } else {
            float percentageFound = 100;

            if (words.length > 0) {
                percentageFound = ((float) n / (float) words.length) * 100.0f;
            }
            System.out.printf("%d results found for the test (%.2f%% of the dictionary):\n", n, percentageFound);
            for (int i = 0; i < Math.min(maxLines, n); i++) {
                if (i % wordsPerLine == 0) {
                    System.out.println();
                }
                System.out.print(results[i] + "\t");
            }
            if (n > maxLines) {
                System.out.printf("... (%d more)\n", n - maxLines);
            }
        }
    }


    /**
     * This method serves multiple purposes for optimizing the main solving algorithm
     * <p>
     * 1. Stores the dictionary on an array so you don't have to open the file every time you run solve()
     * <p>
     * 2. Creates an array parallel to the dictionary that saves a numeric value computed
     * by multiplying each letter with number n (in dictionary order)
     * present on the word by the n-th prime number. Ex: house -> h = primes['h'-104] * primes [104-'o'] ...
     * <p>
     * 3. Sorts all clues by filtered size (how many words satisfy them on the current
     * dictionary). Ex: 'a' is not in the word less common than 'x' is on the word on the 4th position
     *
     * @return A boolean indicating if the preprocessing was successful
     */

    private static boolean setup() {
        // Part 1. Reads the dictionary and puts it inside the words array
        boolean flag = true;
        File dict = new File(config.getProperty("dictionary-path"));
        if (!dict.exists()) {
            System.out.println("Couldn't find dictionary at path: " + System.getProperty("user.dir") + config.getProperty("dictionary-path"));
            return false;
        }

        FileReader fr = null;
        BufferedReader br = null;
        ArrayList<String> lines = new ArrayList<>();
        String entry;
        int lines_read = 0;

        try {
            fr = new FileReader(dict);
            br = new BufferedReader(fr);
            while ((entry = br.readLine()) != null) {
                lines.add(entry);
            }
        } catch (IOException ioErr) {
            System.out.println("Error while reading dictionary file (line " + lines_read + "): ");
            ioErr.printStackTrace();
            flag = false;
        } finally {
            try {
                if (br != null)
                    br.close();
                if (fr != null)
                    fr.close();
            } catch (IOException err) {
                System.out.println("Couldn't close dictionary file!");
                err.printStackTrace();
            }
        }
        words = new String[lines.size()];
        words = lines.toArray(words);

        // Stop if we found an IO error
        if (!flag)
            return false;

        // Part 2. Creates the wordValues array using functional programming
        wordValues = Stream.of(words).mapToInt(
                s -> s.chars().
                        map(a -> primes[a - 97]).
                        reduce((a, b) -> a * b).
                        orElse(0)
        ).toArray();

        // Part 3. Generates all possible clues and sorts them by the above criteria
        int word_size = 0, i = 0;
        Clue clue;
        try {
            word_size = Integer.parseInt(config.getProperty("word-size"));
        } catch (NumberFormatException err) {
            System.out.println("Couldn't parse word size, check config file!");
            err.printStackTrace();
            return false;
        }
        sortedClues = new Clue[letters.length * (word_size + 2)];
        for (char c : letters) {
            for (int n = -1; n <= word_size; n++) {
                sortedClues[i] = new Clue(n, c);
                i++;
            }
        }
        // Probably inefficient but im a hipster
        Arrays.sort(sortedClues, Comparator.comparingInt(c -> c.applyToList(words).length));
        System.out.println(Arrays.toString(sortedClues));
        return true;
    }

    /**
     * Needs preprocessing
     *
     * @param clues:     see class docs
     * @param maxLength: the maximum length fetched, unlimited for values <= 0
     * @return A string array with all the valid words
     */
    private static String[] solveTrivial(List<Clue> clues, int maxLength) {

        // AVG ITER TIME: 0.11ms
        String[] solution = Arrays.copyOf(words, words.length);
        if (maxLength <= 0) {
            maxLength = Integer.MAX_VALUE;
        }

        for (Clue sortedClue : sortedClues) {
            if (clues.contains(sortedClue)) {
                solution = sortedClue.applyToList(solution);
                // System.out.println("processing clue: " + sortedClues[i] + "\n" + "new filtered size: " + sortedClues[i].applyToList(solution).length);
            }
        }

        return Arrays.copyOfRange(solution, 0, Math.min(solution.length, maxLength));
    }

    private static String[] solveOptimized(int[] clueTypes, char[] clueLetters, int maxLength) {
        // AVG ITER TIME: 0.0966ms
        assert clueTypes.length == clueLetters.length;
        assert words.length == wordValues.length;
        if (maxLength <= 0) {
            maxLength = Integer.MAX_VALUE;
        }

        String[] solution = new String[words.length];
        String word;
        int value, p, n = 0;

        // Apply sort order to clueTypes
        int a = 0;
        int[] clueTypesSorted = new int[clueTypes.length];
        char[] clueLettersSorted = new char[clueLetters.length];

        for (Clue c : sortedClues) {
            for (int i = 0; i < clueLetters.length; i++) {
                if (c.getLetter() == clueLetters[i] && c.getType() == clueTypes[i]) {
                    clueTypesSorted[a] = clueTypes[i];
                    clueLettersSorted[a] = clueLetters[i];
                    a++;
                    break;
                }
            }
        }
        clueLetters = clueLettersSorted;
        clueTypes = clueTypesSorted;

        // Convert clueLetters to get adequate indexes in the primes array
        int[] clueLettersInt = new int[clueLetters.length];
        for (int i = 0; i < clueLetters.length; i++)
            clueLettersInt[i] = (int) clueLetters[i] - 97;

        // Sort the clues arrays
        WORD:
        for (int i = 0; i < words.length; i++) {
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
            solution[n] = word;
            n++;
        }

        return Arrays.copyOfRange(solution, 0, Math.min(n, maxLength));
    }

    private static String[] solveMultithread(int[] clueTypes, char[] clueLetters, int[] clueLettersInt, int maxLength) {
        List<String> solution = new ArrayList<>(words.length);
        ParallelSolver.setup(words, wordValues, solution, clueTypes, clueLetters, clueLettersInt, maxLength);
        ParallelSolver solver = new ParallelSolver(0, words.length);
        solver.invoke();
        solver.join();
        return solver.getSolution().toArray(new String[0]);
    }

    private static void loadConfig() {
        File configFile = new File(CONFIG_FILE);
        config = new Properties();
        if (configFile.exists()) {
            try {
                FileReader fr = new FileReader(configFile);
                config.load(fr);
                fr.close();
                return;
            } catch (IOException err) {
                System.out.println("Error loading settings, loading defaults!");
                err.printStackTrace();
            }
        }
        // Load defaults
        config.setProperty("dictionary-path", "dict.txt");
        config.setProperty("word-size", "5");
        config.setProperty("language", "en");
        config.setProperty("expert-mode", "false");
        config.setProperty("max-rounds", "6");
        config.setProperty("test-mode", "1");
        config.setProperty("test-size", "1000");
        config.setProperty("number-of-tests", "1");
        config.setProperty("time-between-tests", "0");
    }

    private static void saveConfig() {
        File configFile = new File(CONFIG_FILE);
        try {
            FileWriter wr = new FileWriter(configFile);
            config.store(wr, "Wordle Settings");
            wr.close();
        } catch (IOException err) {
            System.out.println("Error storing settings!");
            err.printStackTrace();
        }
    }
}
