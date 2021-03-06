package net.sf.rails.ui.swing.gamespecific._1880;

import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import net.sf.rails.game.specific._1880.ParSlot;
import net.sf.rails.game.specific._1880.ParSlotManager;
import net.sf.rails.ui.swing.core.GridAxis;
import net.sf.rails.ui.swing.core.GridTable;
import net.sf.rails.ui.swing.core.TableUI;

public class ParSlotPanel {
        
    private static enum Rows {HEADER}
    private enum Cols {PRICE,COMPANY,LAST_TRAIN}

    private final JPanel panel;

    public ParSlotPanel(ParSlotManager parSlotManager) {
        
        List<ParSlot> slots = parSlotManager.getParSlots();

        GridAxis rows = GridAxis.builder()
                .add(Rows.HEADER).add(slots, ParSlot.class)
                .build();

        GridAxis cols = GridAxis.builder()
                .add(Cols.PRICE).add(Cols.COMPANY).add(Cols.LAST_TRAIN)
                .build();

        GridTable gridTable = GridTable.builder(rows, cols)
                .row().add("Price").add("Slot").add("<html>Last<br>Train</html>")
                .row().add(ParSlotACCs.PRICE).headerFormat().add(ParSlotACCs.COMPANY).color(ParSlotACCs.COMPANY_COLORS).add(ParSlotACCs.LAST_TRAIN)
                .build();

        TableUI table = TableUI.from(gridTable);

        panel = table.convertToPanel();
        panel.setBorder(BorderFactory.createEtchedBorder());

    }

    public JPanel getPanel() {
        return panel;
    }

}
