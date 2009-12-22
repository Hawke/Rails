/* $Header: /Users/blentz/rails_rcs/cvs/18xx/rails/ui/swing/hexmap/GUIHex.java,v 1.31 2009/12/20 15:03:11 evos Exp $*/
package rails.ui.swing.hexmap;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

import org.apache.log4j.Logger;

import rails.game.*;
import rails.game.model.ModelObject;
import rails.ui.swing.GUIToken;
import rails.ui.swing.GameUIManager;
import rails.ui.swing.elements.ViewObject;
import rails.util.Util;

/**
 * Base class that holds common components for GUIHexes of all orientations.
 */

public class GUIHex implements ViewObject {

    public static final double SQRT3 = Math.sqrt(3.0);
    public static double NORMAL_SCALE = 1.0;
    public static double SELECTED_SCALE = 0.8;

    public static int NORMAL_TOKEN_SIZE = 15;
    public static double TILE_GRID_SCALE = 14.0;
    public static double CITY_SIZE = 16.0;

    public static Color BAR_COLOUR = Color.BLUE;
    public static int BAR_WIDTH = 5;

    public static void setScale(double scale) {
        NORMAL_SCALE = scale;
        SELECTED_SCALE = 0.8 * scale;
    }

    protected MapHex model;
    protected GeneralPath innerHexagon;
    protected static final Color highlightColor = Color.red;
    protected Point center;
    /** x and y coordinates on the map */
    protected int x, y;
    protected double zoomFactor = 1.0;
    protected int tokenDiameter = NORMAL_TOKEN_SIZE;

    protected HexMap hexMap; // Containing this hex
    protected String hexName;
    protected int currentTileId;
    protected int originalTileId;
    protected int currentTileOrientation;
    protected String tileFilename;
    protected TileI currentTile;

    protected GUITile currentGUITile = null;
    protected GUITile provisionalGUITile = null;
    protected boolean upgradeMustConnect;

    protected List<TokenI> offStationTokens;
    protected List<Integer> barStartPoints;

    protected GUIToken provisionalGUIToken = null;

    protected double tileScale = NORMAL_SCALE;

    protected String toolTip = "";

    /**
     * Stores the neighbouring views. This parallels the neighors field in
     * MapHex, just on the view side.
     *
     * @todo check if we can avoid this
     */
    private GUIHex[] neighbors = new GUIHex[6];

    // GUI variables
    double[] xVertex = new double[6];
    double[] yVertex = new double[6];
    double len;
    GeneralPath hexagon;
    Rectangle rectBound;
    int baseRotation = 0;

    /** Globally turns antialiasing on or off for all hexes. */
    static boolean antialias = true;
    /** Globally turns overlay on or off for all hexes */
    static boolean useOverlay = true;
    // Selection is in-between GUI and rails.game state.
    private boolean selected;

    protected static Logger log =
            Logger.getLogger(GUIHex.class.getPackage().getName());

    public GUIHex(HexMap hexMap, double cx, double cy, int scale,
            int xCoord, int yCoord) {
        this.hexMap = hexMap;
        this.x = xCoord;
        this.y = yCoord;

        scaleHex (cx, cy, scale, 1.0);

    }

