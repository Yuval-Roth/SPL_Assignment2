package bguspl.set.ex;

import java.util.Random;

public class AISuperSecretIntelligenceService{

    int[][][] sets;
    int cardsCount = 12;

    public AISuperSecretIntelligenceService(){
        sets = new int[cardsCount][cardsCount][cardsCount];
    }

    private boolean isSet(int i, int j, int k){
        return sets[i][j][k] == 1;
    }

    private boolean isPotentialSet(int i, int j, int k){
        
        return sets[i][j][k] != -1;
    }

    public void insertIntel(Integer[] cards,boolean truthValue){

        if(truthValue == false){
            for(int i = 0; i < 3 ;i ++){
                sets[cards[(i)%3]][cards[(i+1)%3]][cards[(i+2)%3]] = -1;  // 0,1,2 -> 1,2,0 -> 2,0,1
                sets[cards[(i+1)%3]][cards[(i)%3]][cards[(i+2)%3]] = -1; // 1,0,2 -> 2,1,0 -> 0,2,1
            }
        }
        else{
            for(int i = 0; i < 3 ;i ++){
                sets[cards[(i)%3]][cards[(i+1)%3]][cards[(i+2)%3]] = 1;  // 0,1,2 -> 1,2,0 -> 2,0,1
                sets[cards[(i+1)%3]][cards[(i)%3]][cards[(i+2)%3]] = 1; // 1,0,2 -> 2,1,0 -> 0,2,1
            }
        }
    }

    public void reportSetClaimed(Integer[] cards) {
        for(Integer card : cards){
            for(int i = 0; i < cardsCount ;i ++){
                if(i == card) continue;
                for(int j = 0; j < cardsCount ;j ++){
                    if(j == card) continue;

                    sets[card][i][j] = 0;
                    sets[i][card][j] = 0;
                    sets[i][j][card] = 0;
                    sets[i][card][j] = 0;
                    sets[j][i][card] = 0;
                    sets[card][j][i] = 0;               
                }
            }
        }
    }

    public Integer[] drawPotentialSet(){
        Random rand = new Random();
        int i,j,k;
        do{
            i = rand.nextInt(cardsCount);
            j = rand.nextInt(cardsCount);
            while(i == j) j = rand.nextInt(cardsCount);
            k = rand.nextInt(cardsCount);
            while(k == i | k == j) k = rand.nextInt(cardsCount);
        }while(isPotentialSet(i, j,k) == false);
        return new Integer[]{i,j,k};
    } 

    public Integer[] getRecommendation(){
        Random rand = new Random();
        int i,j,k;
        int tries = 0;
        do{
            i = rand.nextInt(cardsCount);
            j = rand.nextInt(cardsCount);
            while(i == j) j = rand.nextInt(cardsCount);
            k = rand.nextInt(cardsCount);
            while(k == i | k == j) k = rand.nextInt(cardsCount);
            tries++;
        }while((isSet(i, j,k) == false & tries <= 5) | (isPotentialSet(i, j,k) == false & tries <= 10));
        

        // if(sets[i][j][k] == 1) System.out.println(Thread.currentThread().getName()+" Got intel about a confirmed set!");
        // else if(sets[i][j][k] == 0) System.out.println(Thread.currentThread().getName()+" Got intel about a potential set!");

        return new Integer[]{i,j,k};
    }
}