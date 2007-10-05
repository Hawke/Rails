/* $Header: /Users/blentz/rails_rcs/cvs/18xx/rails/game/model/PriceModel.java,v 1.6 2007/10/05 22:02:30 evos Exp $*/
package rails.game.model;

import rails.game.Bank;
import rails.game.PublicCompanyI;
import rails.game.StockSpaceI;
import rails.game.move.PriceMove;
import rails.game.state.StateI;

public class PriceModel extends ModelObject implements StateI
{

	private StockSpaceI stockPrice = null;
	private PublicCompanyI company = null;
	private String name = null;

	public PriceModel(PublicCompanyI company, String name)
	{
		this.company = company;
		this.name = name;
	}

	public void setPrice(StockSpaceI price)
	{
	    new PriceMove (this, stockPrice, price);
	}

	public StockSpaceI getPrice()
	{
		return stockPrice;
	}
	
	public PublicCompanyI getCompany() {
	    return company;
	}
	
	public String getText()
	{
		if (stockPrice != null)
		{
			return Bank.format(stockPrice.getPrice()) + " ("
					+ stockPrice.getName() + ")";
		}
		return "";
	}
	
	// StateI required methods
	public Object getObject() {
		return stockPrice;
	}

	public void setState(Object object) {
	    if (object == null) {
			stockPrice = null;
			update();
		} else if (object instanceof StockSpaceI) {
		    stockPrice = (StockSpaceI) object;
			update();
		} else {
			new Exception ("Incompatible object type "+object.getClass().getName()
					+ "passed to PriceModel "+name)
				.printStackTrace();
		}
	}

	public String getName() {
	    return name;
	}

}
