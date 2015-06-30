


public class Fridge implements Device {
	// Fahrplan, den der Consumer gerade aushandelt
	double[][] scheduleMinutes;
	
	// Fahrpläne, die schon ausgehandelt sind und fest stehen
	
		
	double currTemp, maxTemp1, minTemp1, maxTemp2, minTemp2;
	// Wie viel Grad pro Minute erwärmt bzw. kühlt der Kühlschrank?
	double fallCooling, riseWarming;
	// Verbrauch zum Kühlen pro Minute in Wh
	double consCooling;
	
	public Fridge(double maxTemp1, double maxTemp2, double minTemp1, double minTemp2, double fallCooling, double riseWarming, double consCooling, double currTemp) {
		//Prüfe Angaben auf Korrektheit
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
	
	// Berechnet das Lastprofil im Minutentakt für eine Stunde
	public void chargeScheduleMinutes() {
		// Erstelle Array mit geplantem Verbrauch (0) und geplanter Temperatur (1) pro Minute
		scheduleMinutes = new double[2][numSlots*15];
		
		boolean cooling = currTemp>=maxTemp1;
		
		scheduleMinutes[0][0] = 0.0;
		scheduleMinutes[1][0] = currTemp;
		
		for (int i=1; i<numSlots*15; i++) {
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
	}
	
	// Berechnet die Werte für das Lastprofil (1h in 15 Minuten Slots)
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
}

