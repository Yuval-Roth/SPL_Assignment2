package bguspl.set.ex;

public class AISuperSecretIntelligenceService{

    int[][][] sets;
    int cardsCount = 12;

    public AISuperSecretIntelligenceService(){
        sets = new int[cardsCount][cardsCount][cardsCount];
    }

    public void insertIntel(Integer[] cards,boolean truthValue){

        if(truthValue == false){
            for(int i = 0; i < 3 ;i ++){
                sets[cards[(i)%3]][cards[(i+1)%3]][cards[(i+2)%3]] = -1;  // 0,1,2 -> 1,2,0 -> 2,0,1
                sets[cards[(i+1)%3]][cards[(i)%3]][cards[(i+2)%3]] = -1; // 1,0,2 -> 2,1,0 -> 0,2,1
            }
        }
        else{
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
    }

    public void getRecommendation(){

    }
}