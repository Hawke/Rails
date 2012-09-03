package rails.game;

import rails.game.model.PortfolioModel;
import rails.game.model.PortfolioOwner;

/**
 * BankPortfolios act as Owner of their owns
 * Used for implementation of the separate Bank identities (IPO, POOL, SCRAPHEAP)
 */
public final class BankPortfolio extends RailsAbstractItem implements PortfolioOwner {
    
    private final PortfolioModel portfolio = PortfolioModel.create(this);
    
    protected BankPortfolio(Bank parent, String id) {
        super (parent, id);
    }
    
    /**
     * @param parent restricted to bank
     */
    public static BankPortfolio create(Bank parent, String id) {
        return new BankPortfolio(parent, id);
    }

    @Override
    public Bank getParent() {
        return (Bank)super.getParent();
    }
    
    // Owner methods
    public PortfolioModel getPortfolioModel() {
        return portfolio;
    }

}