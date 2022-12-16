package bguspl.set.ex;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

import bguspl.set.ex.Player.State;

public class PausingExecution extends PlayerState{

    /**
    * The clicks queue.
    */
    private volatile ConcurrentLinkedQueue<Integer> clickQueue;

    /**
    * The player's currently placed tokens.
    */
    private LinkedList<Integer> placedTokens;

    /**
    * Game entities.
    */
    private final Table table;
    
    public PausingExecution(Player player, ConcurrentLinkedQueue<Integer> clickQueue, LinkedList<Integer> placedTokens,
            Table table) {
        super(player);
        this.clickQueue = clickQueue;
        this.placedTokens = placedTokens;
        this.table = table;
    }

    @Override
    public void run() {
            clearAllPlacedTokens();
            clearClickQueue();
            changeToState(State.paused);
        }      

    @Override
    public State stateName() {
        return State.pausingExecution;
    }  
}