    public void scaleHex (double cx, double cy, int scale, double zoomFactor) {

    	this.zoomFactor = zoomFactor;
    	tokenDiameter = (int)Math.round(NORMAL_TOKEN_SIZE * zoomFactor);

        if (hexMap.getMapManager().getTileOrientation() == MapHex.EW) {
            len = scale;
            xVertex[0] = cx + SQRT3 / 2 * scale;
            yVertex[0] = cy + 0.5 * scale;
            xVertex[1] = cx + SQRT3 * scale;
            yVertex[1] = cy;
            xVertex[2] = cx + SQRT3 * scale;
            yVertex[2] = cy - 1 * scale;
            xVertex[3] = cx + SQRT3 / 2 * scale;
            yVertex[3] = cy - 1.5 * scale;
            xVertex[4] = cx;
            yVertex[4] = cy - 1 * scale;
            xVertex[5] = cx;
            yVertex[5] = cy;

            baseRotation = 30; // degrees
        } else {
            len = scale / 3.0;
            xVertex[0] = cx;
            yVertex[0] = cy;
            xVertex[1] = cx + 2 * scale;
            yVertex[1] = cy;
            xVertex[2] = cx + 3 * scale;
            yVertex[2] = cy + SQRT3 * scale;
            xVertex[3] = cx + 2 * scale;
            yVertex[3] = cy + 2 * SQRT3 * scale;
            xVertex[4] = cx;
            yVertex[4] = cy + 2 * SQRT3 * scale;
            xVertex[5] = cx - 1 * scale;
            yVertex[5] = cy + SQRT3 * scale;

            baseRotation = 0;
        }

        hexagon = makePolygon(6, xVertex, yVertex, true);
        rectBound = hexagon.getBounds();

        center =
                new Point((int) ((xVertex[2] + xVertex[5]) / 2),
                        (int) ((yVertex[0] + yVertex[3]) / 2));
        Point2D.Double center2D =
                new Point2D.Double((xVertex[2] + xVertex[5]) / 2.0,
                        (yVertex[0] + yVertex[3]) / 2.0);

        final double innerScale = 0.8;
        AffineTransform at =
                AffineTransform.getScaleInstance(innerScale, innerScale);
        innerHexagon = (GeneralPath) hexagon.createTransformedShape(at);

        // Translate innerHexagon to make it concentric.
        Rectangle2D innerBounds = innerHexagon.getBounds2D();
        Point2D.Double innerCenter =
                new Point2D.Double(innerBounds.getX() + innerBounds.getWidth()
                                   / 2.0, innerBounds.getY()
                                          + innerBounds.getHeight() / 2.0);
        at =
                AffineTransform.getTranslateInstance(center2D.getX()
                                                     - innerCenter.getX(),
                        center2D.getY() - innerCenter.getY());
        innerHexagon.transform(at);

    }

    public MapHex getHexModel() {
        return this.model;
    }

    public HexMap getHexMap() {
    	return hexMap;
    }

    public void setHexModel(MapHex model) {
        this.model = model;
        currentTile = model.getCurrentTile();
        hexName = model.getName();
        currentTileId = model.getCurrentTile().getId();
        currentTileOrientation = model.getCurrentTileRotation();
        currentGUITile = new GUITile(currentTileId, this);
        currentGUITile.setRotation(currentTileOrientation);
        setToolTip();

        model.addObserver(this);

    }

    public void addBar (int orientation) {
    	orientation %= 6;
    	if (barStartPoints == null) barStartPoints = new ArrayList<Integer>(2);
        int offset = hexMap.getMapManager().getTileOrientation() == MapHex.EW ? 0 : 4;
        barStartPoints.add((offset+5-orientation)%6);
    }

    public Rectangle getBounds() {
        return rectBound;
    }

    public void setBounds(Rectangle rectBound) {
        this.rectBound = rectBound;
    }

    public boolean contains(Point2D.Double point) {
        return (hexagon.contains(point));
    }

    public boolean contains(Point point) {
        return (hexagon.contains(point));
    }

    public boolean intersects(Rectangle2D r) {
        return (hexagon.intersects(r));
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        if (selected) {
            currentGUITile.setScale(SELECTED_SCALE);
        } else {
            currentGUITile.setScale(NORMAL_SCALE);
            provisionalGUITile = null;
        }
    }

    public boolean isSelected() {
        return selected;
    }

    static boolean getAntialias() {
        return antialias;
    }

    static void setAntialias(boolean enabled) {
        antialias = enabled;
    }

    static boolean getOverlay() {
        return useOverlay;
    }

    public static void setOverlay(boolean enabled) {
        useOverlay = enabled;
    }

