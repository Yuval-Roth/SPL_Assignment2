package bguspl.set.ex;

public class Claim{

    public final Integer[] cards;
    public final Player claimer;
    public final int claimVersion; 

    public Claim(Integer[] cards, Player claimer,int claimVersion){
        this.cards = cards;
        this.claimer = claimer;
        this.claimVersion = claimVersion;
    }
}