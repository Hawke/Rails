package rails.game;


import java.util.*;

import rails.game.action.BuyCertificate;
import rails.game.action.NullAction;
import rails.game.action.PossibleAction;
import rails.game.action.SellShares;
import rails.game.action.StartCompany;
import rails.game.action.UseSpecialProperty;
import rails.game.move.DoubleMapChange;
import rails.game.move.MoveSet;
import rails.game.special.ExchangeForShare;
import rails.game.special.SpecialProperty;
import rails.game.special.SpecialPropertyI;
import rails.game.state.BooleanState;
import rails.game.state.IntegerState;
import rails.game.state.State;
import rails.util.LocalText;

/**
 * Implements a basic Stock Round.
 * <p>
 * A new instance must be created for each new Stock Round. At the end of a
 * round, the current instance should be discarded.
 * <p>
 * Permanent memory is formed by static attributes (like who has the Priority
 * Deal).
 */
public class StockRound extends Round
{

	/* Transient memory (per round only) */
	protected static int numberOfPlayers;
	protected Player currentPlayer;

	//protected PublicCompanyI companyBoughtThisTurn = null;
	protected State companyBoughtThisTurnWrapper = 
	    new State ("CompanyBoughtThisTurn", PublicCompany.class);
	
	protected BooleanState hasSoldThisTurnBeforeBuying = 
		new BooleanState ("HoldSoldBeforeBuyingThisTurn", false);
	
	protected BooleanState hasActed = 
		new BooleanState ("HasActed", false); // Is set true on any player action
	
	protected IntegerState numPasses 
		= new IntegerState("StockRoundPasses");

	protected Map<String, StockSpaceI> sellPrices 
		= new HashMap<String, StockSpaceI>();

	/* Transient data needed for rule enforcing */
	/** HashMap per player containing a HashMap per company */
	protected HashMap<Player, HashMap<PublicCompanyI, Object>> playersThatSoldThisRound 
		= new HashMap<Player, HashMap<PublicCompanyI, Object>>();
	/** HashMap per player */
	// Not used (yet?)
	protected HashMap playersThatBoughtThisRound = new HashMap();

	/* Rule constants */
	static protected final int SELL_BUY_SELL = 0;
	static protected final int SELL_BUY = 1;
	static protected final int SELL_BUY_OR_BUY_SELL = 2;

	/* Permanent memory */
    static IntegerState stockRoundNumber = new IntegerState ("StockRoundNumber", 0);
	static protected StockMarketI stockMarket;
	static protected Portfolio ipo;
	static protected Portfolio pool;
	static protected CompanyManagerI companyMgr;
	static protected GameManager gameMgr;

	/* Rules */
	static protected int sequenceRule = SELL_BUY_SELL; // Currently fixed
	static protected boolean buySellInSameRound = true;
	static protected boolean noSaleInFirstSR = false;
	static protected boolean noSaleIfNotOperated = false;

	/**
	 * The constructor.
	 */
	public StockRound()
	{

		if (numberOfPlayers == 0)
			numberOfPlayers = GameManager.getPlayers().size();
		if (gameMgr == null)
			gameMgr = GameManager.getInstance();
		if (stockMarket == null)
			stockMarket = StockMarket.getInstance();
		if (ipo == null)
			ipo = Bank.getIpo();
		if (pool == null)
			pool = Bank.getPool();
		if (companyMgr == null)
			companyMgr = Game.getCompanyManager();
		GameManager.getInstance().setRound(this);
	}

	public void start()
	{
		stockRoundNumber.add (1);

		ReportBuffer.add("\n" + LocalText.getText("StartStockRound")
				+ stockRoundNumber.intValue());

		GameManager.setCurrentPlayerIndex(GameManager.getPriorityPlayer().getIndex());

		initPlayer();
		ReportBuffer.add(LocalText.getText("HasPriority", new String[] {
		        currentPlayer.getName()
		        }));
	}

	/*----- General methods -----*/

	public int getStockRoundNumber()
	{
		return stockRoundNumber.intValue();
	}

	public static int getLastStockRoundNumber()
	{
		return stockRoundNumber.intValue();
	}
	
