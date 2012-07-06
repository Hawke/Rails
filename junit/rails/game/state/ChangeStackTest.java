package rails.game.state;

import static org.junit.Assert.*;
import static org.fest.assertions.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import rails.game.Player;
import rails.game.action.PossibleAction;

@RunWith(MockitoJUnitRunner.class)
public class ChangeStackTest {
    
    private final static String STATE_ID = "State";
    
    private Root root;
    private BooleanState state;
    private ChangeStack changeStack;
    
    private AutoChangeSet auto_1, auto_2, auto_3;
    private ActionChangeSet action_1, action_2, action_3;
    @Mock Player player;
    @Mock PossibleAction action;

    @Before
    public void setUp() {
        root = Root.create();
        state = BooleanState.create(root, STATE_ID);
        changeStack = root.getStateManager().getChangeStack();
        auto_1 = (AutoChangeSet)changeStack.getCurrentChangeSet();
        action_1 = changeStack.startActionChangeSet(player, action);
        auto_2 = changeStack.closeCurrentChangeSet();
        state.set(true);
        auto_3 = changeStack.closeCurrentChangeSet();
        action_2 = changeStack.startActionChangeSet(player, action);
        state.set(false);
        action_3 = changeStack.startActionChangeSet(player, action);
    }

    @Test
    public void testGetCurrentChangeSet() {
        assertSame(action_3, changeStack.getCurrentChangeSet());
        // on the stack there are auto_1, action_1, auto_2, action_2
        assertEquals(4, changeStack.sizeUndoStack());
    }

    @Test
    public void testCloseCurrentChangeSet() {
        changeStack.closeCurrentChangeSet();
        assertTrue(action_3.isClosed());
        assertSame(action_3, changeStack.getLastClosedChangeSet());
        // and now action_3 is added
        assertEquals(changeStack.sizeUndoStack(), 5);
    }

    @Test
    public void testUndo() {
        assertFalse(state.value());
        // undo action 3
        assertTrue(changeStack.undo());
        assertEquals(4, changeStack.sizeUndoStack());
        assertSame(action_2, changeStack.getLastClosedChangeSet());
        assertThat(changeStack.getCurrentChangeSet()).isInstanceOf(AutoChangeSet.class);
        assertFalse(state.value());
        // undo action 2
        assertTrue(changeStack.undo());
        assertEquals(3, changeStack.sizeUndoStack());
        assertSame(auto_2, changeStack.getLastClosedChangeSet());
        assertThat(changeStack.getCurrentChangeSet()).isInstanceOf(AutoChangeSet.class);
        assertTrue(state.value());
        // undo auto_2 and action 1
        assertTrue(changeStack.undo());
        assertEquals(1, changeStack.sizeUndoStack());
        assertSame(auto_1, changeStack.getLastClosedChangeSet());
        assertThat(changeStack.getCurrentChangeSet()).isInstanceOf(AutoChangeSet.class);
        assertFalse(state.value());
        // undo should not do anything now
        assertFalse(changeStack.undo());
        assertEquals(1, changeStack.sizeUndoStack());
        assertSame(auto_1, changeStack.getLastClosedChangeSet());
        assertThat(changeStack.getCurrentChangeSet()).isInstanceOf(AutoChangeSet.class);
        assertFalse(state.value());
    }

    @Test
    public void testRedo() {
        // undo everything
        changeStack.undo();
        changeStack.undo();
        changeStack.undo();
        // the state unitl now was checked in testUndo
        // now redo action_1 and auto_2
        assertTrue(changeStack.redo());
        assertEquals(3, changeStack.sizeUndoStack());
        assertSame(auto_2, changeStack.getLastClosedChangeSet());
        assertThat(changeStack.getCurrentChangeSet()).isInstanceOf(AutoChangeSet.class);
        assertTrue(state.value());
        // redo action_2
        assertTrue(changeStack.redo());
        assertEquals(4, changeStack.sizeUndoStack());
        assertSame(action_2, changeStack.getLastClosedChangeSet());
        assertThat(changeStack.getCurrentChangeSet()).isInstanceOf(AutoChangeSet.class);
        assertFalse(state.value());
        // redo action_3
        assertTrue(changeStack.redo());
        assertEquals(5, changeStack.sizeUndoStack());
        assertSame(action_3, changeStack.getLastClosedChangeSet());
        assertThat(changeStack.getCurrentChangeSet()).isInstanceOf(AutoChangeSet.class);
        assertFalse(state.value());
        // then it should do anything
        assertFalse(changeStack.redo());
        assertEquals(5, changeStack.sizeUndoStack());
        assertSame(action_3, changeStack.getLastClosedChangeSet());
        assertThat(changeStack.getCurrentChangeSet()).isInstanceOf(AutoChangeSet.class);
        assertFalse(state.value());
    }

}
