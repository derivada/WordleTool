package dev.derivada;


import java.io.*;
import java.lang.reflect.Array;
import java.util.*;

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
    private static int DICT_SIZE = 0;
    private static final HashMap<Character, Integer> DEFAULT_CLUES = new HashMap<>();

    static {
        DEFAULT_CLUES.put('a', -1);
        DEFAULT_CLUES.put('i', -1);
        DEFAULT_CLUES.put('s', 0);
        DEFAULT_CLUES.put('e', 0);
        DEFAULT_CLUES.put('r', 1);
        DEFAULT_CLUES.put('n', 2);
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
        loadConfig();

        int test_mode = 0, test_size = 0;
        try {
            test_mode = Integer.parseInt(config.getProperty("test-mode"));
            test_size = Integer.parseInt(config.getProperty("test-size"));
        } catch (NumberFormatException err) {
            System.out.println("Couldn't parse test parameters, check config file!");
            err.printStackTrace();
            return;
        }

        switch (test_mode) {
            case 1:
                // Repeated test with the predetermined DEFAULT_CLUES
                repeatedTest(test_size);
                break;
            default:
                System.out.println("Couldn't recognize test mode!");
                break;
        }

        saveConfig();
    }

    private static void repeatedTest(int test_size) {
        long startTime, stopTime, totalTime;
        double avgTime;
        ArrayList<String> results;
        startTime = System.currentTimeMillis();
        // Save first iteration results
        results = solve(DEFAULT_CLUES, 0);
        for (int i = 1; i < test_size; i++) {
            solve(DEFAULT_CLUES, 0);
        }
        stopTime = System.currentTimeMillis();
        totalTime = stopTime - startTime;
        avgTime = (double) totalTime / test_size;
        System.out.println("--- REPEATED ITERATIONS TEST ---");
        System.out.println("Test size: " + test_size);
        System.out.println("Total test time: " + totalTime + " ms");
        System.out.printf("Average iteration time: %.4f ms\n", avgTime);

        printResults(results, 10, 100);
    }

    private static void printResults(ArrayList<String> results, int wordsPerLine, int maxLines) {
        if (results == null || results.size() == 0) {
            System.out.println("No words were found in the test!");
        } else {
            float percentageFound = 100;

            if (DICT_SIZE > 0) {
                percentageFound = ((float) results.size() / (float) DICT_SIZE) * 100.0f;
            }
            System.out.printf("%d results found for the test (%.2f%% of the dictionary):\n", results.size(), percentageFound);
            for (int i = 0; i < Math.min(maxLines, results.size()); i++) {
                if (i % wordsPerLine == 0) {
                    System.out.println();
                }
                System.out.print(results.get(i) + "\t");
            }
            if (results.size() > maxLines) {
                System.out.printf("... (%d more)\n", results.size() - maxLines);
            }
        }
    }

    /**
     * Main method for solving. Returns all the valid words for Wordle
     *
     * @param clues:     see class docs
     * @param maxLength: the maximum length fetched, unlimited for values <= 0
     * @return A string array with all the valid words
     */
    private static ArrayList<String> solve(HashMap<Character, Integer> clues, int maxLength) {
        File dict = new File(config.getProperty("dictionary-path"));
        FileReader fr = null;
        BufferedReader br = null;
        String entry = null;
        ArrayList<String> solution = null;
        int lines_read = 0;
        if (maxLength <= 0) {
            maxLength = Integer.MAX_VALUE;
        }

        if (!dict.exists()) {
            System.out.println("Couldn't find dictionary at path: " + System.getProperty("user.dir") + config.getProperty("dictionary-path"));
            return null;
        }

        solution = new ArrayList<>();

        try {
            fr = new FileReader(dict);
            br = new BufferedReader(fr);
            ENTRY:
            while ((entry = br.readLine()) != null) {
                // Check if entry satisfies all the clues
                lines_read++;
                for (Map.Entry<Character, Integer> clue : clues.entrySet()) {
                    int n = clue.getValue();
                    char c = clue.getKey();
                    String s = String.valueOf(c);
                    if (n == -1 && entry.contains(s) || // n = -1 -> not in word (gray)
                            n == 0 && !entry.contains(s) || // n = 0 -> in word (yellow)
                            n > 0 && c != entry.charAt(n)) { // n in range [1,5] -> in position (green)
                        continue ENTRY;
                    }
                }
                if (solution.size() > maxLength) {
                    return solution;
                } else {
                    solution.add(entry);
                }
            }
        } catch (IOException ioErr) {
            System.out.println("Error while reading dictionary file (line " + lines_read + "): ");
            ioErr.printStackTrace();
        } catch (Exception err) {
            System.out.println("Unexpected error:");
            err.printStackTrace();
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
        if (DICT_SIZE == 0) {
            // Update the dictionary size
            DICT_SIZE = lines_read;
        }

        return solution;
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