	public boolean setPossibleActions() {
		
		boolean passAllowed = true;
        
		possibleActions.clear();
		
		setBuyableCerts();
		
		setSellableShares();
        
        setSpecialActions();
		
		if (passAllowed) {
			if (hasActed.booleanValue()) {
				possibleActions.add (new NullAction (NullAction.DONE));
			} else {
				possibleActions.add (new NullAction (NullAction.PASS));
			}
		}
		
		for (PossibleAction pa : possibleActions.getList()) {
			log.debug(currentPlayer.getName()+ " may: "+pa.toString());
		}
        
        return true;
	}
	
	/**
	 * Create a list of certificates that a player may buy in a Stock Round,
	 * taking all rules into account.
	 * 
	 * @return List of buyable certificates.
	 */
	public void setBuyableCerts()
	{
		if (!mayCurrentPlayerBuyAnything())
			return;

		List<PublicCertificateI> certs;
		PublicCertificateI cert;
		PublicCompanyI comp;
		StockSpaceI stockSpace;
		Portfolio from;
		int price;
		int number;

		int playerCash = currentPlayer.getCash();

		/* Get the next available IPO certificates */
		// Never buy more than one from the IPO
		PublicCompanyI companyBoughtThisTurn = 
			(PublicCompanyI) companyBoughtThisTurnWrapper.getObject();
		if (companyBoughtThisTurn == null)
		{
			from = Bank.getIpo();
			Map<String, List<PublicCertificateI>> map 
				= from.getCertsPerCompanyMap();
			int shares;

			for (String compName : map.keySet())
			{
				certs = map.get(compName);
				if (certs == null || certs.isEmpty())
					continue;
				/* Only the top certificate is buyable from the IPO */
				cert = certs.get(0);
				comp = cert.getCompany();
				if (isSaleRecorded(currentPlayer, comp))
					continue;
				if (currentPlayer.maxAllowedNumberOfSharesToBuy
						(comp, cert.getShare()) < 1)
					continue;
				shares = cert.getShares();

				if (!comp.hasStarted())
				{
					List<Integer> startPrices = new ArrayList<Integer>();
					for (int startPrice : stockMarket.getStartPrices())
					{
						if (startPrice * shares <= playerCash)
						{
							startPrices.add(startPrice);
						}
					}
					if (startPrices.size() > 0) {
						int[] prices = new int[startPrices.size()];
						for (int i=0; i<prices.length; i++) {
							prices[i] = startPrices.get(i);
						}
						possibleActions.add(new StartCompany (cert, prices));
					}
				}
				else if (comp.hasParPrice())
				{
					price = comp.getParPrice().getPrice() * cert.getShares();
					if (price <= playerCash) {
						possibleActions.add (new BuyCertificate (cert, from, price));
					}
				}
				else if (cert.getCertificatePrice() <= playerCash) {
						possibleActions.add(new BuyCertificate (cert, from));
				}

			}
		}

		/* Get the unique Pool certificates and check which ones can be bought */
		from = Bank.getPool();
		Map<String, List<PublicCertificateI>> map 
			= from.getCertsPerCompanyMap();

		for (String compName : map.keySet())
		{
			certs = map.get(compName);
			if (certs == null || certs.isEmpty())
				continue;
			number = certs.size();
			cert = certs.get(0);
			comp = cert.getCompany();
			if (isSaleRecorded(currentPlayer, comp))
				continue;
			if (currentPlayer.maxAllowedNumberOfSharesToBuy
					(comp, cert.getShare()) < 1)
				continue;
			stockSpace = comp.getCurrentPrice(); 
			price = stockSpace.getPrice();
			
			if (isSaleRecorded(currentPlayer, comp))
				continue;
			if (companyBoughtThisTurn != null)
			{
				// If a cert was bought before, only brown zone ones can be
				// bought again in the same turn
				if (comp != companyBoughtThisTurn)
					continue;
				if (!stockSpace.isNoBuyLimit())
					continue;
			}
			/* Only certs in the brown zone may be bought all at once */ 
			if (!stockSpace.isNoBuyLimit()) {
				number = 1;
				/* Would the player exceed the per-company share hold limit? */
				if (!currentPlayer.mayBuyCompanyShare(comp, number))
					continue;
				
				/* Would the player exceed the total certificate limit? */
				if (!stockSpace.isNoCertLimit() 
						&& !currentPlayer.mayBuyCertificate(comp, number))
				continue;
			}
			
			// Does the player have enough cash?
			while (number > 0 && playerCash < number * price) number--;

			if (number > 0) {
				possibleActions.add(new BuyCertificate (cert, from, price, number));
			}
		}
	}

