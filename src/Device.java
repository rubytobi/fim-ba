
public interface Device {
	int numSlots = 4;
	
	public double[] createValuesLoadprofile();
	public void getStatus();
	public void sendLoadprofile();
}
