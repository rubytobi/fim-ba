package Entity;

import java.util.Map;
import java.util.UUID;
import java.util.GregorianCalendar;

import Packet.ChangeRequestSchedule;
import Packet.AnswerChangeRequestSchedule;

/**
 * Interface fuer alle Geraete
 *
 */
public interface Device extends Identifiable {
	/**
	 * Gibt den SimpleName der Klasse zurück
	 * 
	 * @return SimpleClassName
	 */
	public String getType();

	int numSlots = 4;

	/**
	 * Ueberprueft, ob die gewuentsche Aenderung des Lastprofils moeglich ist
	 * und sendet eine Bestaetigung bzw. Absage an den Consumer
	 * 
	 * @param cr
	 *            Enthaelt Informationen, wie das Lastprofil geaendert werden
	 *            soll
	 * @return Antwort auf den CR
	 */
	public AnswerChangeRequestSchedule receiveChangeRequestSchedule(ChangeRequestSchedule cr);

	/**
	 * Erzeugt ein Deltalastprofil und sendet es an den Consumer. Methode wird
	 * aufgerufen, wenn eine anderer Wert gemessen wurde, als in der Minute
	 * geplant war.
	 * 
	 * @param timeChanged
	 *            Zeitpunkt, wann die Abweichung gemessen wurde
	 * @param valueChanged
	 *            Wert, der gemessen wurde
	 */
	public void sendDeltaLoadprofile(String timeChanged, double valueChanged);

	/**
	 * Liefert die uuid des Devices
	 * 
	 * @return Uuid des Devices
	 */
	public UUID getUUID();

	public void initialize(Map<String, Object> init);

	/**
	 * Liefert den aktuellen Status des Devices
	 * 
	 * @return Aktueller Status des Devices
	 */
	public DeviceStatus getStatus();

	public void ping();

	/**
	 * Erzeugt einen neuen Fahrplan und das zugehoerige Lastprofil. Das
	 * Lastprofil wird an den Consumer geschickt.
	 */
	public void sendNewLoadprofile();

	/**
	 * Legt den Consumer fuer das Device fest
	 * 
	 * @param consumerUUID
	 *            Uuid des Consumers
	 */
	public void setConsumer(UUID consumerUUID);

	/**
	 * Das Device speichert das Lastprofil und den Fahrplan zur uebergebenen
	 * Zeit als fest ab.
	 * 
	 * @param time
	 *            Zeit, fuer die Lastprofil und Fahrplan bestaetigt werden
	 */
	public void confirmLoadprofile(String time);

	public void receiveAnswerChangeRequest(boolean acceptChange);

}