	/**
	 * Create a list of certificates that a player may sell in a Stock Round,
	 * taking all rules taken into account.
	 * 
	 * @return List of sellable certificates.
	 */
	public void setSellableShares()
	{
		if (!mayCurrentPlayerSellAnything())
			return;

		String compName;
		int price;
		int number;
		int share, maxShareToSell;
		boolean dumpAllowed;
		Portfolio playerPortfolio = currentPlayer.getPortfolio();
		
		/* First check of which companies the player owns stock,
		 * and what maximum percentage he is allowed to sell.
		 */ 
		for (PublicCompanyI company : companyMgr.getAllPublicCompanies()) {
			
			// Can't sell shares that have no price
			if (!company.hasStarted()) continue;
			
			share = maxShareToSell = playerPortfolio.getShare(company);
			if (maxShareToSell == 0) continue;

			/* May not sell more than the Pool can accept */
			maxShareToSell = Math.min(maxShareToSell, Bank.getPoolShareLimit() - pool.getShare(company));
			if (maxShareToSell == 0) continue;
			
			/* If the current Player is president, check if he can dump
			 * the presidency onto someone else */
			if (company.getPresident() == currentPlayer) {
				int presidentShare = company.getCertificates().get(0).getShare();
				if (maxShareToSell > share - presidentShare) {
					dumpAllowed = false;
					int playerShare;
					for (Player player : GameManager.getPlayers())
					{
						if (player == currentPlayer) continue;
						playerShare = player.getPortfolio().getShare(company);
						if (playerShare	>= presidentShare)
						{
							dumpAllowed = true;
							break;
						}
					}
					if (!dumpAllowed) maxShareToSell = share - presidentShare;
				}
			}
			
			/* Check what share units the player actually owns.
			 * In some games (e.g. 1835) companies may have different 
			 * ordinary shares: 5% and 10%, or 10% and 20%.
			 * The president's share counts as a multiple of the lowest
			 * ordinary share unit type.
			 */
			// Take care for max. 4 share units per share
			int[] shareCountPerUnit = new int[5]; 
			compName = company.getName();
			for (PublicCertificateI c : playerPortfolio.getCertificatesPerCompany(compName)) {
				if (c.isPresidentShare()) {
					shareCountPerUnit[1] += c.getShares();
				} else {
					++shareCountPerUnit[c.getShares()];
				}
			}
			// TODO The above ignores that a dumped player must be
			// able to exchange the president's share.
			
			/* Check the price.
			 * If a cert was sold before this turn,
			 * the original price is still valid */
			if (sellPrices.containsKey(compName))
			{
				price = ((StockSpaceI) sellPrices.get(compName)).getPrice();
			} else {
				price = company.getCurrentPrice().getPrice();
			}

			
			for (int i=1; i<=4; i++) {
				number = shareCountPerUnit[i];
				if (number == 0) continue;
				number = Math.min (number, 
						maxShareToSell / (i * company.getShareUnit()));
				if (number == 0) continue;

				possibleActions.add (new SellShares (compName,
						i, number, price));
				
			}
		}
	}
    
    protected void setSpecialActions () {
        
        List<SpecialProperty> sps = currentPlayer.getPortfolio()
                .getSpecialProperties(SpecialProperty.class, false);
        for (SpecialPropertyI sp : sps) {
            possibleActions.add(new UseSpecialProperty (sp));
        }
    }
	
	/*----- METHODS THAT PROCESS PLAYER ACTIONS -----*/

	public boolean process (PossibleAction action) {
		
		boolean result = false;
		String playerName = action.getPlayerName();
		currentPlayer = GameManager.getCurrentPlayer();
		
		if (action instanceof NullAction) {
			
			NullAction nullAction = (NullAction) action;
			switch (nullAction.getMode()) {
			case NullAction.PASS:
			case NullAction.DONE:
				result = done (playerName);
				break;
			}
		
		} else if (action instanceof StartCompany) {
			
			StartCompany startCompanyAction = (StartCompany) action;
			
				result = startCompany (playerName, 
						startCompanyAction.getCertificate().getCompany().getName(),
						startCompanyAction.getPrice());
				
		} else if (action instanceof BuyCertificate) {
			
			BuyCertificate buyAction = (BuyCertificate) action;
			result = buyShare (playerName,
					buyAction.getCertificate().getPortfolio(),
					buyAction.getCertificate().getCompany().getName(),
					buyAction.getCertificate().getShares(),
					1);
			
		} else if (action instanceof SellShares) {
			
			result = sellShares ((SellShares)action);
            
        } else if (action instanceof UseSpecialProperty) {
            
            result = useSpecialProperty ((UseSpecialProperty)action);
			
		} else {
		
			DisplayBuffer.add (LocalText.getText("UnexpectedAction",
				action.toString()));
		}
		
        return result;
	}
	/**
	 * Start a company by buying the President's share only
	 * 
	 * @param company
	 *            The company to start.
	 * @return True if the company could be started.
	 */
	public boolean startCompany(String playerName, String companyName, int price)
	{
		return startCompany(playerName, companyName, price, 1);
	}

