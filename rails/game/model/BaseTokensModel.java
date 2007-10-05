/* $Header: /Users/blentz/rails_rcs/cvs/18xx/rails/game/model/BaseTokensModel.java,v 1.3 2007/10/05 22:02:30 evos Exp $*/
package rails.game.model;

import rails.game.PublicCompanyI;

public class BaseTokensModel extends ModelObject
{

	private PublicCompanyI company;

	public BaseTokensModel(PublicCompanyI company)
	{
		this.company = company;
	}

	public String getText()
	{
	    int allTokens = company.getNumberOfBaseTokens();
	    int freeTokens = company.getNumberOfFreeBaseTokens();
	    if (allTokens == 0) {
	        return "";
	    } else if (freeTokens == 0) {
	        return "-/" + allTokens;
	    } else {
	        return freeTokens + "/" + allTokens;
	    }
	}

}
