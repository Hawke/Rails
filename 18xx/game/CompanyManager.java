/* $Header: /Users/blentz/rails_rcs/cvs/18xx/game/Attic/CompanyManager.java,v 1.10 2005/04/29 22:11:10 evos Exp $ *  * Created on 05-Mar-2005 IG Adams * Changes: * 19mar2005 Erik Vos: added CompanyType and split public/private companies. */package game;import java.util.*;import org.w3c.dom.*;import util.XmlUtils;/** * @author iadams *  * First inmplementation of CompanyManager. */public class CompanyManager implements CompanyManagerI, ConfigurableComponentI{   /** A map with all company types, by type name */   private Map mCompanyTypes = new HashMap();   /** A List with all companies */   private List lCompanies = new ArrayList();   /** A List with all private companies */   private List lPrivateCompanies = new ArrayList();   /** A List with all public companies */   private List lPublicCompanies = new ArrayList();   /** A map with all private companies by name */   private Map mPrivateCompanies = new HashMap();   /** A map with all public (i.e. non-private) companies by name */   private Map mPublicCompanies = new HashMap();   /** A map of all type names to lists of companies of that type */   private Map mCompaniesByType = new HashMap();   /*    * NOTES: 1. we don't have a map over all companies, because some games have    * duplicate names, e.g. B&O in 1830. 2. we have both a map and a list of    * private/public companies to preserve configuration sequence while allowing    * direct access.    */   /**    * No-args constructor.    */   public CompanyManager()   {      //Nothing to do here, everything happens when configured.   }   /**    * @see game.ConfigurableComponentI#configureFromXML(org.w3c.dom.Element)    */   public void configureFromXML(Element el) throws ConfigurationException   {      /* Read and configure the company types */      NodeList types = el.getElementsByTagName(CompanyTypeI.ELEMENT_ID);       for (int i = 0; i < types.getLength(); i++)      {         Element compElement = (Element) types.item(i);         NamedNodeMap nnp = compElement.getAttributes();         //Extract the attributes of the Component         String name = XmlUtils.extractStringAttribute(nnp,               CompanyTypeI.NAME_TAG);         if (name == null)         {            throw new ConfigurationException("Unnamed company type found.");         }         String className = XmlUtils.extractStringAttribute(nnp,               CompanyTypeI.CLASS_TAG);         if (className == null)         {            throw new ConfigurationException("Company type " + name                  + " has no class defined.");         }         if (mCompanyTypes.get(name) != null)         {            throw new ConfigurationException("Company type " + name                  + " configured twice");         }                  CompanyTypeI type = new CompanyType(name, className, compElement);         mCompanyTypes.put(name, type);         // Get any default certificate array for public companies         int shareUnit = 10;         NodeList unitElements =  compElement.getElementsByTagName("ShareUnit");         if (unitElements.getLength() > 0) {         	  shareUnit = XmlUtils.extractIntegerAttribute(unitElements.item(0).getAttributes(),         	  			"percentage", 10);         }                  NodeList typeElements = compElement               .getElementsByTagName("Certificate");         if (typeElements.getLength() > 0)         {            int shareTotal = 0;            boolean gotPresident = false;            CertificateI certificate;            for (int j = 0; j < typeElements.getLength(); j++)            {               Element certElement = (Element) typeElements.item(j);               NamedNodeMap nnp2 = certElement.getAttributes();               int shares = XmlUtils.extractIntegerAttribute(nnp2, "shares", 1);               boolean president = "President".equals(XmlUtils                     .extractStringAttribute(nnp2, "type", ""));               int number = XmlUtils.extractIntegerAttribute(nnp2, "number", 1);               if (president)               {                  if (number > 1 || gotPresident)                     throw new ConfigurationException("Company type " + name                           + " cannot have multiple President shares");                  gotPresident = true;               }               for (int k = 0; k < number; k++)               {                  certificate = new Certificate(shares, president);                  type.addCertificate(certificate);                  shareTotal += shares * shareUnit;//System.out.println ("Added "+shares+" shares of "+shareUnit+",makes "+shareTotal);               }            }            if (shareTotal != 100)               throw new ConfigurationException("Company type " + name                     + " total shares is not 100%");         }      }      /* Read and configure the companies */      NodeList children = el.getElementsByTagName(CompanyI.COMPANY_ELEMENT_ID);      for (int i = 0; i < children.getLength(); i++)      {         Element compElement = (Element) children.item(i);         NamedNodeMap nnp = compElement.getAttributes();         //Extract the attributes of the Component         String name = XmlUtils.extractStringAttribute(nnp,               CompanyI.COMPANY_NAME_TAG);         if (name == null)         {            throw new ConfigurationException("Unnamed company found.");         }         String type = XmlUtils.extractStringAttribute(nnp,               CompanyI.COMPANY_TYPE_TAG);         if (type == null)         {            throw new ConfigurationException("Company " + name                  + " has no type defined.");         }         CompanyTypeI cType = (CompanyTypeI) mCompanyTypes.get(type);         if (cType == null)         {            throw new ConfigurationException("Company " + name                  + " has undefined type " + cType);         }         try         {            String className = cType.getClassName();            Company company = (Company) Class.forName(className).newInstance();            company.init(name, cType);            company.configureFromXML(compElement);            /* Add company to the various lists */            lCompanies.add(company);            /* Private or public */            if (company instanceof PrivateCompanyI)            {               mPrivateCompanies.put(name, company);               lPrivateCompanies.add(company);               //bank.getIpo().addPrivate((PrivateCompanyI)company);            }            else if (company instanceof PublicCompanyI)            {               mPublicCompanies.put(name, company);               lPublicCompanies.add(company);               /*                * Add the certificates, if defined with the CompanyType and                * absent in the Company specification                */               if (((PublicCompanyI) company).getCertificates() == null)               {                  List defaultCerts = cType.getDefaultCertificates();                  if (defaultCerts == null)                  {                     throw new ConfigurationException("Company " + name                           + " has no certificates");                  }                  else                  {                     ((PublicCompanyI) company).setCertificates(defaultCerts);                  }               }               // Add the certificates to the IPO               /*                * List certificates = ((PublicCompanyI)                * company).getCertificates(); Iterator it =                * certificates.iterator(); while (it.hasNext()) {                * bank.getIpo().addCertificate((CertificateI)it.next()); }                */            }            /* By type */            if (!mCompaniesByType.containsKey(type))               mCompaniesByType.put(type, new ArrayList());            ((List) mCompaniesByType.get(type)).add(company);         }         catch (Exception e)         {            throw new ConfigurationException("Class " + cType.getClassName()                  + " cannot be instantiated", e);         }      }            // Release all held company type DOM elements      Iterator it = mCompanyTypes.keySet().iterator();      while (it.hasNext()) {      	((CompanyTypeI)mCompanyTypes.get((String)it.next())).releaseDomElement();      }   }   /**    * @see game.CompanyManagerI#getCompany(java.lang.String)    *      */   public PrivateCompanyI getPrivateCompany(String name)   {      return (PrivateCompanyI) mPrivateCompanies.get(name);   }   public PublicCompanyI getPublicCompany(String name)   {      return (PublicCompanyI) mPublicCompanies.get(name);   }   /**    * @see game.CompanyManagerI#getAllNames()    */   public List getAllPrivateNames()   {      return new ArrayList(mPrivateCompanies.keySet());   }   public List getAllPublicNames()   {      return new ArrayList(mPublicCompanies.keySet());   }   /**    * @see game.CompanyManagerI#getAllCompanies()    */   public List getAllCompanies()   {      return (List) lCompanies;   }   public List getAllPrivateCompanies()   {      return (List) lPrivateCompanies;   }   public List getAllPublicCompanies()   {      return (List) lPublicCompanies;   }   public List getCompaniesByType(String type)   {      return (List) mCompaniesByType.get(type);   }   public PublicCompanyI getCompanyByName(String name)   {      for(int i=0; i < lPublicCompanies.size(); i++)      {         PublicCompany co = (PublicCompany) lPublicCompanies.get(i);         if(name.equalsIgnoreCase(co.getName()))         {            return (PublicCompanyI) lPublicCompanies.get(i);         }      }            return null;   }}