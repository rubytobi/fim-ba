package Entity;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonView;

import Util.OfferStatus;
import start.Loadprofile;
import start.View;

public class Offer {
	// Aggregiertes Lastprofil über alle Lastprofile
	@JsonView(View.Summary.class)
	Loadprofile loadprofile;
	// Alle beteiligten Lastprofile
	Map<UUID, Loadprofile> allLoadprofiles = new HashMap<UUID, Loadprofile>();

	// Preis, zu dem das aggregierte Lastprofil aktuell an der B�rse ist
	@JsonView(View.Summary.class)
	double price;

	// Consumer, von dem man das Angebot erhalten hat
	@JsonView(View.Summary.class)
	UUID consumerFrom;

	@JsonView(View.Summary.class)
	private UUID uuid;
	@JsonView(View.Summary.class)
	private OfferStatus status;

	private Offer() {
		uuid = UUID.randomUUID();
	}

	public Offer(Loadprofile loadprofile, Consumer consumer, double price) {
		this();

		// Erstellt neues Angebot auf Basis eines Lastprofils
		this.loadprofile = loadprofile;
		allLoadprofiles.put(consumer.getUUID(), loadprofile);
		consumerFrom = consumer.getUUID();

		this.price = price;
	}

	public Offer(Loadprofile aggLoadprofile, Loadprofile loadprofile, Consumer consumer, Offer offer, double price) {
		this();

		// Consumer hat aggregiertes Lastprofil f�r neues Lastprofil �bergeben
		this.loadprofile = aggLoadprofile;

		// Zu allen Lastprofilen werden die Lastprofile vom vorherigen Angebot
		// aufgenommen und das Lastprofil des neuen Consumers hinzugef�gt
		this.allLoadprofiles = offer.getAllLoadprofiles();
		this.allLoadprofiles.put(consumer.getUUID(), loadprofile);

		// Übergebener Consumer ist neuer Versender des Angebots
		consumerFrom = consumer.getUUID();

		this.price = price;
	}

	public Loadprofile getAggLoadprofile() {
		return loadprofile;
	}

	public Map<UUID, Loadprofile> getAllLoadprofiles() {
		return allLoadprofiles;
	}

	public UUID getConsumerFrom() {
		return consumerFrom;
	}

	public double getPrice() {
		return price;
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
		map.put("price", price);
		map.put("numberOfConsumers", allLoadprofiles.keySet().size());

		return map;
	}
}
