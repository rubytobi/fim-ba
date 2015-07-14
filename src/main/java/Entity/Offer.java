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

public class Offer {
	// Aggregiertes Lastprofil über alle Lastprofile
	@JsonView(View.Summary.class)
	Loadprofile aggLoadprofile;

	@JsonView(View.Summary.class)
	@JsonProperty("authKey")
	UUID authKey = null;

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
		authKey = null;
	}

	public String toString() {
		return "{author=" + author + ",partners=" + allLoadprofiles.keySet() + "countPartner=" + allLoadprofiles.size()
				+ "}";
	}

	public int getCount() {
		return allLoadprofiles.size();
	}

	public Offer(UUID author, Loadprofile loadprofile) {
		this();

		// Erstellt neues Angebot auf Basis eines Lastprofils
		this.aggLoadprofile = loadprofile;
		ArrayList<Loadprofile> list = new ArrayList<Loadprofile>();
		list.add(loadprofile);
		allLoadprofiles.put(author, list);

		this.author = author;
		this.aggPrice = loadprofile.getMinPrice();

		status = OfferStatus.VALID;
	}

	/**
	 * Gibt die Location des Angebots zurück
	 * 
	 * @return Location-String
	 */
	public String getLocation() {
		return new API().consumers(author).offers(uuid).toString();
	}

	public Offer(UUID author, Loadprofile loadprofile, Loadprofile aggLoadprofile, Offer referenzeOffer) {
		this();

		// lastprofile aus bestehendem angebot einbeziehen
		this.allLoadprofiles.putAll(referenzeOffer.getAllLoadprofiles());

		if (allLoadprofiles.get(uuid) == null) {
			ArrayList<Loadprofile> list = new ArrayList<Loadprofile>();
			list.add(loadprofile);

			this.allLoadprofiles.put(author, list);
		} else {
			ArrayList<Loadprofile> list = allLoadprofiles.get(uuid);
			list.add(loadprofile);
		}

		this.author = author;
		this.aggLoadprofile = aggLoadprofile;
		this.aggPrice = aggLoadprofile.getMinPrice();

		this.authKey = UUID.randomUUID();

		status = OfferStatus.VALID;
	}

	public Loadprofile getAggLoadprofile() {
		return aggLoadprofile;
	}

	public Map<UUID, ArrayList<Loadprofile>> getAllLoadprofiles() {
		return allLoadprofiles;
	}

	public UUID getAuthor() {
		return author;
	}

	public double getPrice() {
		return aggPrice;
	}

	public UUID getUUID() {
		return uuid;
	}

	/*
	 * Invalidiert das Angebot
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

	public GregorianCalendar getDate() {
		return this.aggLoadprofile.getDate();
	}

	@JsonIgnore
	public boolean isAuthor(UUID uuid) {
		return this.author.equals(uuid);
	}
}
