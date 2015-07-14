package start;

import java.util.Map;
import java.util.UUID;
import java.util.GregorianCalendar;

import Packet.ChangeRequest;
import Util.DeviceStatus;

/**
 * Interface fuer alle Verbrauchsgeraete
 *
 */
public interface Device {
	int numSlots = 4;	
	
	/**
	 * Berechnet Lastprofil auf Viertel-Stunden-Basis fuer uebergebenen Minutenfahrplan.
	 * Hierbei werden die Werte pro Viertelstunde aufsummiert und als Lastprofil gespeichert.
	 * @param	schedule	Minuetlicher Fahrplan, fuer den Lastprofil erstellt werden soll
	 * @return	Array mit den Werten des Lastprofils
	 */
	public double[] createValuesLoadprofile(double[] values);

	/**
	 * Erzeugt einen neuen Fahrplan und das zugehoerige Lastprofil.
	 * Das Lastprofil wird an den Consumer geschickt.
	 */
	public void sendNewLoadprofile();
	
	/**
	 * Erzeugt ein Deltalastprofil und sendet es an den Consumer.
	 * Methode wird aufgerufen, wenn eine anderer Wert gemessen wurde, als in der Minute geplant war.
	 * @param	timeChanged		Zeitpunkt, wann die Abweichung gemessen wurde
	 * @param	valueChanged	Wert, der gemessen wurde
	 */
	public void sendDeltaLoadprofile(GregorianCalendar timeChanged, double valueChanged);
	
	/**
	 * Liefert die uuid des Devices
	 * @return Uuid des Devices
	 */
	public UUID getUUID();

	public void initialize(Map<String, Object> init);
	
	/**
	 * Liefert den aktuellen Status des Devices
	 * @return Aktueller Status des Devices
	 */
	public DeviceStatus getStatus();

	public void ping();
	
	/**
	 * Legt den Consumer fuer das Device fest
	 * @param uuid	Uuid des Consumers
	 */
	public void setConsumer(UUID consumerUUID);

	/**
	 * Ueberprueft, ob die gewuentsche Aenderung des Lastprofils moeglich ist
	 * und sendet eine Bestaetigung bzw. Absage an den Consumer
	 * @param cr	Enthaelt Informationen, wie das Lastprofil geaendert werden soll 
	 */
	public void changeLoadprofile(ChangeRequest cr);
	
	/**
	 * Das Device speichert das Lastprofil und den Fahrplan zur uebergebenen Zeit als fest ab.
	 * @param time Zeit, fuer die Lastprofil und Fahrplan bestaetigt werden
	 */
	public void confirmLoadprofile (GregorianCalendar time);
}