	/**
	 * Start a company by buying one or more shares (more applies to e.g. 1841)
	 * 
	 * @param player
	 *            The player that wants to start a company.
	 * @param company
	 *            The company to start.
	 * @param price
	 *            The start (par) price (ignored if the price is fixed).
	 * @param shares
	 *            The number of shares to buy (can be more than 1 in e.g. 1841).
	 * @return True if the company could be started. False indicates an error.
	 */
	public boolean startCompany(String playerName, String companyName,
			int price, int shares)
	{

		String errMsg = null;
		StockSpaceI startSpace = null;
		int numberOfCertsToBuy = 0;
		PublicCertificateI cert = null;
		PublicCompanyI company = null;

		currentPlayer = GameManager.getCurrentPlayer();

		// Dummy loop to allow a quick jump out
		while (true)
		{

			// Check everything
			// Only the player that has the turn may buy
			if (!playerName.equals(currentPlayer.getName()))
			{
				errMsg = LocalText.getText("WrongPlayer", playerName);
				break;
			}

			// The player may not have bought this turn.
			if (companyBoughtThisTurnWrapper.getObject() != null)
			{
				errMsg = LocalText.getText("AlreadyBought", playerName);
				break;
			}

			// Check company
			company = companyMgr.getPublicCompany(companyName);
			if (company == null)
			{
				errMsg = LocalText.getText("CompanyDoesNotExist", companyName);
				break;
			}
			// The company may not have started yet.
			if (company.hasStarted())
			{
				errMsg = LocalText.getText("CompanyAlreadyStarted", companyName);
				break;
			}

			// Find the President's certificate
			cert = ipo.findCertificate(company, true);
			// Make sure that we buy at least one!
			if (shares < cert.getShares())
				shares = cert.getShares();

			// Determine the number of Certificates to buy
			// (shortcut: assume that any additional certs are one share each)
			numberOfCertsToBuy = shares - (cert.getShares() - 1);
			// Check if the player may buy that many certificates.
			if (!currentPlayer.mayBuyCertificate(company, numberOfCertsToBuy))
			{
				errMsg = LocalText.getText("CantBuyMoreCerts");
				break;
			}

			// Check if the company has a fixed par price (1835).
			startSpace = company.getParPrice();
			if (startSpace != null)
			{
				// If so, it overrides whatever is given.
				price = startSpace.getPrice();
			}
			else
			{
				// Else the given price must be a valid start price
				if ((startSpace = stockMarket.getStartSpace(price)) == null)
				{
					errMsg = LocalText.getText("InvalidStartPrice", new String[] {
							Bank.format(price),
							company.getName()
					});
					break;
				}
			}

			// Check if the Player has the money.
			if (currentPlayer.getCash() < shares * price)
			{
				errMsg = LocalText.getText("NoMoney");
				break;
			}

			break;
		}

		if (errMsg != null)
		{
			DisplayBuffer.add(LocalText.getText("CantStart", new String[] {
			        playerName,
			        companyName,
			        Bank.format(price),
			        errMsg}));
			return false;
		}
		
		MoveSet.start(true);

		// All is OK, now start the company
		company.start(startSpace);

		// Transfer the President's certificate
		currentPlayer.getPortfolio().buyCertificate(cert,
				ipo,
				cert.getCertificatePrice());

		// If more than one certificate is bought at the same time, transfer
		// these too.
		for (int i = 1; i < numberOfCertsToBuy; i++)
		{
			cert = ipo.findCertificate(company, false);
			currentPlayer.getPortfolio().buyCertificate(cert,
					ipo,
					cert.getCertificatePrice());
		}

		ReportBuffer.add(LocalText.getText ("START_COMPANY_LOG", new String[] {
		        playerName,
		        companyName,
		        String.valueOf(price),
		        String.valueOf(shares),
		        String.valueOf(cert.getShare()),
		        Bank.format (shares * price)
		        }));

		company.checkFlotation();

		//companyBoughtThisTurn = company;
		companyBoughtThisTurnWrapper.set((Object)company);
		hasActed.set (true);
		setPriority();

		return true;
	}

