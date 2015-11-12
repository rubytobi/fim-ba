package Entity;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

import Packet.AnswerChangeRequestLoadprofile;
import Util.API;
import Util.Log;
import Util.View;
import Event.OffersPriceborderException;

/**
 * Klasse fuer Angebote
 *
 */
public class Offer implements Comparable<Offer>, Cloneable {
	/**
	 * Aggregiertes Lastprofil über alle Lastprofile
	 */
	@JsonView(View.Summary.class)
	private Loadprofile aggLoadprofile;
	
	/**
	 * Speichert die vom Marktplatz bestätigten Anpassungen des Angebots
	 */
	@JsonIgnore
	private double[] confirmedChanges = new double[4];

	/**
	 * Gesamtsumme aller Lastprofile
	 */
	@JsonIgnore
	private Double sumAggLoadprofile;

	@JsonView(View.Summary.class)
	@JsonProperty("authKey")
	private UUID authKey = null;

	/**
	 * Alle beteiligten Lastprofile
	 */
	@JsonView(View.Summary.class)
	private HashMap<UUID, HashMap<UUID, Loadprofile>> allLoadprofiles = new HashMap<UUID, HashMap<UUID, Loadprofile>>();

	/**
	 * Preis, zu dem das aggregierte Lastprofil aktuell an der Börse ist
	 */
	@JsonView(View.Summary.class)
	private double priceSugg;

	/**
	 * Minimaler und maximaler Preis, der für das Offer festgesetzt werden kann
	 */
	private double minPrice, maxPrice;

	/**
	 * Consumer, von dem man das Angebot erhalten hat
	 */
	@JsonView(View.Summary.class)
	private UUID author = null;

	@JsonView(View.Summary.class)
	private UUID uuid = null;

	@JsonView(View.Summary.class)
	private OfferStatus status;

	private int numSlots = 4;

	private Offer() {
		uuid = UUID.randomUUID();
		status = OfferStatus.INITIALIZED;
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

		Log.d(uuid, "Offer(author=" + author.toString() + ",loadprofile=" + loadprofile + ")");

		// Erstellt neues Angebot auf Basis eines Lastprofils
		HashMap<UUID, Loadprofile> loadprofiles = new HashMap<UUID, Loadprofile>();
		loadprofiles.put(loadprofile.getUUID(), loadprofile);
		allLoadprofiles.put(author, loadprofiles);

		this.author = author;
		this.priceSugg = loadprofile.getPriceSugg();
		this.minPrice = loadprofile.getMinPrice();
		this.maxPrice = loadprofile.getMaxPrice();

		calculateAggLoadprofile();

		status = OfferStatus.VALID;
		Log.d(uuid, "-- END Offer(): " + toString());
	}

	public Offer(Offer withPrivileges, HashMap<UUID, AnswerChangeRequestLoadprofile> contributions) {
		this();

		Log.d(uuid, "Offer(withPrivileges=" + withPrivileges.toString() + ",contributions=" + contributions.toString()
				+ ")");

		// füge author respektive neue werte hinzu
		author = withPrivileges.getAuthor();
		Log.d(uuid, "set author [" + author + "]");

		// Lastprofile aus bestehendem Angebot einbeziehen
		for (UUID consumerUUID : withPrivileges.getAllLoadprofiles().keySet()) {
			// Neuer Consumer kommt hinzu
			if (!this.allLoadprofiles.containsKey(consumerUUID)) {
				Log.d(uuid, "new consumer [" + consumerUUID + "] in offer");
				this.allLoadprofiles.put(consumerUUID, new HashMap<UUID, Loadprofile>());
			}

			for (UUID loadprofileUUID : withPrivileges.getAllLoadprofiles().get(consumerUUID).keySet()) {
				if (this.allLoadprofiles.get(consumerUUID).containsKey(loadprofileUUID)) {
					// ein bereits existierendes loadprofile soll
					// hinzugefügt werden???
					Log.d(uuid, "adding an existing loadprofile [" + loadprofileUUID + "] to the offer ["
							+ this.toString() + "]");
					continue;
				}

				Log.d(uuid, "new loadprofile [" + loadprofileUUID + "] for consumer [" + consumerUUID + "] in offer");
				Loadprofile value = withPrivileges.getAllLoadprofiles().get(consumerUUID).get(loadprofileUUID);
				this.allLoadprofiles.get(consumerUUID).put(loadprofileUUID, value);
			}
		}

		minPrice = withPrivileges.getMinPrice();
		maxPrice = withPrivileges.getMaxPrice();
		for (UUID consumer : contributions.keySet()) {
			Loadprofile currentLoadprofile = contributions.get(consumer).getLoadprofile();
			addLoadprofile(consumer, currentLoadprofile);
			if (currentLoadprofile.getMinPrice() > minPrice) {
				minPrice = currentLoadprofile.getMinPrice();
			}
			if (currentLoadprofile.getMaxPrice() < maxPrice) {
				maxPrice = currentLoadprofile.getMaxPrice();
			}

		}

		// Prüfe, dass priceSugg weiterhin innerhalb von min und max liegt und
		// passe es gegebenenfalls an
		if (priceSugg < minPrice) {
			priceSugg = minPrice;
		}
		if (priceSugg > maxPrice) {
			priceSugg = maxPrice;
		}

		calculateAggLoadprofile();

		status = OfferStatus.VALID;
		Log.d(uuid, "-- END Offer(): " + toString());
	}

