package Packet;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

import Entity.Loadprofile;
import Util.View;

public class AnswerChangeRequestLoadprofile {
	/**
	 * UUID des Angebots
	 */
	@JsonView(View.Detail.class)
	private UUID uuidoffer;

	/**
	 * Lastprofil, das alle Änderungen und deren Grenzen enthält
	 */
	@JsonView(View.Detail.class)
	private Loadprofile loadprofile;

	public AnswerChangeRequestLoadprofile() {
		// dummy
	}

	/**
	 * Legt eine neue Antwort auf einen bestehenden ChangeRequest an
	 * 
	 * @param uuid
	 *            UUID des ChangeRequests
	 * @param changes
	 *            Mögliche Änderung des Geräts
	 * @param priceFactor
	 *            Preisänderungsfaktor
	 */
	public AnswerChangeRequestLoadprofile(UUID uuid, Loadprofile loadprofile) {
		this.uuidoffer = uuid;
		this.loadprofile = loadprofile;
	}

	/**
	 * Gibt das geänderte Lastprofil zurück
	 * 
	 * @return geändertes Lastprofil
	 */
	public Loadprofile getLoadprofile() {
		return loadprofile;
	}

	/**
	 * Gibt die UUID des vorhergehenden ChangeRequests zurück
	 * 
	 * @return UUID des ChangeRequests
	 */
	@JsonView(View.Detail.class)
	public UUID getUUIDOffer() {
		return uuidoffer;
	}

	/**
	 * Prüft ob keine Änderung möglich war anhand der changes
	 * 
	 * @return Prüfergebnis
	 */
	@JsonIgnore
	public boolean isZero() {
		double sum = 0;
		double[] changes = loadprofile.getValues();

		for (double d : changes) {
			sum += d;
		}

		if (sum == 0) {
			return true;
		} else {
			return false;
		}
	}

	public String toString() {
		return "AnswerChangeRequestLoadprofile " + loadprofile.toString();
	}
}
