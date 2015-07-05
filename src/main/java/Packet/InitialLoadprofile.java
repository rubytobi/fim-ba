package Packet;

public class InitialLoadprofile {
	private double price;
	private double[] values;

	public InitialLoadprofile(double d, double[] loadprofile) {
		this.price = d;
		this.values = loadprofile;
	}

	public double getPrice() {
		return price;
	}

	public double[] getValues() {
		return values;
	}

}
