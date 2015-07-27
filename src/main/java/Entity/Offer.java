package Entity;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

import Util.API;
import Util.DeviceStatus;
import Util.Log;
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
	 * Erstellt ein neues Angebot auf Basis von zwei anderen Angebote
	 * 
	 * @param withPrivileges
	 *            Offer Angebot des Authors
	 * @param withoutPrivileges
	 *            Offer zweites Angbeot
	 */
	public Offer(Offer withPrivileges, Offer withoutPrivileges) {
		this();

		Log.d(uuid, "Offer(withPrivileges=" + withPrivileges.toString() + ",withoutPrivileges="
				+ withoutPrivileges.toString() + ")");

		// füge author respektive neue werte hinzu
		this.author = withPrivileges.getAuthor();
		Log.d(uuid, "set author [" + author + "]");

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
						Log.d(uuid, "adding an existing loadprofile [" + loadprofileUUID + "] to the offer ["
								+ this.toString() + "]");
						continue;
					}

					Log.d(uuid,
							"new loadprofile [" + loadprofileUUID + "] for consumer [" + consumerUUID + "] in offer");
					Loadprofile value = o.getAllLoadprofiles().get(consumerUUID).get(loadprofileUUID);
					this.allLoadprofiles.get(consumerUUID).put(loadprofileUUID, value);
				}
			}
		}

		// generate aggLoadprofile
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

		this.aggPrice = aggLoadprofile.getMinPrice();

		this.authKey = UUID.randomUUID();

		status = OfferStatus.VALID;

		Log.d(uuid, "-- END Offer(): " +

		toString());
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
	 * 
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