	/**
	 * Erstellt ein neues Angebot auf Basis von zwei anderen Angebote
	 * 
	 * @param withPrivileges
	 *            Offer Angebot des Authors
	 * @param withoutPrivileges
	 *            Offer zweites Angbeot
	 * @throws OffersPriceborderException
	 *             die Preisgrenzen stimmen nicht überein
	 */
	public Offer(Offer withPrivileges, Offer withoutPrivileges) throws OffersPriceborderException {
		this();

		Log.d(uuid, "Offer(withPrivileges=" + withPrivileges.toString() + ",withoutPrivileges="
				+ withoutPrivileges.toString() + ")");

		double minWithPrivileges = withPrivileges.getMinPrice();
		double maxWithPrivileges = withPrivileges.getMaxPrice();
		double minWithoutPrivileges = withoutPrivileges.getMinPrice();
		double maxWithoutPrivileges = withoutPrivileges.getMaxPrice();

		// Lege Minimum und Maximum des gesamten Angebots fest
		this.minPrice = Math.max(minWithPrivileges, minWithoutPrivileges);
		this.maxPrice = Math.min(maxWithPrivileges, maxWithoutPrivileges);
		if (minPrice > maxPrice) {
			Log.e(uuid, "ABER OffersPriceborderException!");
			// Werfe Exception, dass Angebot nicht erstellt werden kann
			throw new OffersPriceborderException();
		}

		// füge author respektive neue werte hinzu
		this.author = withPrivileges.getAuthor();
		Log.d(uuid, "Setze Author [" + author + "]");

		for (Offer o : new Offer[] { withPrivileges, withoutPrivileges }) {
			// Lastprofile aus bestehendem Angebot einbeziehen
			for (UUID consumerUUID : o.getAllLoadprofiles().keySet()) {
				// Neuer Consumer kommt hinzu
				if (!this.allLoadprofiles.containsKey(consumerUUID)) {
					Log.d(uuid, "new consumer [" + consumerUUID + "] in offer");
					this.allLoadprofiles.put(consumerUUID, new HashMap<UUID, Loadprofile>());
				}

				for (UUID loadprofileUUID : o.getAllLoadprofiles().get(consumerUUID).keySet()) {
					if (this.allLoadprofiles.get(consumerUUID).containsKey(loadprofileUUID)) {
						// ein bereits existierendes loadprofile soll
						// hinzugefügt
						// werden???
						Log.e(uuid, "adding an existing loadprofile [" + loadprofileUUID + "] to the offer ["
								+ this.toString() + "]");
						continue;
					}

					Log.d(uuid,
							"new loadprofile [" + loadprofileUUID + "] for consumer [" + consumerUUID + "] in offer");
					Loadprofile value = o.getAllLoadprofiles().get(consumerUUID).get(loadprofileUUID);

					if (value.getMinPrice() > this.minPrice) {
						this.minPrice = value.getMinPrice();
					}
					if (value.getMaxPrice() < this.maxPrice) {
						this.maxPrice = value.getMaxPrice();
					}

					this.allLoadprofiles.get(consumerUUID).put(loadprofileUUID, value);
				}
			}
		}

		// Berechne neue gewichtete priceSugg
		double priceSuggWithPrivileges = withPrivileges.getPriceSugg();
		double[] valuesWithPrivileges = withPrivileges.getAggLoadprofile().getValues();
		double sumAggLoadprofileWithPrivileges = 0;
		double priceSuggWithoutPrivileges = withoutPrivileges.getPriceSugg();
		double[] valuesWithoutPrivileges = withoutPrivileges.getAggLoadprofile().getValues();
		double sumAggLoadprofileWithoutPrivileges = 0;
		double newPriceSugg;

		for (int i = 0; i < numSlots; i++) {
			sumAggLoadprofileWithPrivileges += Math.abs(valuesWithPrivileges[i]);
			sumAggLoadprofileWithoutPrivileges += Math.abs(valuesWithoutPrivileges[i]);
		}
		double weight = sumAggLoadprofileWithPrivileges / sumAggLoadprofileWithoutPrivileges;

		double weightWithPrivileges = 1 / (weight + 1) * weight;
		double weightWithoutPrivileges = 1 - weightWithPrivileges;

		newPriceSugg = Math.round(100.00 * (weightWithPrivileges * priceSuggWithPrivileges
				+ weightWithoutPrivileges * priceSuggWithoutPrivileges)) / 100.00;

		if (newPriceSugg < this.minPrice) {
			newPriceSugg = this.minPrice;
		} else if (newPriceSugg > this.maxPrice) {
			newPriceSugg = this.maxPrice;
		}
		this.priceSugg = newPriceSugg;

		calculateAggLoadprofile();

		status = OfferStatus.VALID;
		Log.d(uuid, "-- END Offer(): " + toString());
	}

