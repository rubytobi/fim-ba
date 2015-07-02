package Entity;

import java.util.Date;

import start.Loadprofile;

public class ValuedLoadprofile extends Loadprofile {
	private double minPrice;

	protected ValuedLoadprofile(double[] values, Consumer consumer, Date date) {
		super(values, consumer, date);
		// TODO Auto-generated constructor stub
	}

	public double getMinPrice() {
		return minPrice;
	}

}
