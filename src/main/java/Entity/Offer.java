package Entity;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonView;

import Util.API;
import Util.OfferStatus;
import start.Loadprofile;
import start.View;

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

	public String toString() {
		return "{countPartner=" + allLoadprofiles.size() + ",partners=" + allLoadprofiles.keySet() + ",author=" + author
				+ "}";
	}

	public int getCount() {
		return allLoadprofiles.size();
	}

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

	public String getLocation() {
		return new API().consumers(author).offers(uuid).toString();
	}

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

	public void invalidate() {
		status = OfferStatus.INVALID;
	}

	public Map<String, Object> status() {
		Map<String, Object> map = new TreeMap<String, Object>();

		map.put("uuid", uuid);
		map.put("status", status.name());
		map.put("price", aggPrice);
		map.put("numberOfConsumers", allLoadprofiles.keySet().size());

		return map;
	}

	public UUID getKey() {
		return key;
	}

	public boolean isValid() {
		return status == OfferStatus.VALID;
	}
}
