#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>
#include <unistd.h>
#include <pthread.h>

#define MIN_DICT_SIZE 512
#define WORD_SIZE 5
#define ALPHABET_SIZE 26
#define TEST_SIZE 10000
#define NUMBER_OF_TESTS 10
#define CORES 4

typedef struct
{
    // A clue consist of a letter (char) and a type.
    // type == -1 lettter not on word
    // type == 0: letter on unknown position
    // type == n: letter in nth position (with 1 <= n <= WORD_SIZE)
    int type;
    char letter;
} clue;

typedef int (*solver)(int*, clue*, int, int);

typedef struct
{
    int *result;    // result array
    clue *clues;    // clues
    int cluesSize;  // clues size
    int maxLength;  // max length of result (unused so far)
    int startIndex; // starting index for the partition
    int endIndex;   // ending index for the partition
    int *n;         // initial n, cant return int from here so it must be used as in-out arg
} thread_data;

char letters[ALPHABET_SIZE] = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's',
                               't', 'u', 'v', 'w', 'x', 'y', 'z'};
int primes[ALPHABET_SIZE] = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73,
                             79, 83, 89, 97, 101};
clue *sortedClues;
char **dict;
int *fullDictIndexes;
int *wordValues;
int dictSize;
int fullCluesSize = ALPHABET_SIZE * (WORD_SIZE + 2);
int defaultCluesSize = 4;
clue defaultClues[4] = {
    [0].type = -1,
    [0].letter = 'a',
    [1].type = -1,
    [1].letter = 'e',
    [2].type = 0,
    [2].letter = 'i',
    [3].type = 0,
    [3].letter = 's'};

int setup();
int applyClue(int *resultIndexes, int *dictIndexes, int wordListSize, clue clue);
void sortClues(clue *clues, int orderArray[], int cluesSize);
void test(int test_size, int number_of_tests, solver solver, char* solverName);
void printResults(clue *clues, int clueSize, int *result, int n, int maxLines);
void printClue(clue c);
int solveOptimized(int *result, clue *clues, int cluesSize, int maxLength);
void *solveThread(void *rawData);
int solveOptimizedMultithread(int *result, clue *clues, int cluesSize, int maxLength);

int main(int argc, char const *argv[])
{
    if (setup())
    {
        printf("Data preprocessing failed!");
        return EXIT_FAILURE;
    }

    // test(TEST_SIZE, NUMBER_OF_TESTS, &solveOptimized, "Optimized single-thread solver");
    test(TEST_SIZE, NUMBER_OF_TESTS, &solveOptimizedMultithread, "Optimized multithreaded solver");

    return EXIT_SUCCESS;
}

int setup()
{
    // Part 1. Reads the dictionary and puts it inside the words array

    int bytes = -1, lineCount = 0, currentDictSize = MIN_DICT_SIZE;
    size_t len;
    char *line = NULL;
    FILE *dictFile = fopen("./../dict.txt", "r");
    if (dictFile == NULL)
    {
        printf("Couldn't find dictionary at path: ./../dict.txt\n");
        return 1;
    }

    dict = (char **)malloc(currentDictSize * sizeof(char *));
    while ((bytes = getline(&line, &len, dictFile)) != -1)
    {
        if (lineCount >= currentDictSize)
        {
            currentDictSize *= 2;
            dict = (char **)realloc(dict, currentDictSize * sizeof(char *));
        }
        dict[lineCount] = (char *)malloc(WORD_SIZE * sizeof(char));
        memcpy(dict[lineCount], line, WORD_SIZE);
        lineCount++;
    }
    dictSize = lineCount;
    fclose(dictFile);

    fullDictIndexes = (int *)malloc(dictSize * sizeof(int));
    for (int i = 0; i < dictSize; i++)
        fullDictIndexes[i] = i;

    // 2. Create the WordValues array
    wordValues = (int *)malloc(dictSize * sizeof(int));
    for (int i = 0; i < dictSize; i++)
    {
        wordValues[i] = 1;
        for (int j = 0; j < WORD_SIZE; j++)
        {
            wordValues[i] *= primes[dict[i][j] - 97];
        }
    }

    // Part 3. Generates all possible clues and sorts them by how many matches criteria
    // Less matches -> first in list (when applied they shrink the list a lot)
    int cluesSize = ALPHABET_SIZE * (WORD_SIZE + 2);
    int clueFilterSizes[cluesSize];
    sortedClues = (clue *)malloc(cluesSize * sizeof(clue));

    for (int type = -1; type <= WORD_SIZE; type++)
    {
        for (char letter = 'a'; letter <= 'z'; letter++)
        {
            int index = (type + 1) * ALPHABET_SIZE + (letter - 97);
            sortedClues[index].type = type;
            sortedClues[index].letter = letter;
            int resultsSize, *results;
            results = (int *)malloc(dictSize * sizeof(int));

            resultsSize = applyClue(results, fullDictIndexes, dictSize, sortedClues[index]);
            clueFilterSizes[index] = resultsSize;
            free(results);
        }
    }

    // short the clues array with relation to clueFilterSizes
    sortClues(sortedClues, clueFilterSizes, cluesSize);

    return 0;
}

