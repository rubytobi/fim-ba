package Entity;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonView;

import Util.OfferStatus;
import start.Loadprofile;
import start.View;

public class Offer {
	// Aggregiertes Lastprofil über alle Lastprofile
	@JsonView(View.Summary.class)
	Loadprofile aggLoadprofile;

	// Alle beteiligten Lastprofile
	@JsonView(View.Summary.class)
	Map<UUID, Loadprofile> allLoadprofiles = new HashMap<UUID, Loadprofile>();

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
	}

	public Offer(UUID author, Loadprofile loadprofile) {
		this();

		// Erstellt neues Angebot auf Basis eines Lastprofils
		this.aggLoadprofile = loadprofile;
		allLoadprofiles.put(author, loadprofile);

		this.author = author;
		this.aggPrice = loadprofile.getMinPrice();

		status = OfferStatus.VALID;
	}

	public Offer(UUID author, Loadprofile loadprofile, Loadprofile aggLoadprofile, Offer referenzeOffer) {
		this();

		// lastprofile aus bestehendem angebot einbeziehen
		this.allLoadprofiles.putAll(referenzeOffer.getAllLoadprofiles());
		this.allLoadprofiles.put(author, loadprofile);

		this.author = author;
		this.aggLoadprofile = aggLoadprofile;
		this.aggPrice = aggLoadprofile.getMinPrice();

		status = OfferStatus.VALID;
	}

	public Loadprofile getAggLoadprofile() {
		return aggLoadprofile;
	}

	public Map<UUID, Loadprofile> getAllLoadprofiles() {
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
}
