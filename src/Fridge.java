import java.util.*;


public class Fridge implements Device {
	// Fahrplan, den der Consumer gerade aushandelt
	double[][] scheduleMinutes;
	// Zeitpunkt, ab dem scheduleMinutes gilt
	GregorianCalendar timeFixed;
	
	// Fahrpl�ne, die schon ausgehandelt sind und fest stehen
	Hashtable<Calendar, Double> schedulesFixed = new Hashtable();
	
	// currTemp: Temperatur, bei der der letzte aktuelle Fahrplan endet	
	double currTemp, maxTemp1, minTemp1, maxTemp2, minTemp2;
	// Wie viel Grad pro Minute erw�rmt bzw. k�hlt der K�hlschrank?
	double fallCooling, riseWarming;
	// Verbrauch zum K�hlen pro Minute in Wh
	double consCooling;
	
	public Fridge(double maxTemp1, double maxTemp2, double minTemp1, double minTemp2, double fallCooling, double riseWarming, double consCooling, double currTemp) {
		//Pr�fe Angaben auf Korrektheit
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
	
	public double getConsK�hlen() {
		return consCooling;
	}
	
	// Berechnet das Lastprofil im Minutentakt f�r int minutes Minuten
	private void chargeScheduleMinutes(int minutes) {
		// TODO Nehme jeweilige Zeit in Plan auf (Start: timeFixed)
		
		// Erstelle Array mit geplantem Verbrauch (0) und geplanter Temperatur (1) pro Minute
		scheduleMinutes = new double[2][minutes];
		
		boolean cooling = currTemp>=maxTemp1;
		
		scheduleMinutes[0][0] = 0.0;
		scheduleMinutes[1][0] = currTemp;

		for (int i=1; i<minutes; i++) {
			if (cooling) {
				scheduleMinutes[0][i] = consCooling;
				scheduleMinutes[1][i] = fallCooling + scheduleMinutes[1][i-1];
				if (scheduleMinutes[1][i] <= minTemp1) {
					cooling = false;
				}
			}
			else {
				scheduleMinutes[0][i] = 0.0;
				scheduleMinutes[1][i] = riseWarming+scheduleMinutes[1][i-1];
				if (scheduleMinutes[1][i] >= maxTemp1) {
					cooling = true;
				}
			}
		}
		currTemp = scheduleMinutes[1][minutes-1];
	}
	
	// Berechnet die Werte f�r das Lastprofil (1h in 15 Minuten Slots)
	public double[] createValuesLoadprofile() {
		double[] valuesLoadprofile = new double[numSlots];
		double summeMin = 0;
		double summeHour = 0;
		int j = 0;
		
		for (int i=0; i<numSlots*15; i++) {
			summeMin = summeMin + scheduleMinutes[0][i];
			if((i+1)%15 == 0 && i!=0) {
				valuesLoadprofile[j] = summeMin;
				summeHour = summeHour+valuesLoadprofile[j];
				j++;
				summeMin = 0;
			}
		}
		return valuesLoadprofile;
	}
	
	public void getStatus() {
		// Wie lang geplant, aktuelle Temperatur, ...
	}
	
	public void sendLoadprofile() {
		double[] values;
		
		// Pr�fe, ob dateFixed gesetzt, wenn nicht setze neu
		if (timeFixed == null) {
			// Wenn noch nicht gesetzt, erstelle Fahrplan f�r bis zur n�chsten Stunde
			timeFixed = new GregorianCalendar();
			int minutes = timeFixed.get(Calendar.MINUTE);
			minutes = 60-minutes;
			chargeScheduleMinutes(minutes);
			
			
			// TODO lege Fahrplan in festgelegte Fahrpl�ne ab
			saveSchedule(scheduleMinutes, timeFixed);
			timeFixed.set(Calendar.MINUTE, 0);
			sendLoadprofile();
		}
		else {
			// Wenn schon gesetzt, z�hle timeFixed um eine Stunde hoch
			timeFixed.add(Calendar.HOUR_OF_DAY, 1);
			chargeScheduleMinutes(numSlots*15);
			values = createValuesLoadprofile();
			
			// TODO Schicke values + Startzeit an Consumer
		}
	}
	
	public void saveSchedule(double[][] schedule, Calendar date) {
		int size = schedule[1].length;
		
		for (int i=0; i<size; i++) {
			schedulesFixed.put(date, schedule[1][i]);
			date.add(Calendar.MINUTE, 1);
		}
		System.out.println(schedulesFixed.size());
		
	}
}

