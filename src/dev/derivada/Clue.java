package dev.derivada;

import java.util.Arrays;

public class Clue {

    private final int type;
    private final char letter;

    public Clue(int type, char letter){
        this.type = type;
        this.letter = letter;
    }

    public String[] applyToList(String[] words){
        return Arrays.stream(words).filter(this::applyClue).toArray(String[]::new);
    }

    public boolean applyClue(String word){
        int index = word.indexOf(letter);
        return switch (this.type) {
            case -1 -> index < 0;
            case 0 -> index >= 0;
            default -> index == type;
        };
    }

    @Override
    public String toString(){
        return switch(this.type) {
            case -1 -> "Letter " + letter + " not in word";
            case 0 -> "Letter " + letter + " in unknown position";
            default-> "Letter " + letter + " in position " + type;
        };
    }

    public int getType() {
        return type;
    }
    public char getLetter() {
        return letter;
    }

    @Override
    public boolean equals(Object o){
        if(!(o instanceof Clue c))
            return false;
        return c.getType() == this.getType() && c.getLetter() == this.getLetter();
    }
}
