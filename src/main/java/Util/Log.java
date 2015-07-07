package Util;

public class Log {
	public static void d(String className, String s) {
		System.out.println("#debug\t" + className + "\t" + s);
	}

	public static void d(String s) {
		d("-", s);
	}
}
