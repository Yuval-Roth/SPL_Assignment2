package bguspl.set.ex;

import java.util.Arrays;

public class Claim{

    public final Integer[] cards;
    public final Player claimer;
    public final int claimVersion;
    public boolean validSet;


    /**
     * @param cards - the cards in the claim
     * @param claimer - the player who made the claim
     * @param claimVersion - the version of the claim
     */
    public Claim(Integer[] cards, Player claimer,int claimVersion){
        this.cards = cards;
        this.claimer = claimer;
        this.claimVersion = claimVersion;
    }
    @Override
    public String toString() {
        return "Claim [cards=" + Arrays.toString(cards) + ", claimer=" + claimer.id + ", claimVersion=" + claimVersion
                + ", validSet=" + validSet + "]";
    }
}