package start;

public class BHKWCreation {
	double chpCoefficient;
	double priceFuel;
	double consFuelPerKWh;
	double sizeHeatReservoir;
	double maxLoad;
	double startLoad;

	public BHKWCreation() {
		// dummy
	}

	public BHKWCreation(double chpCoefficient, double priceFuel, double consFuelPerKWh, double startLoad,
			double sizeHeatReservoir) {
		this.chpCoefficient = chpCoefficient;
		this.priceFuel = priceFuel;
		this.consFuelPerKWh = consFuelPerKWh;
		this.sizeHeatReservoir = sizeHeatReservoir;
		this.maxLoad = 0.8;
		this.startLoad = startLoad;
		// this.maxLoad = Math.round(100.00 * ((double) (Math.random() * 1)
		// +0.1)) / 100.00;
	}

	public double getChpCoefficient() {
		return chpCoefficient;
	}

	public double getPriceFuel() {
		return priceFuel;
	}

	public double getConsFuelPerKWh() {
		return consFuelPerKWh;
	}

	public double getSizeHeatReservoir() {
		return sizeHeatReservoir;
	}

	public double getMaxLoad() {
		return maxLoad;
	}

	public double getStartLoad() {
		return startLoad;
	}
}
