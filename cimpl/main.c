#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MIN_DICT_SIZE 512
#define WORD_SIZE 5
#define ALPHABET_SIZE 26

typedef struct
{
    // A clue consist of a letter (char) and a type.
    // type == -1 lettter not on word
    // type == 0: letter on unknown position
    // type == n: letter in nth position (with 1 <= n <= WORD_SIZE)
    int type;
    char letter;
} clue;

char letters[ALPHABET_SIZE] = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's',
                               't', 'u', 'v', 'w', 'x', 'y', 'z'};
int primes[ALPHABET_SIZE] = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73,
                             79, 83, 89, 97, 101};
clue clues[ALPHABET_SIZE * (WORD_SIZE + 2)];

char **dict;
int *wordValues;
int dictSize;

int setup();
void printDict();
int applyClue(int *result, char **wordList, int *wordValues, int wordListSize, clue clue);

int main(int argc, char const *argv[])
{
    if (setup())
    {
        printf("Data preprocessing failed!");
        return EXIT_FAILURE;
    }
    //printDict(stdout);

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

    // 2. Create the WordValues array
    wordValues = (int *)malloc(dictSize * sizeof(int));
    for (int i = 0; i < dictSize; i++)
    {
        wordValues[i] = 1;
        for (int j = 0; j < WORD_SIZE; j++)
        {
            wordValues[i] *= primes[dict[i][j] - 97];
        }
        printf("word = %s, wordValue = %d\n", dict[i], wordValues[i]);
    }

    // Part 3. Generates all possible clues and sorts them by how many matches criteria
    // Less matches -> first in list (when applied they shrink the list a lot)
    int clueFilterSizes[ALPHABET_SIZE * (WORD_SIZE + 2)];
    for (int type = -1; type <= WORD_SIZE; type++)
    {
        for (char letter = 'a'; letter <= 'z'; letter++)
        {
            clues[type * ALPHABET_SIZE + letter].type = type;
            clues[type * ALPHABET_SIZE + letter].letter = letter;
            int resultsSize, *results;
            resultsSize = applyClue(results, dict, wordValues, dictSize, clues[type * ALPHABET_SIZE + letter]);
            for (int i = 0; i < resultsSize; i++)
            {
                printf("%d) %s\n",i, dict[results[i]]);
            }
            clueFilterSizes[type * ALPHABET_SIZE + letter] = resultsSize;
            free(results);
        }
    }

    return 0;
}

void printDict(FILE *stream)
{
    printf("Printing dictionary of length %d:\n", dictSize);
    for (int i = 0; i < 5; i++)
    {
        fprintf(stream, "%d) %s\n", i, dict[i]);
    }
}

// returns result, an array of indexes on the wordList (usually the dictionary) and an int value representing
// the length of that array
int applyClue(int *result, char **wordList, int *wordValues, int wordListSize, clue clue)
{
    result = (int *)malloc(wordListSize * sizeof(int));
    int n = 0;
    
    for (int i = 0; i < wordListSize; i++)
    {
        switch (clue.type)
        {
        case -1:
            // clue -> letter not in word
            // then the wordValues[i] MUST NOT divide the prime associated to clue letter
            if (wordValues[i] % primes[clue.letter - 97] == 0)
                continue; // next word, we do not include this one since it violated this condition
            break;
        case 0:
            // clue -> letter  in unknown position
            // then the wordValues[i] MUST divide the prime associated to clue letter
            if (wordValues[i] % primes[clue.letter - 97] != 0)
                continue;
            break;
        case 1:
            // clue -> letter in known position
            // we simply check if the position is correct
            for (int j = 0; j < WORD_SIZE; j++)
            {
                if (wordList[i][j] == clue.letter)
                    continue;
            }
            break;
        }
        result[n] = i;
        n++;
    }

    return n;
}