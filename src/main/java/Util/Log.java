package Util;

public class Log {
	public static void i(String className, String s) {
		System.out.println("#info\t" + className + "\t" + s);
	}

	public static void i(String s) {
		i("-", s);
	}

	public static void d(String className, String s) {
		System.out.println("#debug\t" + className + "\t" + s);
	}

	public static void d(String s) {
		d("-", s);
	}

	public static void e(String className, String s) {
		System.err.println("#error\t" + className + "\t" + s);
	}

	public static void e(String s) {
		e("-", s);
	}
}