// applyClue() uses an array of dictionary indexes dictIndexes and its size,
// and puts in the sub-array resultIndexes all the dictionary indexes of the dictionary
// words that match the clue given.
// it return the size of the resultIndexes array
int applyClue(int *resultIndexes, int *dictIndexes, int wordListSize, clue clue)
{
    int n = 0, wordValue = 0;
    char *word;

    for (int i = 0; i < wordListSize; i++)
    {
        word = dict[dictIndexes[i]];
        wordValue = wordValues[dictIndexes[i]];
        switch (clue.type)
        {
        case -1:
            // clue -> letter not in word
            // then the wordValues[i] MUST NOT divide the prime associated to clue letter
            if (wordValue % primes[clue.letter - 97] == 0)
                continue; // next word, we do not include this one since it violated this condition
            break;
        case 0:
            // clue -> letter  in unknown position
            // then the wordValues[i] MUST divide the prime associated to clue letter
            if (wordValue % primes[clue.letter - 97] != 0)
                continue;
            break;
        default:
            // clue -> letter in known position (clue.type - 1) in the word
            // we simply check if the position is correct
            if (word[clue.type - 1] != clue.letter)
                continue;
            break;
        }
        resultIndexes[n] = dictIndexes[i];
        n++;
    }
    return n;
}

void sortClues(clue *clues, int orderArray[], int cluesSize)
{
    // Simple insertion sort, since clues is a small array
    int i, j, key;
    clue keyClue;
    for (i = 1; i < cluesSize; i++)
    {
        keyClue = clues[i];
        key = orderArray[i];
        j = i - 1;
        while (j >= 0 && (orderArray[j] > key))
        {
            clues[j + 1] = clues[j];
            orderArray[j + 1] = orderArray[j];
            j--;
        }
        clues[j + 1] = keyClue;
        orderArray[j + 1] = key;
    }
}

// Applies clues consecutively to the dictionary with the wordValues computed beforehand in setup()
int solveOptimized(int *result, clue *clues, int cluesSize, int maxLength)
{
    int n = dictSize;
    int lastResult[dictSize];

    // Initialize result to full dictionary indexes. We dont want to modify fullDictIndexes since lastResult is changed later
    memcpy(lastResult, fullDictIndexes, dictSize * sizeof(int));

    for (int i = 0; i < fullCluesSize; i++)
    {
        // check if clue is in clues[] array. Can this be optimized?
        for (int j = 0; j < cluesSize; j++)
        {
            if ((clues[j].type == sortedClues[i].type) && (clues[j].letter == sortedClues[i].letter))
            {
                n = applyClue(result, lastResult, n, clues[j]);
                memcpy(lastResult, result, n * sizeof(int));
                /*
                    printf("\n----------------\nj = %d\n", j);
                    printClue(clues[j]);
                    printf("\n");
                    printf("%d) %s", lastResult[0], dict[result[0]]);
                    for (int k = 1; k < 50; k++)
                    {
                        printf("\t%d) %s", lastResult[k], dict[lastResult[k]]);
                    }
                    printf("\nWORD 0: %s\n", dict[0]);
                    printf("\nn = %d\n----------------\n", n);
                */
                continue; // next clue
            }
        }
    }

    return n;
}

void *solveThread(void *rawData)
{
    thread_data *data = rawData;
    int lastResult[dictSize];

    // Initialize result to all dictionary indexes in the range
    memcpy(lastResult, fullDictIndexes, *(data->n) * sizeof(int));

    for (int i = 0; i < fullCluesSize; i++)
    {
        // check if clue is in clues[] array. Can this be optimized?
        for (int j = 0; j < data->cluesSize; j++)
        {
            if ((data->clues[j].type == sortedClues[i].type) && (data->clues[j].letter == sortedClues[i].letter))
            {
                *(data->n) = applyClue(data->result, lastResult, *(data->n), data->clues[j]);
                memcpy(lastResult, data->result, *(data->n) * sizeof(int));
                /*
                    printf("\n----------------\nj = %d\n", j);
                    printClue(clues[j]);
                    printf("\n");
                    printf("%d) %s", lastResult[0], dict[result[0]]);
                    for (int k = 1; k < 50; k++)
                    {
                        printf("\t%d) %s", lastResult[k], dict[lastResult[k]]);
                    }
                    printf("\nWORD 0: %s\n", dict[0]);
                    printf("\nn = %d\n----------------\n", n);
                */
                continue; // next clue
            }
        }
    }
    return EXIT_SUCCESS;
}

