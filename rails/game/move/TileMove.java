/* $Header: /Users/blentz/rails_rcs/cvs/18xx/rails/game/move/TileMove.java,v 1.5 2007/10/05 22:02:29 evos Exp $
 * 
 * Created on 17-Jul-2006
 * Change Log:
 */
package rails.game.move;

import java.util.List;

import rails.game.*;


/**
 * @author Erik Vos
 */
public class TileMove extends Move {
    
    MapHex hex;
    TileI oldTile;
    int oldTileOrientation;
    List<Station> oldStations;
    TileI newTile;
    int newTileOrientation;
    List<Station> newStations;
    
    public TileMove (MapHex hex, TileI oldTile, int oldTileOrientation, List<Station> oldStations,
            TileI newTile, int newTileOrientation, List<Station> newStations) {
        
        this.hex = hex;
        this.oldTile = oldTile;
        this.oldTileOrientation = oldTileOrientation;
        this.oldStations = oldStations;
        this.newTile = newTile;
        this.newTileOrientation = newTileOrientation;
        this.newStations = newStations;
        
        MoveSet.add (this);
    }


    public boolean execute() {

        hex.replaceTile (oldTile, newTile, newTileOrientation, newStations);
        return true;
    }

    public boolean undo() {
        
        hex.replaceTile (newTile, oldTile, oldTileOrientation, oldStations);
        log.debug ("-Undone: " + toString());
        return true;
    }
    
    public String toString() {
        return "TileMove: hex "+hex.getName()
               +" from #"+oldTile.getName()+"/"+oldTileOrientation
               +" to #"+newTile.getName()+"/"+newTileOrientation;
   }

}
