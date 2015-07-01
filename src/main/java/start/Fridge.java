package start;

public class Fridge extends Device {
	double[][] scheduleMinutes;

	double currTemp, maxTemp1, minTemp1, maxTemp2, minTemp2;
	// Wie viel Grad pro Minute erw�rmt bzw. kühlt der K�hlschrank?
	double fallCooling, riseWarming;
	// Verbrauch zum Kühlen pro Minute in Wh
	double consCooling;

	public Fridge(double maxTemp1, double maxTemp2, double minTemp1, double minTemp2, double fallCooling,
			double riseWarming, double consCooling, double currTemp) {
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

	public double getConsCooling() {
		return consCooling;
	}

	// Berechnet das Lastprofil im Minutentakt f�r eine Stunde
	public void chargeScheduleMinutes() {
		// Erstelle Array mit geplantem Verbrauch (0) und geplanter Temperatur
		// (1) pro Minute
		scheduleMinutes = new double[2][numSlots * 15];

		boolean cooling = currTemp >= maxTemp1;

		scheduleMinutes[0][0] = 0.0;
		scheduleMinutes[1][0] = currTemp;

		for (int i = 1; i < numSlots * 15; i++) {
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

	}

	// Berechnet das Lastprofil für die Angebote (1h in 15 Minuten Slots)
	public double[] chargeValuesLoadprofile() {
		return super.chargeValuesLoadprofile(scheduleMinutes[0]);
	}
}
