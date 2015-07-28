package Entity;

import java.net.URI;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;

import Event.IllegalDeviceState;
import Packet.ChangeRequestSchedule;
import Packet.AnswerChangeRequest;
import Util.API;
import Util.DateTime;
import Util.DeviceStatus;
import Util.SimulationBHKW;
import start.Device;
import start.Loadprofile;


/**
 * Klasse für Blockheizkraftwerke (BHKW)
 * @author Admin
 *
 */
public class BHKW implements Device{
	// Stromkoeffizient: Strom = Koeffizient * Wärme
	private double chpCoefficient;
	
	// Verbrauch für Leistung einer zusätzlichen kWh in l
	private double consFuelPerKWh;

	// UUID des Consumers des BHKW
	private UUID consumerUUID;

	// Maximale Last des BHKW
	private double maxLoad;

	// Preis für 1l Brennstoff
	private double priceFuel;
	
	// Fahrplan in 15-Minuten Rythmus, den der Consumer gerade aushandelt
	// mit Füllstand des Reservoirs (0) und Stromerzeugung (1)
	private double[][] scheduleMinutes;
	
	// Fahrpläne in 15-Minuten Rythmus, die schon ausgehandelt sind und fest stehen
	// mit Füllstand des Reservoirs (0) und Stromerzeugung (1)
	private TreeMap<String, double[][]> schedulesFixed = new TreeMap<String, double[][]>();

	// Simulator für das BHKW
	private SimulationBHKW simulation;

	// Größe des Wärmespeichers
	private double sizeHeatReservoir;

	// Status des BHKW
	private DeviceStatus status;

	private GregorianCalendar timeFixed;
	
	// UUID des BHKW
	private UUID uuid;
	
	private BHKW() {
		status = DeviceStatus.CREATED;
		uuid = UUID.randomUUID();
	}
	
	/**
	 * Erstellt ein neues Blockheizkraftwerk.
	 * @param chpCoefficient	Stromkoeffizient: Strom = Koeffizient * Wärme
	 * @param priceFuel			Preis des Brennstoffs pro Liter
	 * @param consFuelPerKWh	Benötigter Brennstoff in Liter, um eine kWh Strom zu produzieren
	 * @param sizeHeatReservoir	Größe des Wärmespeichers
	 * @param maxLoad			Maximale Last des Blockheizkraftwerks
	 */
	public BHKW (double chpCoefficient, double priceFuel, double consFuelPerKWh, double sizeHeatReservoir,
			double maxLoad) {
		this();
		this.chpCoefficient = chpCoefficient;
		this.priceFuel = priceFuel;
		this.consFuelPerKWh = consFuelPerKWh;
		this.sizeHeatReservoir = sizeHeatReservoir;
		this.maxLoad = maxLoad;
		
		simulation = new SimulationBHKW();
		
		status = DeviceStatus.INITIALIZED;
	}
	