int solveOptimizedMultithread(int *result, clue *clues, int cluesSize, int maxLength)
{
    pthread_t threads[CORES];
    thread_data args[CORES];
    int partitionSize = dictSize / CORES; // TODO: refine this so it works when dictSize % CORES != 0
    int n = 0;
    int **resultSizes = (int **)malloc(CORES * sizeof(int *));
    int **partialResults = (int **)malloc(CORES * sizeof(int *)); // an array of int* for partial results

    for (int i = 0; i < CORES; i++)
    {
        partialResults[i] = (int *)malloc(partitionSize * sizeof(int)); // allocate enough memory for partitionSize entries
        resultSizes[i] = (int *)malloc(sizeof(int));                    // allocate enough memory for partitionSize entries
        args[i].result = partialResults[i];
        args[i].clues = clues; // clues is not written to in the method so it is thread-safe to pass the same array to all threads
        args[i].cluesSize = cluesSize;
        args[i].maxLength = maxLength;
        args[i].startIndex = partitionSize * i;
        args[i].endIndex = partitionSize * (i + 1);
        args[i].n = resultSizes[i];
        pthread_create(&threads[i], NULL, solveThread, (void *)&args[i]);
    }

    for (int i = 0; i < CORES; i++)
    {
        pthread_join(threads[i], NULL); // Esperamos a que todos los threads acaben
    }

    for (int i = 0; i < CORES; i++)
    {
        // Combine the results
        n += *(resultSizes[i]); // add the n up
        // put all the integers found in partial results to the result array starting at the last position
        memcpy(&result[n], partialResults[i], *(resultSizes[i]) * sizeof(int));
        free(resultSizes[i]);
    }
    free(resultSizes);
    return n;
}

void test(int test_size, int number_of_tests, solver solver, char* solverName)
{
    int *result = (int *)malloc(dictSize * sizeof(int));
    int n;
    printf("--- REPEATED ITERATIONS TEST ---\n"
           "--- %s ---\n\n"
           "Test size: %d\n"
           "Number of tests: %d\n",
           solverName, test_size, number_of_tests);

    printf("Test number | Test running time (ms) | Average iteration time in test (ms)\n");
    double avgTestTime = 0;
    double avgIterTime = 0;
    double avgIterTimes[number_of_tests];
    double testTimes[number_of_tests];

    for (int i = 0; i < number_of_tests; i++)
    {
        printf("TEST %d | ", i);
        fflush(stdout);
        struct timeval stop, start;
        gettimeofday(&start, NULL);
        // Save first iteration results
        n = solver(result, defaultClues, defaultCluesSize, 0);
        for (int j = 1; j < test_size; j++)
        {
            n = solver(result, defaultClues, defaultCluesSize, 0);
        }
        gettimeofday(&stop, NULL);
        testTimes[i] = ((stop.tv_sec - start.tv_sec) * 1000) + ((stop.tv_usec - start.tv_usec) / 1000); // en milisegundos
        avgIterTimes[i] = testTimes[i] / test_size;
        printf("%.4lf ms | %.4lf ms\n", testTimes[i], avgIterTimes[i]);
        fflush(stdout);
    }
    for (int i = 0; i < number_of_tests; i++)
    {
        avgTestTime += testTimes[i];
        avgIterTime += avgIterTimes[i];
    }
    avgTestTime = avgTestTime / number_of_tests;
    avgIterTime = avgIterTime / number_of_tests;

    printf("AVERAGE: %.4lf ms | %.4lf ms\n", avgTestTime, avgIterTime);
    printResults(defaultClues, defaultCluesSize, result, n, 1000);
}

void printResults(clue *clues, int clueSize, int *result, int n, int maxLines)
{
    printf("\nShowing results for list of clues: \n");
    for (int i = 0; i < clueSize; i++)
    {
        printClue(clues[i]);
    }
    int printSize = n;
    if (maxLines < n)
        printSize = maxLines;
    printf("\nFound %d matching words: \n", n);
    for (int i = 0; i < printSize; i++)
    {
        printf("  %d) %s\n", i, dict[result[i]]);
    }
    if (maxLines < n)
    {
        printf("... (%d more)\n", (n - maxLines));
    }
    printf("\n");
    fflush(stdout);
}

void printClue(clue c)
{
    switch (c.type)
    {
    case -1:
        printf("- Letter %c not in word\n", c.letter);
        break;
    case 0:
        printf("- Letter %c in unknown position\n", c.letter);
        break;
    default:
        printf("- Letter %c in %dth position\n", c.letter, c.type);
        break;
    }
}
void test(int test_size, int number_of_tests, solver solver, char* solverName);
