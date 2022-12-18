package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */     
    protected Integer[] slotToCard; // card per slot (if any).
    // Used to be final, we changed it to be able to reset the table

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected Integer[] cardToSlot; // slot per card (if any).
    // Used to be final, we changed it to be able to reset the table

    private int cardCount;

    private LinkedList<Integer> cardsPlacementSlotsOrder;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {
        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        cardsPlacementSlotsOrder = new LinkedList<>();
        for (int i = 0; i < slotToCard.length; i++) {
            cardsPlacementSlotsOrder.add(i);
        }
        Collections.shuffle(cardsPlacementSlotsOrder);
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {
        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Places a card on the table in the first available slot.
     * @param cardToPlace - the card id to place in the slot.
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int cardToPlace) {
        placeCard(cardToPlace, findEmptySlot());
    }
    public void placeCard(int cardToPlace, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        
        cardToSlot[cardToPlace] = slot;
        slotToCard[slot] = cardToPlace;

        env.ui.placeCard(cardToPlace, slot);
        cardCount++;
    }

    /*
     * Finds the first empty slot on the table.
     * @return - the first empty slot on the table.
     */
    private int findEmptySlot() {
        int suggestedSlot = -1;
        for (int i =0 ; i< slotToCard.length; i++) {
            if (slotToCard[i] == null) {
                suggestedSlot = i;
                break;
            }
        }
        return suggestedSlot;
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public int removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        if(slotToCard[slot]!= null){
            cardToSlot[slotToCard[slot]] = null;
            slotToCard[slot] = null;
            env.ui.removeCard(slot);
            cardCount--;
            return slot;
        }
        return slot;
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     * @returns true if action was successful and false otherwise
     */
    public boolean placeToken(int player, int slot) {       
        if(slotToCard[slot] != null){
            env.ui.placeToken(player, slot);
            return true;
        }else return false;
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     * @returns true if action was successful and false otherwise
     */
    public void removeToken(int player, int slot) {
        env.ui.removeToken(player, slot);
    }

    /*
     * Clears the table of all cards and tokens.
     * @return - array of cards that were on the table.
     * @post - the table is empty.
     */
    public Integer[] clearTable() {
        cardsPlacementSlotsOrder = Arrays.stream(slotToCard)
        .filter(Objects::nonNull).map(i->cardToSlot[i]).collect(Collectors.toCollection(LinkedList::new));
        Collections.shuffle(cardsPlacementSlotsOrder);

        Integer[] cardsRemoved = new Integer[getCurrentSize()];

        int i = 0;
        for (Integer slot : cardsPlacementSlotsOrder) {
            cardsRemoved[i++] = slotToCard[slot];
            removeCard(slot);
        }
        // Collections.reverse(cardsPlacementSlotsOrder);
        return cardsRemoved;
    }

    /**
     * returns the current size
     */
    public int getCurrentSize(){
        return cardCount;
    }

    /*
     * Returns the number of empty slots on the table.
     */
    int getEmptySlotCount() {
        int countToPlace = env.config.tableSize - getCurrentSize();
        return countToPlace;
    }
    /*
     * Returns the number of possible sets on the table.
     */
    public int getSetCount() {
        Integer[] cardsOnTable = slotToCard.clone();
        List<Integer> tableCards = Arrays.stream(cardsOnTable).filter(Objects::nonNull).collect(Collectors.toList());
        return env.util.findSets(tableCards, 1).size();
    }

    
    /**
     * @param slot - slot number
     * @return true if the slot is empty and false otherwise
     */
    public boolean isSlotEmpty(int slot) {
        return slotToCard[slot] == null;
    }

    public int getSlotFromCard(int card) {
        return cardToSlot[card];
    }
    
    public LinkedList<Integer> getCardsPlacementSlotsOrder() {
        Collections.shuffle(cardsPlacementSlotsOrder);
        return cardsPlacementSlotsOrder;
    }
    
    public LinkedList<Integer> getCardsOnTable(){
        LinkedList<Integer> cardsOnTable = new LinkedList<>();
        for (int i = 0; i < slotToCard.length; i++) {
            if (slotToCard[i] != null) {
                cardsOnTable.add(slotToCard[i]);
            }
        }
        return cardsOnTable;
    }

}
