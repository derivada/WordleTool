package dev.derivada;

import java.util.Arrays;

public class Clue {

    private final int type;
    private final char letter;

    public Clue(int type, char letter) {
        this.type = type;
        this.letter = letter;
    }

    public String[] applyToList(String[] words) {
        return Arrays.stream(words).filter(this::applyClue).toArray(String[]::new);
    }

    public boolean applyClue(String word) {
        int index = word.indexOf(letter);
        switch (this.type) {
            case -1:
                return index < 0;
            case 0:
                return index >= 0;
            default:
                return index == type;
        }
    }

    @Override
    public String toString() {
        switch (this.type) {
            case -1:
                return "Letter " + letter + " not in word";
            case 0:
                return "Letter " + letter + " in unknown position";
            default:
                return "Letter " + letter + " in position " + type;
        }
    }

    public int getType() {
        return type;
    }

    public char getLetter() {
        return letter;
    }

    @Override
    public boolean equals(Object o) {
        Clue c;
        if (!(o instanceof Clue))
            return false;
        c = (Clue) o;
        return c.getType() == this.getType() && c.getLetter() == this.getLetter();
    }
}