	/**
	 * Buy one or more single-share certificates (more is sometimes possible)
	 * 
	 * @param player
	 *            The player buying shares.
	 * @param portfolio
	 *            The portfolio from which to buy shares.
	 * @param company
	 *            The company of which to buy shares.
	 * @param shares
	 *            The number of shares to buy.
	 * @return True if the certificates bould be bought. False indicates an
	 *         error.
	 */
	public boolean buyShare(String playerName, Portfolio from,
			String companyName, int shares)
	{
		return buyShare(playerName, from, companyName, shares, 1);
	}

	/**
	 * Buying one or more single or double-share certificates (more is sometimes
	 * possible)
	 * 
	 * @param player
	 *            The player that wants to buy shares.
	 * @param portfolio
	 *            The portfolio from which to buy shares.
	 * @param company
	 *            The company of which to buy shares.
	 * @param shares
	 *            The number of shares to buy.
	 * @param unit
	 *            The number of share units in each certificate to buy (e.g.
	 *            value is 2 for 20% Badische or 10% Preussische non-president
	 *            certificates in 1835).
	 * @return True if the certificates could be bought. False indicates an
	 *         error. TODO Usage of 'unit' argument.
	 */
	public boolean buyShare(String playerName, Portfolio from,
			String companyName, int shares, int unit)
	{

		String errMsg = null;
		int price = 0;
		PublicCompanyI company = null;

		currentPlayer = GameManager.getCurrentPlayer();

		// Dummy loop to allow a quick jump out
		while (true)
		{

			// Check everything
			// Only the player that has the turn may buy
			if (!playerName.equals(currentPlayer.getName()))
			{
				errMsg = LocalText.getText("WrongPlayer", playerName);
				break;
			}

			// Check company
			company = companyMgr.getPublicCompany(companyName);
			if (company == null)
			{
				errMsg = LocalText.getText("CompanyDoesNotExist", companyName);
				break;
			}

			// The player may not have sold the company this round.
			if (isSaleRecorded(currentPlayer, company))
			{
				errMsg = LocalText.getText("AlreadySoldThisTurn", new String[] {
						        currentPlayer.getName(),
						        companyName});
				break;
			}

			// The company must have started before
			if (!company.hasStarted())
			{
				errMsg =  LocalText.getText("NotYetStarted", companyName);
				break;
			}

			// The player may not have bought this turn, unless the company
			// bought before and now is in the brown area.
			PublicCompanyI companyBoughtThisTurn 
				= (PublicCompanyI) companyBoughtThisTurnWrapper.getObject();
			if (companyBoughtThisTurn != null
					&& (companyBoughtThisTurn != company || !company.getCurrentPrice()
							.isNoBuyLimit()))
			{
				errMsg =  LocalText.getText("AlreadyBought", playerName);
				break;
			}

			// Check if that many shares are available
			if (shares > from.getShare(company))
			{
				errMsg = LocalText.getText("NotAvailable", new String[] {
						        companyName,
						        from.getName()});
				break;
			}

			StockSpaceI currentSpace;
			if (from == ipo && company.hasParPrice())
			{
				currentSpace = company.getParPrice();
			}
			else
			{
				currentSpace = company.getCurrentPrice();
			}

			// Check if it is allowed to buy more than one certificate (if
			// requested)
			if (shares > 1 && !currentSpace.isNoBuyLimit())
			{
				errMsg = LocalText.getText("CantBuyMoreThanOne", companyName);
				break;
			}

			// Check if player would not exceed the certificate limit.
			// (shortcut: assume 1 cert == 1 certificate)
			if (!currentSpace.isNoCertLimit()
					&& !currentPlayer.mayBuyCertificate(company, shares))
			{
				errMsg = currentPlayer.getName()
						+ LocalText.getText("WouldExceedCertLimit", 
								String.valueOf(Player.getCertLimit()));
				break;
			}

			// Check if player would exceed the per-company share limit
			if (!currentSpace.isNoHoldLimit()
					&& !currentPlayer.mayBuyCompanyShare(company, shares))
			{
				errMsg = currentPlayer.getName()
						+ LocalText.getText("WouldExceedHoldLimit");
				break;
			}

			price = currentSpace.getPrice();

			// Check if the Player has the money.
			if (currentPlayer.getCash() < shares * price)
			{
				errMsg = LocalText.getText("NoMoney");
				break;
			}

			break;
		}

		if (errMsg != null)
		{
			DisplayBuffer.add(LocalText.getText("CantBuy", new String[] {
					playerName,
					String.valueOf(shares),
					companyName,
					from.getName(),
					errMsg
					}));
			return false;
		}

		// All seems OK, now buy the shares.
		MoveSet.start(true);
		PublicCertificateI cert;
		for (int i = 0; i < shares; i++)
		{
			cert = from.findCertificate(company, false);
			ReportBuffer.add(LocalText.getText("BUY_SHARES_LOG", new String[] {
			        playerName,
			        String.valueOf(shares),
			        String.valueOf(cert.getShare()),
			        companyName,
			        from.getName(),
			        Bank.format(shares * price)}));
			currentPlayer.buy(cert, price * cert.getShares());
		}

		companyBoughtThisTurnWrapper.set (company);
		hasActed.set(true);
		setPriority();

		// Check if the company has floated
		if (from == ipo)
			company.checkFlotation();

		return true;
	}

