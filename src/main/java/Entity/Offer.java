package Entity;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

import Util.API;
import Util.DeviceStatus;
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
	@JsonProperty("authKey")
	UUID authKey = null;

	// Alle beteiligten Lastprofile
	@JsonView(View.Summary.class)
	HashMap<UUID, HashMap<UUID, Loadprofile>> allLoadprofiles = new HashMap<UUID, HashMap<UUID, Loadprofile>>();

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
		authKey = null;
	}

	/**
	 * 
	 */
	public String toString() {
		return "{author=" + author + ",partners=" + allLoadprofiles.keySet() + "countPartner=" + allLoadprofiles.size()
				+ "}";
	}

	/**
	 * Liefert die Anzahl der Lastprofile des Angebots
	 * 
	 * @return Anzahl der Lastprofile des Angebots
	 */
	public int getCount() {
		return allLoadprofiles.size();
	}

	/**
	 * Erstellt neues Angebot auf Basis eines Lastprofils
	 * 
	 * @param author
	 *            UUID des Consumers, der Angebot erstellen will
	 * @param loadprofile
	 *            Lastprofil, aus dem Angebot erstellt werden soll
	 */
	public Offer(UUID author, Loadprofile loadprofile) {
		this();

		// Erstellt neues Angebot auf Basis eines Lastprofils
		this.aggLoadprofile = loadprofile;

		HashMap<UUID, Loadprofile> loadprofiles = new HashMap<UUID, Loadprofile>();
		loadprofiles.put(loadprofile.getUUID(), loadprofile);
		allLoadprofiles.put(author, loadprofiles);

		this.author = author;
		this.aggPrice = loadprofile.getMinPrice();

		status = OfferStatus.VALID;
	}

	/**
	 * Liefert URL des Angebots
	 * 
	 * @return URL des Angebots als String
	 */
	public String getLocation() {
		return new API().consumers(author).offers(uuid).toString();
	}

	/**
	 * Erstellt neues Angebot auf Basis eines alten Angebots
	 * 
	 * @param author
	 *            UUID des Consumers, der neues Angebot erstellen will
	 * @param loadprofile
	 *            Neues Lastprofil, das zu dem alten Angebot hinzugefuegt werden
	 *            soll
	 * @param aggLoadprofile
	 *            Aggregiertes Lastprofil des neuen Angebots
	 * @param referenceOffer
	 *            Altes Angebot
	 */
	public Offer(UUID author, Loadprofile loadprofile, Loadprofile aggLoadprofile, Offer referenceOffer) {
		this();

		// Lastprofile aus bestehendem Angebot einbeziehen
		this.allLoadprofiles.putAll(referenceOffer.getAllLoadprofiles());

		// Neues Lastprofil hinzufügen
		HashMap<UUID, Loadprofile> existingLoadprofiles = allLoadprofiles.get(author);
		if (existingLoadprofiles == null) {
			HashMap<UUID, Loadprofile> loadprofiles = new HashMap<UUID, Loadprofile>();
			loadprofiles.put(loadprofile.getUUID(), loadprofile);
			this.allLoadprofiles.put(author, loadprofiles);
		} else {
			existingLoadprofiles.put(loadprofile.getUUID(), loadprofile);
			this.allLoadprofiles.put(author, existingLoadprofiles);
		}

		this.author = author;
		this.aggLoadprofile = aggLoadprofile;
		this.aggPrice = aggLoadprofile.getMinPrice();

		this.authKey = UUID.randomUUID();

		status = OfferStatus.VALID;
	}

	/**
	 * Liefert das aggregierte Lastprofil des Angebots
	 * 
	 * @return Aggregiertes Lastprofil des Angebots
	 */
	public Loadprofile getAggLoadprofile() {
		return aggLoadprofile;
	}

	/**
	 * Liefert alle am Angebot beteiligten Lastprofile
	 * 
	 * @return Alle am Angebot beteiligten Lastprofile als Map, mit der UUID des
	 *         Consumers als Key und einem Array aller dazugehoerigen
	 *         Lastprofile
	 */
	public HashMap<UUID, HashMap<UUID, Loadprofile>> getAllLoadprofiles() {
		return allLoadprofiles;
	}

	/**
	 * Liefert die UUID des aktuellen Autors des Angebots
	 * 
	 * @return UUID des aktuellen Autors des Angebots
	 */
	public UUID getAuthor() {
		return author;
	}

	/**
	 * Liefert den aktuellen Preis des Angebots
	 * 
	 * @return Aktueller Preis des Angebots
	 */
	public double getPrice() {
		return aggPrice;
	}

	/**
	 * Liefert UUID des Angebots
	 * 
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

	public UUID getAuthKey() {
		return authKey;
	}

	public boolean isValid() {
		return status == OfferStatus.VALID;
	}
	
	/**
	 * Liefert den Startzeitpunkt des Angebots
	 * @return Startzeitpunkt des Angebots als GregorianCalendar
	 */
	public GregorianCalendar getDate() {
		return this.aggLoadprofile.getDate();
	}

	@JsonIgnore
	public boolean isAuthor(UUID uuid) {
		return this.author.equals(uuid);
	}
}