    /**
     * Return a GeneralPath polygon, with the passed number of sides, and the
     * passed x and y coordinates. Close the polygon if the argument closed is
     * true.
     */
    static GeneralPath makePolygon(int sides, double[] x, double[] y,
            boolean closed) {
        GeneralPath polygon = new GeneralPath(GeneralPath.WIND_EVEN_ODD, sides);
        polygon.moveTo((float) x[0], (float) y[0]);
        for (int i = 1; i < sides; i++) {
            polygon.lineTo((float) x[i], (float) y[i]);
        }
        if (closed) {
            polygon.closePath();
        }

        return polygon;
    }

    public void setNeighbor(int i, GUIHex hex) {
        if (i >= 0 && i < 6) {
            neighbors[i] = hex;
            getHexModel().setNeighbor(i, hex.getHexModel());
        }
    }

    public GUIHex getNeighbor(int i) {
        if (i < 0 || i > 6) {
            return null;
        } else {
            return neighbors[i];
        }
    }

    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;

        if (getAntialias()) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
        } else {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);
        }

        Color terrainColor = Color.WHITE;
        if (isSelected()) {
            g2.setColor(highlightColor);
            g2.fill(hexagon);

            g2.setColor(terrainColor);
            g2.fill(innerHexagon);

            g2.setColor(Color.black);
            g2.draw(innerHexagon);
        }

        paintOverlay(g2);
        paintStationTokens(g2);
        paintOffStationTokens(g2);

        FontMetrics fontMetrics = g2.getFontMetrics();
        if (getHexModel().getTileCost() > 0 ) {
            g2.drawString(
                    Bank.format(getHexModel().getTileCost()),
                    rectBound.x
                            + (rectBound.width - fontMetrics.stringWidth(Integer.toString(getHexModel().getTileCost())))
                            * 3 / 5,
                    rectBound.y
                            + ((fontMetrics.getHeight() + rectBound.height) * 9 / 15));
        }

        Map<PublicCompanyI, City> homes = getHexModel().getHomes();
        
        if (homes  != null) {
           City city;
            Point p;
homes:      for (PublicCompanyI company : homes.keySet()) {
                if (company.isClosed()) continue;
                city = homes.get(company);

                // Only draw the company name if there isn't yet a token of that company
                if (city.getTokens() != null) {
                    for (TokenI token : city.getTokens()) {
                        if (token instanceof BaseToken
                                && ((BaseToken)token).getCompany() == company) {
                            continue homes;
                        }
                    }
                }
                p = getTokenCenter (1, 0, getHexModel().getCities().size(),
                        city.getNumber()-1);
                //log.debug("+++ Home of "+company.getName()+" hex"+getName()+" city"+city.getName()
                //        + " x="+(p.x)+" y="+(p.y));
                drawHome (g2, company, p);
            }
        }

        if (getHexModel().isBlocked()) {
            List<PrivateCompanyI> privates =
                    GameManager.getInstance().getCompanyManager().getAllPrivateCompanies();
            for (PrivateCompanyI p : privates) {
                List<MapHex> blocked = p.getBlockedHexes();
				if (blocked != null) {
					for (MapHex hex : blocked) {
						if (getHexModel().equals(hex)) {
							g2.drawString(
										  "(" + p.getName() + ")",
										  rectBound.x
										  + (rectBound.width - fontMetrics.stringWidth("("
																					   + p.getName()
																					   + ")"))
										  * 1 / 2,
										  rectBound.y
										  + ((fontMetrics.getHeight() + rectBound.height) * 5 / 15));
						}
					}
				}
            }
        }

    }

    private void paintOverlay(Graphics2D g2) {
        if (provisionalGUITile != null) {
            provisionalGUITile.paintTile(g2, center.x, center.y);
        } else {
            currentGUITile.paintTile(g2, center.x, center.y);
        }
    }

    public void paintBars(Graphics g) {
    	if (barStartPoints == null) return;
        Graphics2D g2 = (Graphics2D) g;
    	for (int startPoint : barStartPoints) {
    		drawBar(g2,
    				(int)Math.round(xVertex[startPoint]),
    				(int)Math.round(yVertex[startPoint]),
    				(int)Math.round(xVertex[(startPoint+1)%6]),
    				(int)Math.round(yVertex[(startPoint+1)%6]));
    	}
    }

    protected void drawBar(Graphics2D g2d, int x1, int y1, int x2, int y2) {
        Color oldColor = g2d.getColor();
        Stroke oldStroke = g2d.getStroke();

        g2d.setColor(BAR_COLOUR);
        g2d.setStroke(new BasicStroke(BAR_WIDTH));
        g2d.drawLine(x1, y1, x2, y2);

        g2d.setColor(oldColor);
        g2d.setStroke(oldStroke);
    }

    private void paintStationTokens(Graphics2D g2) {
        if (getHexModel().getCities().size() > 1) {
            paintSplitStations(g2);
            return;
        }

        int numTokens = getHexModel().getTokens(1).size();
        List<TokenI> tokens = getHexModel().getTokens(1);

        for (int i = 0; i < tokens.size(); i++) {
            PublicCompanyI co = ((BaseToken) tokens.get(i)).getCompany();
            Point center = getTokenCenter(numTokens, i, 1, 0);
            drawBaseToken(g2, co, center, tokenDiameter);
        }
    }

    private void paintSplitStations(Graphics2D g2) {
        int numStations = getHexModel().getCities().size();
        int numTokens;
        List<TokenI> tokens;
        Point origin;
        PublicCompanyI co;

        for (int i = 0; i < numStations; i++) {
            tokens = getHexModel().getTokens(i + 1);
            numTokens = tokens.size();

            for (int j = 0; j < tokens.size(); j++) {
                origin = getTokenCenter(numTokens, j, numStations, i);
                co = ((BaseToken) tokens.get(j)).getCompany();
                drawBaseToken(g2, co, origin, tokenDiameter);
            }
        }
    }

    private static int[] offStationTokenX = new int[] { -11, 0 };
    private static int[] offStationTokenY = new int[] { -19, 0 };

    private void paintOffStationTokens(Graphics2D g2) {
        List<TokenI> tokens = getHexModel().getTokens();
        if (tokens == null) return;

        int i = 0;
        for (TokenI token : tokens) {

            Point origin =
                    new Point(center.x + offStationTokenX[i],
                            center.y + offStationTokenY[i]);
            if (token instanceof BaseToken) {

                PublicCompanyI co = ((BaseToken) token).getCompany();
                drawBaseToken(g2, co, origin, tokenDiameter);

            } else if (token instanceof BonusToken) {

                drawBonusToken(g2, (BonusToken) token, origin);
            }
            if (++i > 1) break;
        }
    }

    private void drawBaseToken(Graphics2D g2, PublicCompanyI co, Point center, int diameter) {

        GUIToken token =
                new GUIToken(co.getFgColour(), co.getBgColour(), co.getName(),
                        center.x, center.y, diameter);
        token.setBounds(center.x-(int)(0.5*diameter), center.y-(int)(0.5*diameter),
        		diameter, diameter);

        token.drawToken(g2);
    }

    private void drawHome (Graphics2D g2, PublicCompanyI co, Point origin) {

        Font oldFont = g2.getFont();
        Color oldColor = g2.getColor();

        double scale = 0.5 * zoomFactor;
        String name = co.getName();

        Font font = GUIToken.getTokenFont(name.length());
        g2.setFont(new Font("Helvetica", Font.BOLD,
                (int) (font.getSize() * zoomFactor * 0.75)));
        g2.setColor(Color.BLACK);
        g2.drawString(name, (int) (origin.x + (-4 - 3*name.length()) * scale),
                (int) (origin.y + 4 * scale));

        g2.setColor(oldColor);
        g2.setFont(oldFont);
    }

    private void drawBonusToken(Graphics2D g2, BonusToken bt, Point origin) {
        Dimension size = new Dimension(40, 40);

        GUIToken token =
                new GUIToken(Color.BLACK, Color.WHITE, "+" + bt.getValue(),
                        origin.x, origin.y, 15);
        token.setBounds(origin.x, origin.y, size.width, size.height);

        token.drawToken(g2);
    }

    public void rotateTile() {
        if (provisionalGUITile != null) {
            provisionalGUITile.rotate(1, currentGUITile, upgradeMustConnect);
        }
    }

    private Point getTokenCenter(int numTokens, int currentToken,
            int numStations, int stationNumber) {
        Point p = new Point(center.x, center.y);

        int cityNumber = stationNumber + 1;
        Station station = model.getCity(cityNumber).getRelatedStation();

        // Find the correct position on the tile
        double x = 0;
        double y = 0;
        double xx, yy;
        int positionCode = station.getPosition();
        if (positionCode != 0) {
            y = TILE_GRID_SCALE * zoomFactor;
            double r = Math.toRadians(30 * (positionCode / 50));
            xx = x * Math.cos(r) + y * Math.sin(r);
            yy = y * Math.cos(r) - x * Math.sin(r);
            x = xx;
            y = yy;
        }

        // Correct for the number of base slots and the token number
        switch (station.getBaseSlots()) {
        case 2:
            x += (-0.5 + currentToken) * CITY_SIZE * zoomFactor;
            break;
        case 3:
            if (currentToken < 2) {
                x += (-0.5 + currentToken) * CITY_SIZE * zoomFactor;
                y += -3 + 0.25 * SQRT3 * CITY_SIZE * zoomFactor;
            } else {
                y -= 3 + 0.5 * CITY_SIZE * zoomFactor;
            }
            break;
        case 4:
            x += (-0.5 + currentToken % 2) * CITY_SIZE * zoomFactor;
            y += (0.5 - currentToken / 2) * CITY_SIZE * zoomFactor;
        }

        // Correct for the tile base and actual rotations
        int rotation = model.getCurrentTileRotation();
        double r = Math.toRadians(baseRotation + 60 * rotation);
        xx = x * Math.cos(r) + y * Math.sin(r);
        yy = y * Math.cos(r) - x * Math.sin(r);
        x = xx;
        y = yy;

        p.x += x;
        p.y -= y;

        // log.debug("New origin for hex "+getName()+" tile
        // #"+model.getCurrentTile().getId()
        // + " city "+cityNumber+" pos="+positionCode+" token "+currentToken+":
        // x="+x+" y="+y);

        return p;
    }

    // Added by Erik Vos
    /**
     * @return Returns the name.
     */
    public String getName() {
        return hexName;
    }

    /**
     * @return Returns the currentTile.
     */
    public TileI getCurrentTile() {
        return currentTile;
    }

    /**
     * @param currentTileOrientation The currentTileOrientation to set.
     */
    public void setTileOrientation(int tileOrientation) {
        this.currentTileOrientation = tileOrientation;
    }

    public String getToolTip() {
        return toolTip;
    }

    protected void setToolTip() {
        StringBuffer tt = new StringBuffer("<html>");
        tt.append("<b>Hex</b>: ").append(hexName);
        String name = model.getCityName();
        if (Util.hasValue(name)) {
            tt.append(" (").append(name).append(")");
        }
        // The next line is a temporary development aid, that can be removed
        // later.
        /*
         * tt.append(" <small>(") .append(model.getX()) .append(",")
         * .append(model.getY()) .append(")</small>");
         */
        tt.append("<br><b>Tile</b>: ").append(currentTile.getId());
        // TEMPORARY
        tt.append("<small> rot=" + currentTileOrientation + "</small>");
        if (currentTile.hasStations()) {
            // for (Station st : currentTile.getStations())
            Station st;
            int cityNumber;
            for (City city : model.getCities()) {
                cityNumber = city.getNumber();
                st = city.getRelatedStation();
                tt.append("<br>  ").append(st.getType()).append(" ").append(
                        cityNumber) // .append("/").append(st.getNumber())
                .append(" (").append(model.getConnectionString(cityNumber)).append(
                        "): value ");
                if (model.hasOffBoardValues()) {
                    tt.append(model.getCurrentOffBoardValue(hexMap.getPhase())).append(" [");
                    int[] values = model.getOffBoardValues();
                    for (int i = 0; i < values.length; i++) {
                        if (i > 0) tt.append(",");
                        tt.append(values[i]);
                    }
                    tt.append("]");
                } else {
                    tt.append(st.getValue());
                }
                if (st.getBaseSlots() > 0) {
                    tt.append(", ").append(st.getBaseSlots()).append(" slots");
                    List<TokenI> tokens = model.getTokens(cityNumber);
                    if (tokens.size() > 0) {
                        tt.append(" (");
                        int oldsize = tt.length();
                        for (TokenI token : tokens) {
                            if (tt.length() > oldsize) tt.append(",");
                            tt.append(token.getName());
                        }
                        tt.append(")");
                    }
                }
                // TEMPORARY
                tt.append(" <small>pos=" + st.getPosition() + "</small>");
            }
        }
        String upgrades = currentTile.getUpgradesString(model);
        if (upgrades.equals("")) {
            tt.append("<br>No upgrades");
        } else {
            tt.append("<br><b>Upgrades</b>: ").append(upgrades);
            if (model.getTileCost() > 0)
                tt.append("<br>Upgrade cost: "
                          + Bank.format(model.getTileCost()));
        }

        if (getHexModel().getDestinations() != null) {
            tt.append("<br><b>Destination</b>:");
            for (PublicCompanyI dest : getHexModel().getDestinations()) {
                tt.append(" ");
                tt.append(dest.getName());
            }
        }
        tt.append("</html>");
        toolTip = tt.toString();
    }

    public boolean dropTile(int tileId, boolean upgradeMustConnect) {
        this.upgradeMustConnect = upgradeMustConnect;

        provisionalGUITile = new GUITile(tileId, this);
        /* Check if we can find a valid orientation of this tile */
        if (provisionalGUITile.rotate(0, currentGUITile, upgradeMustConnect)) {
            /* If so, accept it */
            provisionalGUITile.setScale(SELECTED_SCALE);
            toolTip = "Click to rotate";
            return true;
        } else {
            /* If not, refuse it */
            provisionalGUITile = null;
            return false;
        }

    }

    public void removeTile() {
        provisionalGUITile = null;
        setSelected(false);
        setToolTip();
    }

    public boolean canFixTile() {
        return provisionalGUITile != null;
    }

    public TileI getProvisionalTile() {
        return provisionalGUITile.getTile();
    }

    public int getProvisionalTileRotation() {
        return provisionalGUITile.getRotation();
    }

    public void fixTile() {

        setSelected(false);
        setToolTip();
    }

    public void removeToken() {
        provisionalGUIToken = null;
        setSelected(false);
        setToolTip();
    }

    public void fixToken() {
        setSelected(false);
        setToolTip();
    }

    /** Needed to satisfy the ViewObject interface. Currently not used. */
    public void deRegister() {
        if (model != null)
            model.deleteObserver(this);
    }

    public ModelObject getModel() {
        return model;
    }

    // Required to implement Observer pattern.
    // Used by Undo/Redo
    public void update(Observable observable, Object notificationObject) {

        if (notificationObject instanceof String) {
            // The below code so far only deals with tile lay undo/redo.
            // Tokens still to do
            String[] elements = ((String) notificationObject).split("/");
            currentTileId = Integer.parseInt(elements[0]);
            currentTileOrientation = Integer.parseInt(elements[1]);
            currentGUITile = new GUITile(currentTileId, this);
            currentGUITile.setRotation(currentTileOrientation);
            currentTile = currentGUITile.getTile();

            hexMap.repaint(getBounds());

            provisionalGUITile = null;

            //log.debug("GUIHex " + model.getName() + " updated: new tile "
            //          + currentTileId + "/" + currentTileOrientation);

            //if (GameUIManager.instance != null && GameUIManager.instance.orWindow != null) {
            //	GameUIManager.instance.orWindow.updateStatus();
            //}
        } else {
            hexMap.repaint(getBounds());
        }
    }

}