	private void recordSale(Player player, PublicCompanyI company)
	{
	    new DoubleMapChange<Player, PublicCompanyI, Object> 
            (playersThatSoldThisRound, player, company, null);
	}

	private boolean isSaleRecorded(Player player, PublicCompanyI company)
	{
		return playersThatSoldThisRound.containsKey(currentPlayer)
				&& playersThatSoldThisRound.get(currentPlayer).containsKey(company);
	}

	public boolean sellShares (SellShares action)
	// NOTE: Don't forget to keep ShareSellingRound.sellShares() in sync
	{

		Portfolio portfolio = currentPlayer.getPortfolio();
		String playerName = currentPlayer.getName();
		String errMsg = null;
		String companyName = action.getCompanyName();
		PublicCompanyI company = companyMgr.getPublicCompany(action.getCompanyName());
		PublicCertificateI cert = null;
		PublicCertificateI presCert = null;
		List<PublicCertificateI> certsToSell 
			= new ArrayList<PublicCertificateI>();
		Player dumpedPlayer = null;
		int presSharesToSell = 0;
		int numberToSell = action.getNumberSold();
		int shareUnits = action.getShareUnits();
		int currentIndex = GameManager.getCurrentPlayerIndex();

		// Dummy loop to allow a quick jump out
		while (true)
		{

			// Check everything
			if (stockRoundNumber.intValue() == 1 && noSaleInFirstSR)
			{
				errMsg = LocalText.getText("FirstSRNoSell");
				break;
			}
			if (numberToSell <= 0)
			{
				errMsg = LocalText.getText("NoSellZero");
				break;
			}

			// May not sell in certain cases
			if (!mayCurrentPlayerSellAnything())
			{
				errMsg = LocalText.getText("SoldEnough");
				break;
			}

			// Check company
			if (company == null)
			{
				errMsg = LocalText.getText("NoCompany");
				break;
			}

			// The player must have the share(s)
			if (portfolio.getShare(company) < numberToSell)
			{
				errMsg = LocalText.getText("NoShareOwned");
				break;
			}

			// The pool may not get over its limit.
			if (pool.getShare(company) + numberToSell * company.getShareUnit() > Bank.getPoolShareLimit())
			{
				errMsg = LocalText.getText("PoolOverHoldLimit");
				break;
			}

			// Find the certificates to sell
			Iterator it = portfolio.getCertificatesPerCompany(companyName)
					.iterator();
			while (numberToSell > 0 && it.hasNext())
			{
				cert = (PublicCertificateI) it.next();
				if (cert.isPresidentShare())
				{
					// Remember the president's certificate in case we need it
					if (cert.isPresidentShare())
						presCert = cert;
					continue;
				}
				else if (shareUnits != cert.getShares())
				{
					// Wrong number of share units
					continue;
				}
				// OK, we will sell this one
				certsToSell.add(cert);
				numberToSell--;
			}
			if (numberToSell == 0)
				presCert = null;

			if (numberToSell > 0 && presCert != null
					&& numberToSell <= presCert.getShares())
			{
				// More to sell and we are President: see if we can dump it.
				Player otherPlayer;
				for (int i = currentIndex + 1; i < currentIndex
						+ numberOfPlayers; i++)
				{
					otherPlayer = GameManager.getPlayer(i);
					if (otherPlayer.getPortfolio().getShare(company) >= presCert.getShare())
					{
						// Check if he has the right kind of share
						if (numberToSell > 1
								|| otherPlayer.getPortfolio()
										.ownsCertificates(company, 1, false) >= 1)
						{
							// The poor sod.
							dumpedPlayer = otherPlayer;
							presSharesToSell = numberToSell;
							numberToSell = 0;
							break;
						}
					}
				}
			}
			// Check if we could sell them all
			if (numberToSell > 0)
			{
				if (presCert != null)
				{
					errMsg = LocalText.getText("NoDumping");
				}
				else
				{
					errMsg = LocalText.getText("NotEnoughShares");
				}
				break;
			}

			break;
		}

		numberToSell = action.getNumberSold();
		if (errMsg != null)
		{
			DisplayBuffer.add(LocalText.getText("CantSell", new String[] {
					playerName,
					String.valueOf(numberToSell),
					companyName,
					errMsg
					}));
			return false;
		}

		// All seems OK, now do the selling.
		StockSpaceI sellPrice;
		int price;

		// Get the sell price (does not change within a turn)
		if (sellPrices.containsKey(companyName))
		{
			price = ((StockSpaceI) sellPrices.get(companyName)).getPrice();
		}
		else
		{
			sellPrice = company.getCurrentPrice();
			price = sellPrice.getPrice();
			sellPrices.put(companyName, sellPrice);
		}

		MoveSet.start(true);

		ReportBuffer.add (LocalText.getText("SELL_SHARES_LOG", new String[]{
		        playerName,
		        String.valueOf(numberToSell),
		        String.valueOf((numberToSell * company.getShareUnit())),
		        companyName,
		        Bank.format(numberToSell * price)}));

		// Check if the presidency has changed
		if (presCert != null && dumpedPlayer != null && presSharesToSell > 0)
		{
			ReportBuffer.add(LocalText.getText("IS_NOW_PRES_OF", new String[] {
					        dumpedPlayer.getName(),
					        companyName
					}));
			// First swap the certificates
			Portfolio dumpedPortfolio = dumpedPlayer.getPortfolio();
			List<PublicCertificateI> swapped = portfolio.swapPresidentCertificate(company,
					dumpedPortfolio);
			for (int i = 0; i < presSharesToSell; i++)
			{
				certsToSell.add(swapped.get(i));
			}
		}

		// Transfer the sold certificates
		Iterator it = certsToSell.iterator();
		while (it.hasNext())
		{
			cert = (PublicCertificateI) it.next();
			if (cert != null)
				pool.buyCertificate(cert, portfolio, cert.getShares() * price);
		}
		stockMarket.sell(company, numberToSell);

		// Check if we still have the presidency
		if (currentPlayer == company.getPresident())
		{
			Player otherPlayer;
			for (int i = currentIndex + 1; i < currentIndex + numberOfPlayers; i++)
			{
				otherPlayer = GameManager.getPlayer(i);
				if (otherPlayer.getPortfolio().getShare(company) > portfolio.getShare(company))
				{
					portfolio.swapPresidentCertificate(company,
							otherPlayer.getPortfolio());
					ReportBuffer.add(LocalText.getText("IS_NOW_PRES_OF", new String[]{
									otherPlayer.getName(),
									company.getName()
							}));
					break;
				}
			}
		}

		// Remember that the player has sold this company this round.
		recordSale(currentPlayer, company);

		if (companyBoughtThisTurnWrapper.getObject() == null)
		    hasSoldThisTurnBeforeBuying.set (true);
		hasActed.set (true);
		setPriority();

		return true;
	}
    