	/**
	 * Ueberprueft, ob die gewuentsche Aenderung des Lastprofils moeglich ist
	 * und sendet eine Antwort mit der moeglichen Aenderung und dem zugehoerigen Preis
	 * an den Consumer.
	 * @param cr	Enthaelt Informationen, wie das Lastprofil geaendert werden soll 
	 */
	public void changeLoadprofile(ChangeRequestSchedule cr) {		
		double[] changesKWH = cr.getChangesLoadprofile();
		String dateCR = DateTime.ToString(cr.getStartLoadprofile());
		String dateCurrent = DateTime.ToString(cr.getStartLoadprofile());
		if (! dateCR.equals(dateCurrent)) {
			// TODO Fehler
			// ChangeRequest kann nur für scheduleMinutes mit Startzeit
			// timeFixed angefragt werden
		}
		
		double[] planned = scheduleMinutes[1];
		double[] levelReservoir = scheduleMinutes[0];
		
		double price = 0;
		
		for (int i=0; i<numSlots; i++) {
			double value = changesKWH[i];
			double powerGained = 0;
			double currentLevel = levelReservoir[i];
						
			if (value < 0) {
				// Prüfe, dass produzierte Last nicht unter 0 fällt
				if (planned[i] + value < 0) {
					value = -planned[i];
				}
				// Berechne Wärme, die nun nicht mehr produziert,
				// aber benötigt wird und daher vom Wärmespeicher
				// bezogen werden muss
				double heat = Math.abs(value/chpCoefficient);
				if (currentLevel-heat > 0) {
					currentLevel -= heat;
					powerGained = value;
				}
				else {
					// Fülle Speicher so weit wie möglich auf
					powerGained = -sizeHeatReservoir;
					currentLevel = 0;
				}
			}
			if (value > 0) {
				// Prüfe, das maximale Last nicht überschritten wird
				if (planned[i] + value > maxLoad) {
					value = maxLoad-planned[i];
				}
				// Nehme Strom möglichst vom Wärmespeicher
				if (currentLevel > 0) {
					if (currentLevel >= value) {
						// TODO Wie Verhältnis abgeführte Wärme - daraus erzeugter Strom
						currentLevel -= value;
						powerGained = value;
					}
					else {
						powerGained = currentLevel;
						currentLevel = 0;
					}
				}
				
				if (powerGained != value) {
					// Produziere restlichen Strom, speichere dabei
					// als Nebenprodukt produzierte Wärme und 
					// berechne anfallende Kosten
					double powerProduced = value-powerGained;
					double heatProduced = powerProduced*chpCoefficient;
					if (currentLevel+heatProduced <= sizeHeatReservoir) {
						currentLevel += heatProduced;
					}
					else {
						powerProduced = sizeHeatReservoir - currentLevel;
						currentLevel = sizeHeatReservoir;
					}
					// Berechne, wie viel Energie tatsächlich produziert werden konnte
					powerGained += powerProduced;
	
					// Berechne Preis für zusätzlich benötigten Brennstoff
					price += Math.abs(powerProduced*consFuelPerKWh*priceFuel);
				}
			}
			changesKWH[i] = powerGained;
			levelReservoir[i] = currentLevel;
			planned[i] += changesKWH[i];
		}
		for (int i=0; i<numSlots; i++) {
			scheduleMinutes[0][i] = levelReservoir[i];
			scheduleMinutes[1][i] = planned[i];
		}
		
		// Schicke Info mit möglichen Änderungen und Preis dafür an Consumer 
		AnswerChangeRequest answer = new AnswerChangeRequest(cr.getUUID(), changesKWH, price);
		
		/*
		RestTemplate rest = new RestTemplate();
		
		// TODO Passe url an
		String url = new API().consumers(consumerUUID).loadprofiles().toString();
		
		try {
			RequestEntity<AnswerChangeRequest> request = RequestEntity.post(new URI(url)).accept(MediaType.APPLICATION_JSON)
					.body(answer);
			rest.exchange(request, Boolean.class);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
	}

	/**
	 * Das Device speichert das Lastprofil und den Fahrplan zur uebergebenen Zeit als fest ab.
	 * @param time Zeit, fuer die Lastprofil und Fahrplan bestaetigt werden
	 */
	public void confirmLoadprofile (GregorianCalendar time) {
		if (DateTime.ToString(timeFixed).equals(DateTime.ToString(time))) {
			saveSchedule(scheduleMinutes, timeFixed);
			sendNewLoadprofile();
		}
	}
	
	/**
	 * Liefert den aktuellen Status des Kuehlschranks
	 * 
	 * @return Aktueller Status des Kuehlschranks
	 */
	public DeviceStatus getStatus() {
		return status;
	}

	/**
	 * Liefert die uuid des Devices
	 * @return Uuid des Devices
	 */
	public UUID getUUID() {
		return uuid;
	}

	public void initialize(Map<String, Object> init) {
		// TODO Auto-generated method stub
	}

	@Override
	public void ping() {
		GregorianCalendar currentTime = DateTime.now();
		currentTime.set(Calendar.SECOND, 0);
		currentTime.set(Calendar.MILLISECOND, 0);
	
		double powerPlanned, powerScaled;
		int minute = (int) Math.floor(currentTime.get(Calendar.MINUTE)/15);
		powerPlanned = schedulesFixed.get(DateTime.ToString(currentTime))[1][minute];
		powerScaled = simulation.getPower(currentTime);
	
		if (powerPlanned != powerScaled) {
			// Schicke DeltaLastprofil, falls weitere Änderungen
			sendDeltaLoadprofile(currentTime, powerScaled);
		}
	}

	/**
	 * Speichert den uebergebenen Fahrplan als festen Fahrplan ab
	 * 
	 * @param schedule
	 *            Fahrplan, der abgespeichert werden soll
	 * @param start
	 *            Zeitpunkt, zu dem schedule startet
	 */
	public void saveSchedule(double[][] schedule, GregorianCalendar start) {
		schedulesFixed.put(DateTime.ToString(start), schedule);
	}

	/**
	 * Sendet dem Consumer ein Lastprofil
	 * @param loadprofile	Lastprofil, das an den Consumer gesendet werden soll
	 */
	private void sendLoadprofileToConsumer(Loadprofile loadprofile) {
		RestTemplate rest = new RestTemplate();
	
		String url = new API().consumers(consumerUUID).loadprofiles().toString();
		try {
			RequestEntity<Loadprofile> request = RequestEntity.post(new URI(url)).accept(MediaType.APPLICATION_JSON)
					.body(loadprofile);
			rest.exchange(request, Boolean.class);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Erzeugt einen neuen Fahrplan und das zugehoerige Lastprofil.
	 * Das Lastprofil wird an den Consumer geschickt.
	 */
	public void sendNewLoadprofile() {
		double[] valuesLoadprofile;
		
		/*
		 * Prüfe, ob dateFixed gesetzt, wenn nicht setze neu und erstelle
		 * initiales Lastprofil
		 */
		if (timeFixed == null) {
			/*
			 * Wenn noch nicht gesetzt, erstelle initialen Fahrplan für bis zur
			 * nächsten Stunde
			 */
			timeFixed = DateTime.now();
			timeFixed.set(Calendar.SECOND, 0);
			timeFixed.set(Calendar.MILLISECOND, 0);
	
			scheduleMinutes = simulation.getNewSchedule(timeFixed);
			valuesLoadprofile = scheduleMinutes[1];
	
			timeFixed.set(Calendar.MINUTE, 0);
	
			saveSchedule(scheduleMinutes, timeFixed);
		}
		// Zähle timeFixed um eine Stunde hoch
		timeFixed.add(Calendar.HOUR_OF_DAY, 1);
		scheduleMinutes = simulation.getNewSchedule(timeFixed);
		valuesLoadprofile = scheduleMinutes[1];
	
		Loadprofile loadprofile = new Loadprofile(valuesLoadprofile, timeFixed, 0.0, false);
		sendLoadprofileToConsumer(loadprofile);
	}
	
	/**
	 * Legt den Consumer fuer das Device fest
	 * @param uuid	Uuid des Consumers
	 */
	public void setConsumer(UUID consumerUUID) {
		if (status == DeviceStatus.INITIALIZED) {
			status = DeviceStatus.READY;
			consumerUUID = uuid;
		} else {
			throw new IllegalDeviceState();
		}
	
		// sende an den eigenen Consumer das Lastprofil
		sendNewLoadprofile();
	}
	
	/**
	 * Erzeugt bei Bedarf ein Deltalastprofil und sendet es an den Consumer.
	 * Methode wird aufgerufen, wenn eine anderer Wert gemessen wurde, als in der Minute geplant war.
	 * @param	timeChanged		Zeitpunkt, wann die Abweichung gemessen wurde
	 * @param	valueChanged	Wert, der gemessen wurde
	 */
	public void sendDeltaLoadprofile(GregorianCalendar start, double valueChanged) {	
		int minute = start.get(Calendar.MINUTE);
		start.set(Calendar.MINUTE, 0);
		
		double[] oldPlan = schedulesFixed.get(start)[1];
		double[] newPlan = simulation.getNewSchedule(start)[1];
		
		newPlan[minute] = oldPlan[minute];
		
		// Prüfe, ob es weitere Abweichungen gibt
		if (! oldPlan.equals(newPlan)) {
			double[] deltaValues = new double[numSlots];
			for (int i=0; i<numSlots; i++) {
				deltaValues[i] = oldPlan[i] - newPlan[i];
			}
			
			// Versende deltaValues als Delta-Lastprofil an den Consumer
			Loadprofile deltaLoadprofile = new Loadprofile(deltaValues, start, 0.0, true);
			sendLoadprofileToConsumer(deltaLoadprofile);
		}
	}
}
