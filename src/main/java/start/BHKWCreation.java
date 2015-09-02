package start;

public class BHKWCreation {
	double chpCoefficient;
	double priceFuel;
	double consFuelPerKWh;
	double sizeHeatReservoir;
	double maxLoad;

	public BHKWCreation() {
		// dummy
	}

	public BHKWCreation(double chpCoefficient, double priceFuel, double consFuelPerKWh) {
		this.chpCoefficient = chpCoefficient;
		this.priceFuel = priceFuel;
		this.consFuelPerKWh = consFuelPerKWh;
		this.sizeHeatReservoir = ((int) (Math.random() * 10)) * (10 - 5) + 50;
		this.maxLoad = ((int) (Math.random() * (10 - 5))) + 5;
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

}
