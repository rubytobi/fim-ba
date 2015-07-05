package Entity;

import java.util.*;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonView;

import Packet.ChangeRequest;
import Packet.InitialLoadprofile;
import Util.DateTime;
import Util.DeviceStatus;
import start.Application;
import start.Device;
import start.View;

public class Fridge implements Device {
	// Fahrplan, den der Consumer gerade aushandelt
	double[][] scheduleMinutes = new double[2][60];
	
	// Zeitpunkt, ab dem scheduleMinutes gilt
	@JsonView(View.Summary.class)
	GregorianCalendar timeFixed;

	// Fahrpläne, die schon ausgehandelt sind und fest stehen

	// currTemp: Temperatur, bei der der letzte aktuelle Fahrplan endet
	double currTemp, maxTemp1, minTemp1, maxTemp2, minTemp2;
	// Wie viel Grad pro Minute erwärmt bzw. kühlt der Kühlschrank?
	double fallCooling, riseWarming;
	// Verbrauch zum Kühlen pro Minute in Wh
	double consCooling;
	@JsonView(View.Summary.class)
	private DeviceStatus status;
	@JsonView(View.Summary.class)
	private UUID uuid;
	@JsonView(View.Summary.class)
	private UUID consumerUUID;
	private boolean send;

	public Fridge() {
		status = DeviceStatus.CREATED;
		uuid = UUID.randomUUID();
	}

	public Fridge(double maxTemp1, double maxTemp2, double minTemp1, double minTemp2, double fallCooling,
			double riseWarming, double consCooling, double currTemp) {
		this();

		// Prüfe Angaben auf Korrektheit
		boolean correct = maxTemp1 <= maxTemp2;
		correct = correct && minTemp1 >= minTemp2;
		correct = correct && maxTemp1 > minTemp1;
		correct = correct && fallCooling < 0;
		correct = correct && riseWarming > 0;
		correct = correct && consCooling > 0;
		if (!correct) {
			// TODO
		}
		this.maxTemp1 = maxTemp1;
		this.minTemp1 = minTemp1;
		this.maxTemp2 = maxTemp2;
		this.minTemp2 = minTemp2;
		this.fallCooling = fallCooling;
		this.riseWarming = riseWarming;
		this.consCooling = consCooling;
		this.currTemp = currTemp;
	}

	public double getCurrTemp() {
		return currTemp;
	}

	public double getMaxTemp1() {
		return maxTemp1;
	}

	public double getMaxTemp2() {
		return maxTemp2;
	}

	public double getMinTemp1() {
		return minTemp1;
	}

	public double getMinTemp2() {
		return minTemp2;
	}

	public double getFallCooling() {
		return fallCooling;
	}

	public double getRiseWarming() {
		return riseWarming;
	}

	public double getConsKühlen() {
		return consCooling;
	}

	// Berechnet das Lastprofil im Minutentakt für int minutes Minuten
	private void chargeScheduleMinutes(int minutes) {
		// TODO Nehme jeweilige Zeit in Plan auf (Start: timeFixed)

		// Erstelle Array mit geplantem Verbrauch (0) und geplanter Temperatur
		// (1) pro Minute
		scheduleMinutes = new double[2][minutes];

		boolean cooling = currTemp >= maxTemp1;

		scheduleMinutes[0][0] = 0.0;
		scheduleMinutes[1][0] = currTemp;

		for (int i = 1; i < minutes; i++) {
			if (cooling) {
				scheduleMinutes[0][i] = consCooling;
				scheduleMinutes[1][i] = fallCooling + scheduleMinutes[1][i - 1];
				if (scheduleMinutes[1][i] <= minTemp1) {
					cooling = false;
				}
			} else {
				scheduleMinutes[0][i] = 0.0;
				scheduleMinutes[1][i] = riseWarming + scheduleMinutes[1][i - 1];
				if (scheduleMinutes[1][i] >= maxTemp1) {
					cooling = true;
				}
			}
		}
		currTemp = scheduleMinutes[1][minutes - 1];
	}

	// Berechnet die Werte für das Lastprofil (1h in 15 Minuten Slots)
	public double[] createValuesLoadprofile() {
		double[] valuesLoadprofile = new double[numSlots];
		double summeMin = 0;
		double summeHour = 0;
		int j = 0;

		for (int i = 0; i < numSlots * 15; i++) {
			summeMin = summeMin + scheduleMinutes[0][i];
			if ((i + 1) % 15 == 0 && i != 0) {
				valuesLoadprofile[j] = summeMin;
				summeHour = summeHour + valuesLoadprofile[j];
				j++;
				summeMin = 0;
			}
		}
		return valuesLoadprofile;
	}

	public DeviceStatus getStatus() {
		return status;
	}

	public void sendLoadprofile() {
		double[] values;

		// Prüfe, ob dateFixed gesetzt, wenn nicht setze neu
		if (timeFixed == null) {
			// Wenn noch nicht gesetzt, erstelle Fahrplan für bis zur nächsten
			// Stunde
			timeFixed = new GregorianCalendar();
			int minutes = timeFixed.get(Calendar.MINUTE);
			minutes = 60 - minutes;
			chargeScheduleMinutes(minutes);

			// TODO lege Fahrplan in festgelegte Fahrpläne ab
			timeFixed.set(Calendar.MINUTE, 0);
			sendLoadprofile();
		} else {
			// Wenn schon gesetzt, zähle timeFixed um eine Stunde hoch
			timeFixed.add(Calendar.HOUR_OF_DAY, 1);
			chargeScheduleMinutes(numSlots * 15);
			values = createValuesLoadprofile();

			// TODO Schicke values + Startzeit an Consumer
		}
	}

	@Override
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public double[] chargeValuesLoadprofile(double[] toBeReduced) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void initialize(Map<String, Object> init) {
		// TODO Auto-generated method stub

	}

	@Override
	public void ping() {
		System.out.println("ping@" + uuid + " " + DateTime.timestamp());

		if (send == false) {
			sendInitialLoadprofile();
			send = true;
		}
		// TODO berechnungen
	}

	public void setConsumer(UUID uuid) {
		consumerUUID = uuid;
	}

	@Override
	public void changeLoadprofile(ChangeRequest cr) {
		// TODO Auto-generated method stub

	}

	private void sendInitialLoadprofile() {
		RestTemplate rest = new RestTemplate();

		InitialLoadprofile lp = new InitialLoadprofile(0.0, createValuesLoadprofile());
		HttpEntity<InitialLoadprofile> entity = new HttpEntity<InitialLoadprofile>(lp, Application.getRestHeader());
		System.out.println(entity.toString());

		rest.exchange("http://localhost:8080/consumers/" + consumerUUID + "/offers", HttpMethod.POST, entity,
				String.class);
	}
}