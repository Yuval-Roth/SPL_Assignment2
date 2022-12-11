package bguspl.set.ex;
import java.util.Arrays;
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

    private Env env;

    private Dealer dealer;

    public AISuperSecretIntelligenceService(Env env, Dealer dealer){
        this.dealer = dealer;
        sets = new int[cardsCount][cardsCount][cardsCount];

        AI_WAIT_BETWEEN_KEY_PRESSES = env.config.penaltyFreezeMillis == 0 ? 100 : 500;

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
                WAIT_BETWEEN_INTELLIGENCE_GATHERING = 1;
                break;
            }
            default: {
                WAIT_BETWEEN_INTELLIGENCE_GATHERING = -1;
            }
                
        }

        this.env = env;
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

    public Integer[] drawPotentialSet(){
        Random rand = new Random();
        int i,j,k;
        int tries = 0;
        do{
            i = rand.nextInt(cardsCount);
            j = rand.nextInt(cardsCount);
            while(i == j){
                j = rand.nextInt(cardsCount);
            } 
            k = rand.nextInt(cardsCount);
            while(k == i | k == j){
                k = rand.nextInt(cardsCount);
            } 
            tries++;
        }while(continueExecution && isPotentialSet(i, j,k) == false & tries++ <= isPotentialSetTries);
        return new Integer[]{i,j,k};
    }

    public Integer[] getIntel(){
        Random rand = new Random();
        int i,j,k;
        int tries = 0;
        
        do{
            i = rand.nextInt(cardsCount);
            j = rand.nextInt(cardsCount);
            while(i == j){
                j = rand.nextInt(cardsCount);
            } 
            k = rand.nextInt(cardsCount);
            while(k == i | k == j){
                k = rand.nextInt(cardsCount);
            } 
            tries++;
        }while((continueExecution && isSet(i, j,k) == false & tries <= isSetTries));
        
        if(isSet(i, j,k) == false){
            do{
                i = rand.nextInt(cardsCount);
            j = rand.nextInt(cardsCount);
            while(i == j){
                j = rand.nextInt(cardsCount);
            } 
            k = rand.nextInt(cardsCount);
            while(k == i | k == j){
                k = rand.nextInt(cardsCount);
            } 
            tries++;
            }while(continueExecution && isPotentialSet(i, j,k) == false & tries <= isPotentialSetTries);
        }

        boolean announce_to_console = false;
        if(announce_to_console){
            if(sets[i][j][k] == 1) System.out.println(Thread.currentThread().getName()+" Got intel about a confirmed set!");
            if(sets[i][j][k] == 0) System.out.println(Thread.currentThread().getName()+" Got intel about a potential set!");
            if(sets[i][j][k] == -1) System.out.println(Thread.currentThread().getName()+" Got intel about a non-set!");
        }

        return new Integer[]{i,j,k};
    }


}