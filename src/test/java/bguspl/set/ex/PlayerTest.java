 package bguspl.set.ex;

 import bguspl.set.*;
import bguspl.set.ex.Player.State;

import org.junit.jupiter.api.AfterEach;
 import org.junit.jupiter.api.BeforeEach;
 import org.junit.jupiter.api.Test;
 import org.junit.jupiter.api.extension.ExtendWith;
 import org.mockito.Mock;
 import org.mockito.junit.jupiter.MockitoExtension;

 import java.util.logging.Logger;

 import static org.junit.jupiter.api.Assertions.assertEquals;
 import static org.junit.jupiter.api.Assertions.assertTrue;
 import static org.mockito.ArgumentMatchers.eq;
 import static org.mockito.Mockito.verify;
 import static org.mockito.Mockito.when;

 @ExtendWith(MockitoExtension.class)
 class PlayerTest {

     Player player;
     @Mock
     Util util;
     @Mock
     private UserInterface ui;
     @Mock
     private Table table;
     @Mock
     private Dealer dealer;
     @Mock
     private Logger logger;

     void assertInvariants() {
         assertTrue(player.id >= 0);
         assertTrue(player.getScore() >= 0);
     }

     @BeforeEach
     void setUp() {
         // purposely do not find the configuration files (use defaults here).
         Env env = new Env(logger, new Config(logger, ""), ui, util);
         player = new Player(env, dealer, table, 0, false);
         assertInvariants();

        Thread playerThread = new Thread(player,"Player");
        playerThread.start();
        try{Thread.sleep(100);
        }catch(InterruptedException ignored){}
        player.resume();

     }

     @AfterEach
     void tearDown() {
        assertInvariants();
        player.pause();
        player.terminate();
     }

     @Test
     void point() {

        assertEquals(Player.State.waitingForActivity, player.getState());

        // calculate the expected score for later
        int expectedScore = player.getScore() + 1;

        player.setState(State.waitingForClaimResult);
        player.nudge();
        assertEquals(Player.State.waitingForClaimResult, player.getState());
        
        Claim claim = new Claim(new Integer[]{1,2,3},player,0);
        claim.validSet = true;
        player.notifyClaim(claim);

        // check that the score was increased correctly
        assertEquals(expectedScore, player.getScore());

        // check that ui.setScore was called with the player's id and the correct score
        verify(ui).setScore(eq(player.id), eq(expectedScore));
     }

 }
