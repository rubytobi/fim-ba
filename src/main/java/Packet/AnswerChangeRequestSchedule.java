package Packet;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class AnswerChangeRequestSchedule {
	/**
	 * UUID des ChangeRequests
	 */
	private UUID uuid;

	/**
	 * Mögliche Änderungen des Geräts
	 */
	private double[] changes;

	/**
	 * Kostenfaktor für die möglichen Änderungen
	 */
	private double priceFactor;

	protected AnswerChangeRequestSchedule() {
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
	public AnswerChangeRequestSchedule(UUID uuid, double[] changes, double priceFactor) {
		this.uuid = uuid;
		this.changes = changes;
		this.priceFactor = priceFactor;
	}

	/**
	 * Gibt die Änderungen zurück
	 * 
	 * @return Änderungen
	 */
	public double[] getChanges() {
		return changes;
	}

	/**
	 * Gibt den Preisänderungsfaktor zurück
	 * 
	 * @return Preisänderungsfaktor
	 */
	public double getPriceFactor() {
		return priceFactor;
	}

	/**
	 * Gibt die UUID des vorhergehenden ChangeRequests zurück
	 * 
	 * @return UUID des ChangeRequests
	 */
	public UUID getUUID() {
		return uuid;
	}

	/**
	 * Prüft ob keine Änderung möglich war anhand der changes
	 * 
	 * @return Prüfergebnis
	 */
	@JsonIgnore
	public boolean isZero() {
		double sum = 0;

		for (double d : changes) {
			sum += d;
		}

		if (sum == 0) {
			return true;
		} else {
			return false;
		}
	}
}
