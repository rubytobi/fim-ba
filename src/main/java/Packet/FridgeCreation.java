package Packet;

public class FridgeCreation {
	double maxTemp1;
	double maxTemp2;
	double minTemp1;
	double minTemp2;
	double fallCooling;
	double riseWarming;
	double consCooling;
	double currTemp;

	public FridgeCreation() {
		// dummy constructor
	}

	public FridgeCreation(double maxTemp1, double maxTemp2, double minTemp1, double minTemp2, double fallCooling,
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

	public double getCurrTemp() {
		return currTemp;
	}
}