	/**
	 * 
	 */
	public String toString() {
		return "{uuid=" + uuid + ",author=" + author + ",partners=" + allLoadprofiles.keySet() + "countPartner="
				+ allLoadprofiles.size() + "}";
	}

	/**
	 * Liefert die Anzahl der Lastprofile des Angebots
	 * 
	 * @return Anzahl der Lastprofile des Angebots
	 */
	@JsonIgnore
	public int getCount() {
		return allLoadprofiles.size();
	}

	/**
	 * Liefert URL des Angebots
	 * 
	 * @return URL des Angebots als String
	 */
	@JsonIgnore
	public String getLocation() {
		return new API<Void, Void>(Void.class).consumers(author).offers(uuid).toString();
	}

	private void calculateAggLoadprofile() {
		for (UUID consumerUUID : this.allLoadprofiles.keySet()) {
			for (UUID loadprofileUUID : this.allLoadprofiles.get(consumerUUID).keySet()) {
				Loadprofile lp = this.allLoadprofiles.get(consumerUUID).get(loadprofileUUID);
				if (this.aggLoadprofile == null) {
					this.aggLoadprofile = lp;
				} else {
					this.aggLoadprofile = new Loadprofile(this.aggLoadprofile, lp);
				}
			}
		}
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
	public double getPriceSugg() {
		return priceSugg;
	}

	/**
	 * Liefert den von den Devices festgelegten minimalen Preis des Angebots
	 * 
	 * @return Minimaler Preis des Angebots
	 */
	public double getMinPrice() {
		return minPrice;
	}

	/**
	 * Liefert den von den Devices festgelegten maximalen Preis des Angebots
	 * 
	 * @return Maximaler Preis des Angebots
	 */
	public double getMaxPrice() {
		return maxPrice;
	}

	/**
	 * Gibt die Anzahl an Lastprofilen im Angebot zurück
	 * 
	 * @return Anzahl enthaltener Lastprofile
	 */
	@JsonIgnore
	public int getNumLoadprofiles() {
		int i = 0;

		for (UUID consumer : allLoadprofiles.keySet()) {
			i += allLoadprofiles.get(consumer).size();
		}

		return i;
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

	public double getSumAggLoadprofile() {
		if (sumAggLoadprofile == null) {
			sumAggLoadprofile = 0.0;
			for (int i = 0; i < numSlots; i++) {
				sumAggLoadprofile += aggLoadprofile.getValues()[i];
			}
		}

		return sumAggLoadprofile;
	}

	@JsonIgnore
	public boolean isValid() {
		return status == OfferStatus.VALID;
	}

	/**
	 * Liefert den Startzeitpunkt des Angebots
	 * 
	 * @return Startzeitpunkt des Angebots als GregorianCalendar
	 */
	@JsonIgnore
	public GregorianCalendar getDate() {
		return getAggLoadprofile().getDate();
	}

	@JsonIgnore
	public boolean isAuthor(UUID uuid) {
		return getAuthor().equals(uuid);
	}

	@Override
	public int compareTo(Offer offer) {
		double otherSum = Math.abs(offer.getSumAggLoadprofile());
		if (otherSum < Math.abs(getSumAggLoadprofile())) {
			return -1;
		} else if (otherSum == Math.abs(getSumAggLoadprofile())) {
			return 0;
		} else {
			return 1;
		}
	}

	private void addLoadprofile(UUID consumer, Loadprofile loadprofile) {
		if (!allLoadprofiles.containsKey(consumer)) {
			allLoadprofiles.put(consumer, new HashMap<UUID, Loadprofile>());
		}

		allLoadprofiles.get(consumer).put(loadprofile.getUUID(), loadprofile);

		this.aggLoadprofile = null;
		this.sumAggLoadprofile = null;
	}

	public void generateAuthKey() {
		authKey = UUID.randomUUID();
	}

	public void setMinPrice(double newMinPrice) {
		this.minPrice = newMinPrice;
	}

	public void setMaxPrice(double newMaxPrice) {
		this.maxPrice = newMaxPrice;
	}
	
	public void setChanges(double[] changes) {
		if (confirmedChanges == null) {
			confirmedChanges = new double[changes.length];
			for (int i=0; i<changes.length; i++) {
				confirmedChanges[i] = 0;
			}
		}
		else { 
			if (confirmedChanges.length != changes.length) {
				Log.e(uuid, "Änderungen haben nicht die richtige Anzahl an Werten.");
				return;
			}
		}
		
		for (int i=0; i<changes.length; i++) {
			confirmedChanges[i] += changes[i];
		}
	}
	
	public double[] getChanges() {
		return confirmedChanges;
	}
}
