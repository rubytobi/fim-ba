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
 * Klasse fuer Blockheizkraftwerke (BHKW)
 * @author Admin
 *
 */
public class BHKW implements Device{
	/**
	 *  Stromkoeffizient: Strom = Koeffizient * Waerme
	 */
	private double chpCoefficient;
	
	/**
	 *  Verbrauch fuer Leistung einer zusaetzlichen kWh in l
	 */
	private double consFuelPerKWh;

	/**
	 * UUID des Consumers des BHKW
	 */
	private UUID consumerUUID;

	/**
	 *  Maximale Last des BHKW
	 */
	private double maxLoad;

	/**
	 *  Preis fuer 1l Brennstoff
	 */
	private double priceFuel;
	
	/**
	 *  Fahrplan in 15-Minuten Rythmus, den der Consumer gerade aushandelt
	 *  mit Fuellstand des Reservoirs (0) und Stromerzeugung (1)
	 */
	private double[][] scheduleMinutes;
	
	/** 
	 * Fahrplaene in 15-Minuten Rythmus, die schon ausgehandelt sind 
	 * und fest stehen mit Fuellstand des Reservoirs (0) und Stromerzeugung (1)
	 */
	private TreeMap<String, double[][]> schedulesFixed = new TreeMap<String, double[][]>();

	/**
	 *  Simulator fuer das BHKW
	 */
	private SimulationBHKW simulation;

	/**
	 *  Groesse des Waermespeichers
	 */
	private double sizeHeatReservoir;

	/**
	 *  Status des BHKW
	 */
	private DeviceStatus status;
	
	/**
	 * Zeitpunkt, ab dem scheduleMinutes gilt
	 */
	private GregorianCalendar timeFixed;
	
	/**
	 *  UUID des BHKW
	 */
	private UUID uuid;
	
	private BHKW() {
		status = DeviceStatus.CREATED;
		uuid = UUID.randomUUID();
	}
	
	/**
	 * Erstellt ein neues Blockheizkraftwerk.
	 * @param chpCoefficient	Stromkoeffizient: Strom = Koeffizient * Waerme
	 * @param priceFuel			Preis des Brennstoffs pro Liter
	 * @param consFuelPerKWh	Benoetigter Brennstoff in Liter, um eine kWh Strom zu produzieren
	 * @param sizeHeatReservoir	Groesse des Waermespeichers
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
			// ChangeRequest kann nur fuer scheduleMinutes mit Startzeit
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
				// Pruefe, dass produzierte Last nicht unter 0 faellt
				if (planned[i] + value < 0) {
					value = -planned[i];
				}
				// Berechne Waerme, die nun nicht mehr produziert,
				// aber benoetigt wird und daher vom Waermespeicher
				// bezogen werden muss
				double heat = Math.abs(value/chpCoefficient);
				if (currentLevel-heat > 0) {
					currentLevel -= heat;
					powerGained = value;
				}
				else {
					// Fuelle Speicher so weit wie moeglich auf
					powerGained = -sizeHeatReservoir;
					currentLevel = 0;
				}
			}
			if (value > 0) {
				// Pruefe, das maximale Last nicht ueberschritten wird
				if (planned[i] + value > maxLoad) {
					value = maxLoad-planned[i];
				}
				// Nehme Strom moeglichst vom Waermespeicher
				if (currentLevel > 0) {
					if (currentLevel >= value) {
						// TODO Wie Verhaeltnis abgefuehrte Waerme - daraus erzeugter Strom
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
					// als Nebenprodukt produzierte Waerme und 
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
					// Berechne, wie viel Energie tatsaechlich produziert werden konnte
					powerGained += powerProduced;
	
					// Berechne Preis fuer zusaetzlich benoetigten Brennstoff
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
		
		// Schicke Info mit moeglichen aenderungen und Preis dafuer an Consumer 
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
			// Schicke DeltaLastprofil, falls weitere aenderungen
			sendDeltaLoadprofile(currentTime, powerScaled);
		}
	}

	/**
	 * Speichert den uebergebenen Fahrplan als festen Fahrplan ab
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
		 * Pruefe, ob dateFixed gesetzt, wenn nicht setze neu und erstelle
		 * initiales Lastprofil
		 */
		if (timeFixed == null) {
			/*
			 * Wenn noch nicht gesetzt, erstelle initialen Fahrplan fuer bis zur
			 * naechsten Stunde
			 */
			timeFixed = DateTime.now();
			timeFixed.set(Calendar.SECOND, 0);
			timeFixed.set(Calendar.MILLISECOND, 0);
	
			scheduleMinutes = simulation.getNewSchedule(timeFixed);
			valuesLoadprofile = scheduleMinutes[1];
	
			timeFixed.set(Calendar.MINUTE, 0);
	
			saveSchedule(scheduleMinutes, timeFixed);
		}
		// Zaehle timeFixed um eine Stunde hoch
		timeFixed.add(Calendar.HOUR_OF_DAY, 1);
		scheduleMinutes = simulation.getNewSchedule(timeFixed);
		valuesLoadprofile = scheduleMinutes[1];
	
		Loadprofile loadprofile = new Loadprofile(valuesLoadprofile, timeFixed, 0.0, false);
		sendLoadprofileToConsumer(loadprofile);
	}
	
	/**
	 * Legt den Consumer fuer das Device fest
	 * @param consumerUUID	Uuid des Consumers
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
	 * @param	start		Zeitpunkt, wann die Abweichung gemessen wurde
	 * @param	valueChanged	Wert, der gemessen wurde
	 */
	public void sendDeltaLoadprofile(GregorianCalendar start, double valueChanged) {	
		int minute = start.get(Calendar.MINUTE);
		start.set(Calendar.MINUTE, 0);
		
		double[] oldPlan = schedulesFixed.get(start)[1];
		double[] newPlan = simulation.getNewSchedule(start)[1];
		
		newPlan[minute] = oldPlan[minute];
		
		// Pruefe, ob es weitere Abweichungen gibt
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
