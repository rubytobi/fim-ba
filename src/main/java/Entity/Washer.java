package Entity;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ArrayList;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonView;

import Packet.ChangeRequestSchedule;
import Packet.AnswerChangeRequest;
import Util.DateTime;
import Util.View;

/**
 * Klasse fuer Waschmaschinen
 *
 */
public class Washer implements Device {
	// Wert, in welcher Phase und in der wievielten Minute der Phase
	private String currentProgram;
	private int currentMinute;

	// Der Verlauf eines Waschprogramms
	private Map<String, double[]> programs = new TreeMap<String, double[]>();

	// Zeiten, wann die Waschmaschine welches Waschprogramm starten soll
	private Map<GregorianCalendar, String> startPrograms = new TreeMap<GregorianCalendar, String>();
	private ConcurrentLinkedQueue<String> programQueue = new ConcurrentLinkedQueue<String>();

	private GregorianCalendar timeFixed;

	private TreeMap<String, double[]> loadprofilesFixed = new TreeMap<String, double[]>();
	private double[] scheduleMinutes = new double[15 * numSlots];

	@JsonView(View.Summary.class)
	private DeviceStatus status;

	@JsonView(View.Summary.class)
	private UUID uuid;

	@JsonView(View.Summary.class)
	private UUID consumerUUID;

	private Washer() {
		status = DeviceStatus.CREATED;
		uuid = UUID.randomUUID();
	}

	/**
	 * Erstellt neue Waschmaschine mit uebergebenen Programmen
	 * 
	 * @param programs
	 *            Map aller Programme mit dem jeweiligen Namen und dem Verbrauch
	 *            pro Minute für das gesamte Programm
	 */
	public Washer(Map<String, double[]> programs) {
		this();

		this.programs = programs;
		currentProgram = null;
		currentMinute = 0;

		status = DeviceStatus.INITIALIZED;
	}

	/**
	 * Erstellt die Werte fuer das Lastprofil zum uebergebenen Fahrplan
	 * 
	 * @param schedule
	 *            Minuetlicher Fahrplan, fuer welchen Lasprofil erstellt werden
	 *            soll
	 * @return Array mit Verbrauchswert pro Viertelstunde
	 */
	public double[] createValuesLoadprofile(double[] schedule) {
		double[] valuesLoadprofile = new double[numSlots];
		double summeMin = 0;
		double summeHour = 0;
		int n = 0;

		for (int i = 0; i < numSlots * 15; i++) {
			summeMin = summeMin + schedule[i];
			if ((i + 1) % 15 == 0 && i != 0) {
				valuesLoadprofile[n] = summeMin;
				summeHour = summeHour + valuesLoadprofile[n];
				n++;
				summeMin = 0;
			}
		}
		return valuesLoadprofile;
	}

	/**
	 * Fuegt den Start eine Programms hinzu
	 * 
	 * @param start
	 *            Start des Programms
	 * @param program
	 *            Name des Programms
	 */
	public void addStart(GregorianCalendar start, String program) {
		startPrograms.put(start, program);
	}

	/**
	 * Erzeugt einen neuen Fahrplan und das zugehoerige Lastprofil. Das
	 * Lastprofil wird an den Consumer geschickt.
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
			System.out.println("TimeFixed null");
			timeFixed = DateTime.now();
			timeFixed.set(Calendar.SECOND, 0);
			timeFixed.set(Calendar.MILLISECOND, 0);

			chargeNewSchedule();
			valuesLoadprofile = createValuesLoadprofile(scheduleMinutes);

			timeFixed.set(Calendar.MINUTE, 0);

			// saveSchedule(scheduleMinutes, timeFixed);
			loadprofilesFixed.put(DateTime.ToString(timeFixed), valuesLoadprofile);
		}
		// Zähle timeFixed um eine Stunde hoch
		timeFixed.add(Calendar.HOUR_OF_DAY, 1);
		chargeNewSchedule();
		// valuesLoadprofile = createValuesLoadprofile(scheduleMinutes[0]);

		// Loadprofile loadprofile = new Loadprofile(valuesLoadprofile,
		// timeFixed, false);
		// sendLoadprofileToConsumer(loadprofile, false);
	}

	/**
	 * Berechnet einen neuen Fahrplan im Minutentakt für die volle Stunde, in
	 * welcher timeFixed liegt und mit Temperatur currTempt zum Zeitpunkt
	 * timeFixed
	 */
	private void chargeNewSchedule() {
		// Erstelle Array mit geplantem Verbrauch pro Minute
		scheduleMinutes = new double[15 * numSlots];

		// Prüfe, in welcher Minute Plan von timeFixed startet und
		// setze ggf. alle Werte der Stunde vor timeFixed gleich 0
		int currentMinuteSchedule = timeFixed.get(Calendar.MINUTE);

		System.out.println("--------Setze Werte vor timeFixed = 0: " + (currentMinuteSchedule > 0));
		for (int i = 0; i < currentMinuteSchedule; i++) {
			scheduleMinutes[i] = 0.0;
			System.out.println("Minute " + i + " Programm: " + currentProgram + " Verbrauch: " + scheduleMinutes[i]);
		}

		// Wenn das Programm der letzten Stunde noch nicht beendet ist, beende
		// zuerst
		// dieses Programm
		System.out
				.println("--------Beende vorhergehendes Programm: " + (currentProgram != null) + ", " + currentMinute);
		if (currentProgram != null) {
			double[] values = programs.get(currentProgram);

			currentMinuteSchedule = addProgram(values, currentMinuteSchedule, currentMinute);
			if (currentMinuteSchedule == 15 * numSlots + 1) {
				return;
			}
		}

		// Prüfe, ob Elemente in der Queue sind
		System.out.println("--------Hole Elemente aus Queue: " + (programQueue.size() != 0));
		while (programQueue.size() != 0) {
			currentProgram = programQueue.poll();
			double[] values = programs.get(currentProgram);

			currentMinuteSchedule = addProgram(values, currentMinuteSchedule, 0);
			if (currentMinuteSchedule == 15 * numSlots + 1) {
				return;
			} else {
				currentProgram = null;
			}
		}

		// Schau, ob der nächste Start schon erreicht ist
		GregorianCalendar currentTimeSchedule = (GregorianCalendar) timeFixed.clone();
		currentTimeSchedule.set(Calendar.MINUTE, currentMinuteSchedule);
		ArrayList<GregorianCalendar> toRemove = new ArrayList<GregorianCalendar>();

		Set<GregorianCalendar> allStarts = startPrograms.keySet();
		for (GregorianCalendar times : allStarts) {
			System.out.println("Nächster Start: " + DateTime.ToString(times));
			while (!(times.before(currentTimeSchedule) || times.equals(currentTimeSchedule))
					&& !(currentMinuteSchedule == 15 * numSlots)) {
				scheduleMinutes[currentMinuteSchedule] = 0;
				System.out.println("Minute " + currentMinuteSchedule + " Programm: " + currentProgram + " Verbrauch: "
						+ scheduleMinutes[currentMinuteSchedule]);
				currentTimeSchedule.add(Calendar.MINUTE, 1);
				currentMinuteSchedule++;
			}
			if ((times.before(currentTimeSchedule) || times.equals(currentTimeSchedule))) {
				currentProgram = startPrograms.get(times);
				double[] values = programs.get(currentProgram);
				toRemove.add(times);

				currentMinuteSchedule = addProgram(values, currentMinuteSchedule, 0);
				currentTimeSchedule.add(Calendar.MINUTE, values.length);
			}
			if (currentMinuteSchedule == 15 * numSlots) {
				break;
			}
		}

		for (GregorianCalendar time : toRemove) {
			startPrograms.remove(time);
		}

		if (allStarts.size() == 0) {
			System.out.println("--------Es gibt keine Starts");
			while (currentMinuteSchedule < 15 * numSlots) {
				scheduleMinutes[currentMinuteSchedule] = 0;
				System.out.println("Minute " + currentMinuteSchedule + " Programm: " + currentProgram + " Verbrauch: "
						+ scheduleMinutes[currentMinuteSchedule]);
				currentMinuteSchedule++;
			}
		}
		System.out.println("currentMinuteSchedule: " + currentMinuteSchedule);
		// Befülle Queue
		updateQueue();
	}

