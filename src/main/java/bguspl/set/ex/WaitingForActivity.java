package bguspl.set.ex;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

import bguspl.set.Env;
import bguspl.set.ex.Player.State;

public class WaitingForActivity implements PlayerState {

    /**
     * The AI service
     */
    public static AISuperSecretIntelligenceService secretService;
    
    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The player's currently placed tokens.
     */
    private LinkedList<Integer> placedTokens;

    /**
     * The game's dealer
     */
    private Dealer dealer;
    
    /**
     * The clicks queue.
     */
    private volatile ConcurrentLinkedQueue<Integer> clickQueue;

    /**
     * Object for breaking wait() when waiting for general activity
     */
    private volatile Object activityListener;

    private Player player;


    public WaitingForActivity(Env env, Table table, LinkedList<Integer> placedTokens, Dealer dealer,
            ConcurrentLinkedQueue<Integer> clickQueue, Object activityListener, Player player) {
        this.env = env;
        this.table = table;
        this.placedTokens = placedTokens;
        this.dealer = dealer;
        this.clickQueue = clickQueue;
        this.activityListener = activityListener;
        this.player = player;
    }

    @Override
    public State getState() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void run() {

        while(player.getState() == State.waitingForActivity ){

            try{
                //wait for a click
                synchronized(activityListener){
                    activityListener.wait();
                }
            }catch(InterruptedException ignored){}

            //if there is a click to be processed
            while(clickQueue.isEmpty() == false & player.getState() == State.waitingForActivity){
                Integer key = clickQueue.remove();
                placeOrRemoveToken(key);
            }
        }
    }

    /**
     * If a token is placed in the given slot, remove it.
     * Otherwise, place a token in the given slot.
     * Placing or removing a token sends a message to the table.
     * Claims a set if the player has placed a full set.
     * @post - the token is placed or removed from the given slot.
     */
    private void placeOrRemoveToken(Integer slot){

        if(placedTokens.contains(slot) == false){
            boolean insertState = false;
            int tries = 0;
            while(insertState == false & tries <=5 & player.getState() != State.pausingExecution){
                insertState = table.placeToken(player.id, slot);
                tries++;
                try{Thread.sleep(10);}catch(InterruptedException ignored){}
            }
            if(insertState){
                placedTokens.addLast(slot);
                if(placedTokens.size() == Dealer.SET_SIZE) {
                    player.setState(State.turningInClaim);
                    clearClickQueue();
                } 
            }
        }
        else {
            clearPlacedToken(slot);       
        }
    }

    /**
     * Clears the pending clicks queue
     */
    private void clearClickQueue() {
        while(clickQueue.isEmpty() == false){
            clickQueue.remove();
        }
    }
    
    private void clearPlacedToken(Integer slot) {
        table.removeToken(player.id, slot);
        placedTokens.remove(slot);
    }
    
}