    public boolean useSpecialProperty (UseSpecialProperty action) {
        
        SpecialPropertyI sp = action.getSpecialProperty();
        
        // TODO This should work for all subclasses, but not all have execute() yet.
        if (sp instanceof ExchangeForShare) {
            
            boolean result =((ExchangeForShare)sp).execute();
            if (result) hasActed.set(true);
            return result;
            
        } else {
            return false;
        }
    }
    
	/**
	 * The current Player passes or is done.
	 * 
	 * @param player
	 *            Name of the passing player.
	 * @return False if an error is found.
	 */
	public boolean done(String playerName)
	{

		currentPlayer = GameManager.getCurrentPlayer();

		if (!playerName.equals(currentPlayer.getName()))
		{
			DisplayBuffer.add(LocalText.getText("WrongPlayer", playerName));
			return false;
		}

		MoveSet.start (false);
		
		if (hasActed.booleanValue())
		{
			numPasses.set(0);
		}
		else
		{
			numPasses.add(1);
			ReportBuffer.add(LocalText.getText("PASSES", currentPlayer.getName()));
		}

		if (numPasses.intValue() >= numberOfPlayers)
		{

			ReportBuffer.add(LocalText.getText("END_SR", String.valueOf(stockRoundNumber.intValue())));

			/* Check if any companies are sold out. */
			for (PublicCompanyI company : companyMgr.getAllPublicCompanies())
			{
				if (company.hasStockPrice() && company.isSoldOut())
				{
					StockSpaceI oldSpace = company.getCurrentPrice();
					stockMarket.soldOut(company);
					StockSpaceI newSpace = company.getCurrentPrice();
					if (newSpace != oldSpace) {
						ReportBuffer.add(LocalText.getText("SoldOut", new String[] {
								company.getName(),
								Bank.format(oldSpace.getPrice()),
								oldSpace.getName(),
								Bank.format(newSpace.getPrice()),
								newSpace.getName(),
						}));
					} else {
						ReportBuffer.add(LocalText.getText("SoldOutNoRaise", new String[] {
								company.getName(),
								Bank.format(newSpace.getPrice()),
								newSpace.getName(),
						}));
					}
				}
			}

			// Inform GameManager
			GameManager.getInstance().nextRound(this);

		}
		else
		{

			setNextPlayer();
			sellPrices.clear();

		}
		
		return true;
	}

