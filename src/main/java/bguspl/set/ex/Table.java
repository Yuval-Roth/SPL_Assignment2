package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
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
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        env.ui.placeCard(card, slot); // UI update, this is shitty software design 
    }

    /**
     * Places a card on the table in the first available slot.
     * @param cardToPlace - the card id to place in the slot.
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int cardToPlace) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        int suggestedSlot = findEmptySlot();

        cardToSlot[cardToPlace] = suggestedSlot;
        slotToCard[suggestedSlot] = cardToPlace;

        env.ui.placeCard(cardToPlace, suggestedSlot); // UI update, this is shitty software design 
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

    /*
     * Replaces a card on the table with another card.
     */
    public void replaceCard(int card, int slot) {
        // TODO: implement this method
    }
    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[slotToCard[slot]] = null;
        slotToCard[slot] = null;
        env.ui.removeCard(slot);
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
    // used to be boolean return archetype
    public boolean removeToken(int player, int slot) {
        if(slotToCard[slot] != null){
            env.ui.removeToken(player, slot);
            return true;
        } else return false;
    }

    /*
     * Clears the table of all cards and tokens.
     */
    public Integer[] clearTable() {
        Integer[] cardsRemoved = slotToCard.clone();
        slotToCard = new Integer[slotToCard.length];
        cardToSlot = new Integer[cardToSlot.length];
        return cardsRemoved;
    }

    public int getCurrentSize(){
        int emptySlotCount = 0;
        for (int i = 0; i < slotToCard.length; i ++)
        if (slotToCard[i] != null)
            emptySlotCount ++;
        return emptySlotCount;
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
        List<Integer> deck = Arrays.stream(cardsOnTable).filter(Objects::nonNull).collect(Collectors.toList());
        return env.util.findSets(deck, 1).size();
    }
    

    
}
