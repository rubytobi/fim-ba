package Entity;

import java.util.*;

import Util.OfferStatus;
import start.Loadprofile;

public class Offer {
	// Aggregiertes Lastprofil �ber alle Lastprofile
	Loadprofile loadprofile;
	// Alle beteiligten Lastprofile
	ArrayList<Loadprofile> allLoadprofiles;

	// Preis, zu dem das aggregierte Lastprofil aktuell an der B�rse ist
	double price;

	// Consumer, von dem man das Angebot erhalten hat
	Consumer consumerFrom;
	// Alle beteiligten Consumer
	ArrayList<Consumer> allConsumers;

	private UUID uuid;
	private OfferStatus status;

	private Offer() {
		uuid = UUID.randomUUID();
	}

	public Offer(Loadprofile loadprofile, Consumer consumer, double price) {
		this();

		// Erstellt neues Angebot auf Basis eines Lastprofils
		this.loadprofile = loadprofile;
		allLoadprofiles.add(loadprofile);

		consumerFrom = consumer;
		allConsumers.add(consumer);

		this.price = price;
	}

	public Offer(Loadprofile aggLoadprofile, Loadprofile loadprofile, Consumer consumer, Offer offer, double price) {
		this();

		// Consumer hat aggregiertes Lastprofil f�r neues Lastprofil �bergeben
		this.loadprofile = aggLoadprofile;

		// Zu allen Lastprofilen werden die Lastprofile vom vorherigen Angebot
		// aufgenommen und das Lastprofil des neuen Consumers hinzugef�gt
		this.allLoadprofiles = offer.getAllLoadprofiles();
		this.allLoadprofiles.add(loadprofile);

		// �bergebener Consumer ist neuer Versender des Angebots
		consumerFrom = consumer;
		// Zu allen Consumern werden die Consumer vom vorherigen Angebot
		// aufgenommen und das Lastprofil des neuen Consumers hinzugef�gt
		this.allConsumers = offer.getAllConsumers();
		this.allConsumers.add(consumer);

		this.price = price;
	}

	public Loadprofile getAggLoadprofile() {
		return loadprofile;
	}

	public ArrayList<Loadprofile> getAllLoadprofiles() {
		return allLoadprofiles;
	}

	public Consumer getConsumerFrom() {
		return consumerFrom;
	}

	public ArrayList<Consumer> getAllConsumers() {
		return allConsumers;
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
		map.put("numberOfConsumers", allConsumers.size());

		return map;
	}
}
