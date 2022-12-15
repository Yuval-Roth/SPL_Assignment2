package bguspl.set.ex;
import java.util.Random;

import bguspl.set.Env;

public class AISuperSecretIntelligenceService{

    private enum IntelligenceStrength{
        disabled,
        weak,
        medium,
        shabac,
        illuminati
    }

    private static final IntelligenceStrength intelligenceStrength = IntelligenceStrength.illuminati;

    private volatile int[][][] sets;
    private int cardsCount = 12;

    private int isSetTries;
    private int isPotentialSetTries;

    public final int WAIT_BETWEEN_INTELLIGENCE_GATHERING;

    public final int AI_WAIT_BETWEEN_KEY_PRESSES;

    public boolean continueExecution;

    private Table table;
    private Dealer dealer;

    public AISuperSecretIntelligenceService(Env env,Dealer dealer,Table table){
        sets = new int[cardsCount][cardsCount][cardsCount];

        AI_WAIT_BETWEEN_KEY_PRESSES = env.config.penaltyFreezeMillis == 0 ? 50 : 500;

        switch(intelligenceStrength){
            case disabled:{
                isSetTries = 0;
                isPotentialSetTries = 0;
                WAIT_BETWEEN_INTELLIGENCE_GATHERING = 1;
                break;
            }
            case weak:{
                isSetTries = 2;
                isPotentialSetTries = 5;
                WAIT_BETWEEN_INTELLIGENCE_GATHERING = 100;
                break;
            }
            case medium:{
                isSetTries = 5;
                isPotentialSetTries = 10;
                WAIT_BETWEEN_INTELLIGENCE_GATHERING = 50;
                break;
            }
            case shabac:{
                isSetTries = 10;
                isPotentialSetTries = 20;
                WAIT_BETWEEN_INTELLIGENCE_GATHERING = 25;
                break;
            }
            case illuminati:{
                isSetTries = 1000;
                isPotentialSetTries = 2000;
                WAIT_BETWEEN_INTELLIGENCE_GATHERING = 10;
                break;
            }
            default: {
                WAIT_BETWEEN_INTELLIGENCE_GATHERING = -1;
            }
                
        }

        this.table = table;
        this.dealer = dealer;
        continueExecution = intelligenceStrength != IntelligenceStrength.disabled ;
    }

    private boolean isSet(int i, int j, int k){
        return sets[i][j][k] == 1;
    }

    private boolean isPotentialSet(int i, int j, int k){
        return sets[i][j][k] == 0;
    }

    public void sendIntel(Integer[] cards,boolean truthValue){

        int value = truthValue ? 1:-1;

        for(int i = 0; continueExecution && i < 3 ;i ++){
            sets[cards[(i)%3]][cards[(i+1)%3]][cards[(i+2)%3]] = value;  // 0,1,2 -> 1,2,0 -> 2,0,1
            sets[cards[(i+1)%3]][cards[(i)%3]][cards[(i+2)%3]] = value; // 1,0,2 -> 2,1,0 -> 0,2,1
        }
    }

    public void reportSetClaimed(Integer[] cards) {

        //reportSetClaimed costs exactly 3630 operations.....
        //small price to pay for high quality intelligence...

        for(Integer card : cards){
            for(int i = 0; i < cardsCount ;i ++){
                if(i == card) continue;
                for(int j = 0; j < cardsCount ;j ++){
                    if(j == card) continue;
                    
                    if(continueExecution == false) return;

                    sets[card][i][j] = 0; // 0,1,2
                    sets[i][card][j] = 0; // 1,2,0
                    sets[i][j][card] = 0; // 2,0,1
                    sets[i][card][j] = 0; // 1,0,2
                    sets[j][i][card] = 0; // 2,1,0
                    sets[card][j][i] = 0; // 0,2,1
                }
            }
        }
    }

    public void gatherIntel() {
        Integer[] keys;
        keys = drawPotentialSet();
        sendIntel(keys, dealer.isValidSet(keys));
    }

    private Integer[] drawPotentialSet(){
        Integer[] cards;
        int tries = 0;
        do{
            cards = generateCards();
            tries++;
        }while(continueExecution && isPotentialSet(cards[0],cards[1],cards[2]) == false & tries <= isPotentialSetTries);
        return cards;
    }

    public Integer[] getIntel(){
        Integer[] cards;
        int tries = 0;
        
        do{
            cards = generateCards();
            tries++;
        }while((continueExecution && isSet(cards[0],cards[1],cards[2]) == false & tries <= isSetTries));
        
        if(isSet(cards[0],cards[1],cards[2]) == false){
            do{
                cards = generateCards();
                tries++;
            }while(continueExecution && isPotentialSet(cards[0],cards[1],cards[2]) == false & tries <= isPotentialSetTries);
        }

        return cards;
    }

    private Integer[] generateCards(){
        Random rand = new Random();
        int i,j,k;
        do{
            i = rand.nextInt(cardsCount);
        //TODO: AI gets stuck here when stopping players because all the cards on the table are null
        //when the round ends table.isSlotEmpty(i) always returns true in this scenario.
        }while(table.isSlotEmpty(i));

        do{
            j = rand.nextInt(cardsCount);
        }while(table.isSlotEmpty(j) || i == j);

        do{
            k = rand.nextInt(cardsCount);
        }while(table.isSlotEmpty(k) || k == i || k == j);

        return new Integer[]{i,j,k};
    }


}