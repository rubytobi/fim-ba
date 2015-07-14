package Entity;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonView;

import Util.API;
import Util.OfferStatus;
import start.Loadprofile;
import start.View;

/**
 * Klasse fuer Angebote
 *
 */
public class Offer {
	// Aggregiertes Lastprofil über alle Lastprofile
	@JsonView(View.Summary.class)
	Loadprofile aggLoadprofile;

	@JsonView(View.Summary.class)
	UUID key = null;

	// Alle beteiligten Lastprofile
	@JsonView(View.Summary.class)
	Map<UUID, ArrayList<Loadprofile>> allLoadprofiles = new HashMap<UUID, ArrayList<Loadprofile>>();

	// Preis, zu dem das aggregierte Lastprofil aktuell an der B�rse ist
	@JsonView(View.Summary.class)
	double aggPrice;

	// Consumer, von dem man das Angebot erhalten hat
	@JsonView(View.Summary.class)
	UUID author = null;

	@JsonView(View.Summary.class)
	private UUID uuid = null;

	@JsonView(View.Summary.class)
	private OfferStatus status;

	private Offer() {
		uuid = UUID.randomUUID();
		status = OfferStatus.INITIALIZED;
		key = null;
	}
	
	/**
	 * 
	 */
	public String toString() {
		return "{countPartner=" + allLoadprofiles.size() + ",partners=" + allLoadprofiles.keySet() + ",author=" + author
				+ "}";
	}
	
	/**
	 * Liefert die Anzahl der Lastprofile des Angebots
	 * @return Anzahl der Lastprofile des Angebots
	 */
	public int getCount() {
		return allLoadprofiles.size();
	}
	
	/**
	 * Erstellt neues Angebot auf Basis eines Lastprofils
	 * @param author		UUID des Consumers, der Angebot erstellen will
	 * @param loadprofile	Lastprofil, aus dem Angebot erstellt werden soll
	 */
	public Offer(UUID author, Loadprofile loadprofile) {
		this();

		// Erstellt neues Angebot auf Basis eines Lastprofils
		this.aggLoadprofile = loadprofile;
		
		ArrayList<Loadprofile> loadprofiles = new ArrayList<Loadprofile>();
		loadprofiles.add(loadprofile);
		allLoadprofiles.put(author,  loadprofiles);

		this.author = author;
		this.aggPrice = loadprofile.getMinPrice();

		status = OfferStatus.VALID;
	}
	
	/**
	 * Liefert URL des Angebots
	 * @return URL des Angebots als String
	 */
	public String getLocation() {
		return new API().consumers(author).offers(uuid).toString();
	}
	
	/**
	 * Erstellt neues Angebot auf Basis eines alten Angebots
	 * @param author			UUID des Consumers, der neues Angebot erstellen will
	 * @param loadprofile		Neues Lastprofil, das zu dem alten Angebot hinzugefuegt werden soll
	 * @param aggLoadprofile	Aggregiertes Lastprofil des neuen Angebots
	 * @param referenceOffer	Altes Angebot
	 */
	public Offer(UUID author, Loadprofile loadprofile, Loadprofile aggLoadprofile, Offer referenceOffer) {
		this();

		// Lastprofile aus bestehendem Angebot einbeziehen
		this.allLoadprofiles.putAll(referenceOffer.getAllLoadprofiles());
		
		// Neues Lastprofil hinzufügen
		ArrayList<Loadprofile> existingLoadprofiles = allLoadprofiles.get(author);
		if (existingLoadprofiles == null) {
			ArrayList<Loadprofile> loadprofiles = new ArrayList<Loadprofile>();
			loadprofiles.add(loadprofile);
		}
		else {
			existingLoadprofiles.add(loadprofile);
			this.allLoadprofiles.put(author, existingLoadprofiles);
		}
		
		this.author = author;
		this.aggLoadprofile = aggLoadprofile;
		this.aggPrice = aggLoadprofile.getMinPrice();

		this.key = UUID.randomUUID();

		status = OfferStatus.VALID;
	}
	
	/**
	 * Liefert das aggregierte Lastprofil des Angebots
	 * @return Aggregiertes Lastprofil des Angebots
	 */
	public Loadprofile getAggLoadprofile() {
		return aggLoadprofile;
	}

	/**
	 * Liefert alle am Angebot beteiligten Lastprofile
	 * @return Alle am Angebot beteiligten Lastprofile als Map, mit der UUID des Consumers als Key und einem Array aller dazugehoerigen Lastprofile
	 */
	public Map<UUID, ArrayList<Loadprofile>> getAllLoadprofiles() {
		return allLoadprofiles;
	}
	
	/**
	 * Liefert die UUID des aktuellen Autors des Angebots
	 * @return UUID des aktuellen Autors des Angebots
	 */
	public UUID getAuthor() {
		return author;
	}
	
	/**
	 * Liefert den aktuellen Preis des Angebots
	 * @return Aktueller Preis des Angebots
	 */
	public double getPrice() {
		return aggPrice;
	}
	
	/**
	 * Liefert UUID des Angebots
	 * @return UUID des Angebots
	 */
	public UUID getUUID() {
		return uuid;
	}

	/**
	 * Setzt den Status des Angebots auf INVALID
	 */
	public void invalidate() {
		status = OfferStatus.INVALID;
	}
	
	/**
	 * Liefert den aktuellen Status des Angebots
	 * @return Map mit UUID, Status, Preis und der Anzahl der beteiligten Consumer des Angebots
	 */
	public Map<String, Object> status() {
		Map<String, Object> map = new TreeMap<String, Object>();

		map.put("uuid", uuid);
		map.put("status", status.name());
		map.put("price", aggPrice);
		map.put("numberOfConsumers", allLoadprofiles.keySet().size());

		return map;
	}
	
	/**
	 * 
	 * @return
	 */
	public UUID getKey() {
		return key;
	}
	
	/**
	 * Liefert, ob das Angebot noch gueltig ist
	 * @return false, wenn das Angebot nicht mehr gueltig ist
	 * 			true, wenn das Angebot noch gueltig ist
	 */
	public boolean isValid() {
		return status == OfferStatus.VALID;
	}
}