	/**
	 * Fuegt Programm zum Fahrplan hinzu
	 * 
	 * @param values
	 *            Verbrauchswerte pro Minute des Programms
	 * @param currentMinuteSchedule
	 *            Minute des Fahrplans, in der das Programm starten soll
	 * @param minuteProgram
	 *            Minute des Programms
	 * @return Minute des Fahrplans, zu der das Programm fertig ist
	 */
	private int addProgram(double[] values, int currentMinuteSchedule, int minuteProgram) {
		int end = values.length + currentMinuteSchedule;

		while (currentMinuteSchedule < end && currentMinuteSchedule < 15 * numSlots && minuteProgram < values.length) {
			scheduleMinutes[currentMinuteSchedule] = values[minuteProgram];
			System.out.println("Minute " + currentMinuteSchedule + " Programm: " + currentProgram + " Verbrauch: "
					+ scheduleMinutes[currentMinuteSchedule]);
			minuteProgram++;
			currentMinuteSchedule++;
		}

		if (currentMinuteSchedule == 15 * numSlots && minuteProgram != values.length) {
			updateQueue();
			currentMinute = minuteProgram++;
			return 15 * numSlots;
		}
		currentProgram = null;
		currentMinute = 0;
		return currentMinuteSchedule;
	}

	/**
	 * Fuegt alle Programme, die in der aktuellen Stunde starten sollen, der
	 * Queue hinzu
	 */
	private void updateQueue() {
		// Füge alle Starts der aktuellen Stunde der Queue hinzu
		GregorianCalendar until = (GregorianCalendar) timeFixed.clone();
		until.set(Calendar.MINUTE, 0);
		until.add(Calendar.HOUR, 1);
		System.out.println("Alle Starts bis: " + DateTime.ToString(until));

		ArrayList<GregorianCalendar> toRemove = new ArrayList<GregorianCalendar>();
		Set<GregorianCalendar> allStarts = startPrograms.keySet();
		for (GregorianCalendar times : allStarts) {
			if (times.before(until)) {
				programQueue.add(startPrograms.get(times));
				toRemove.add(times);
				System.out.println(" Von " + DateTime.ToString(times) + ": " + startPrograms.get(times));
			} else {
				break;
			}
		}

		for (GregorianCalendar time : toRemove) {
			startPrograms.remove(time);
		}
	}

	public void sendDeltaLoadprofile(GregorianCalendar timeChanged, double valueChanged) {
		// TODO
	}

	public UUID getUUID() {
		return uuid;
	}

	public void initialize(Map<String, Object> init) {
		// TODO
	}

	public DeviceStatus getStatus() {
		return status;
	}

	public void ping() {
		// TODO
	}

	public void setConsumer(UUID consumerUUID) {
		// TODO
	}

	public AnswerChangeRequest changeLoadprofile(ChangeRequestSchedule cr) {
		// TODO
		return null;
	}

	public void confirmLoadprofile(GregorianCalendar time) {
		// TODO
	}

	@Override
	public void receiveAnswerChangeRequest(boolean acceptChange) {
		// TODO Auto-generated method stub

	}
}