	/**
	 * Internal method: pass the turn to the next player.
	 */
	protected void setNextPlayer()
	{

		GameManager.setNextPlayer();
		initPlayer();
	}

 	protected void initPlayer()
	{

		currentPlayer = GameManager.getCurrentPlayer();
		companyBoughtThisTurnWrapper.set(null);
		hasSoldThisTurnBeforeBuying.set(false);
		hasActed.set(false);

	}

	/**
	 * Remember the player that has the Priority Deal. <b>Must be called BEFORE
	 * setNextPlayer()!</b>
	 */
	protected void setPriority()
	{
		GameManager.setPriorityPlayer();
	}

	/*----- METHODS TO BE CALLED TO SET UP THE NEXT TURN -----*/

	/**
	 * @return The player that has the Priority Deal.
	 */
	public static Player getPriorityPlayer()
	{
		return GameManager.getPriorityPlayer();
	}

	/**
	 * @return The player that has the turn.
	 */
	public Player getCurrentPlayer()
	{
		return GameManager.getCurrentPlayer();
	}

	/**
	 * @return The index of the player that has the turn.
	 */
	public int getCurrentPlayerIndex()
	{
		return GameManager.getCurrentPlayerIndex();
	}

	/**
	 * Can the current player do any selling?
	 * 
	 * @return True if any selling is allowed.
	 */
	public boolean mayCurrentPlayerSellAnything()
	{
		if (stockRoundNumber.intValue() == 1 && noSaleInFirstSR)
			return false;
		
		if (companyBoughtThisTurnWrapper.getObject() != null
				&& (sequenceRule == SELL_BUY_OR_BUY_SELL
						&& hasSoldThisTurnBeforeBuying.booleanValue()
					|| sequenceRule == SELL_BUY))
			return false;
		return true;
	}

	/**
	 * Can the current player do any buying?
	 * 
	 * @return True if any buying is allowed.
	 */
	 public boolean mayCurrentPlayerBuyAnything() {
		 return companyBoughtThisTurnWrapper.getObject() == null; 
	 }

	public static void setNoSaleInFirstSR()
	{
		noSaleInFirstSR = true;
	}

	public static void setNoSaleIfNotOperated()
	{
		noSaleIfNotOperated = true;
	}

	public String getHelp()
	{
		return LocalText.getText("SRHelpText");
	}
    
    public String toString () {
        return "StockRound "+getStockRoundNumber();
    }
}
